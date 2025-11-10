package com.example.eshop.ktorm.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.ktorm.table.Customers
import com.example.eshop.ktorm.table.Orders
import com.example.eshop.ktorm.table.Payments
import com.example.eshop.ktorm.util.toEnumOrThrow
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.support.mysql.bulkInsert
import java.math.BigDecimal

class KtormPaymentRepository(private val database: Database) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        database.insert(Payments) {
            set(it.id, payment.id.value)
            set(it.orderId, payment.orderId.value)
            set(it.amount, payment.amount.amount)
            set(it.paymentDate, payment.paymentDate)
            set(it.method, payment.method.name)
            set(it.status, payment.status.name)
        }
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return database.from(Payments)
            .select()
            .where { Payments.id eq id.value }
            .map { row -> row.toPayment() }
            .singleOrNull()
    }

    override fun findAll(): List<Payment> {
        return database.from(Payments)
            .select()
            .map { row -> row.toPayment() }
    }

    override fun update(payment: Payment): Payment {
        val updatedRows = database.update(Payments) {
            set(it.orderId, payment.orderId.value)
            set(it.amount, payment.amount.amount)
            set(it.paymentDate, payment.paymentDate)
            set(it.method, payment.method.name)
            set(it.status, payment.status.name)
            where { it.id eq payment.id.value }
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Payment with id ${payment.id.value} not found")
        }
        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val deleted = database.delete(Payments) { it.id eq id.value }
        return deleted > 0
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        val totalPaymentsAlias = sum(Payments.amount).aliased("total_payments")
        val avgPaymentAlias = avg(Payments.amount).aliased("avg_payment")
        val paymentCountAlias = count(Payments.id).aliased("payment_count")
        val orderCountAlias = countDistinct(Orders.id).aliased("order_count")

        return database.from(Customers)
            .innerJoin(Orders, on = Customers.id eq Orders.customerId)
            .innerJoin(Payments, on = Orders.id eq Payments.orderId)
            .select(
                Customers.id,
                Customers.name,
                totalPaymentsAlias,
                avgPaymentAlias,
                paymentCountAlias,
                orderCountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Customers.id, Customers.name)
            .map { row ->
                CustomerStatistics(
                    customerId = row[Customers.id]!!,
                    customerName = row[Customers.name]!!,
                    totalPayments = Money(BigDecimal.valueOf((row[totalPaymentsAlias] as? Number)?.toDouble() ?: 0.0)),
                    averagePayment = Money(BigDecimal.valueOf((row[avgPaymentAlias] as? Number)?.toDouble() ?: 0.0)),
                    paymentCount = (row[paymentCountAlias] as? Number)?.toLong() ?: 0L,
                    completedOrderCount = (row[orderCountAlias] as? Number)?.toLong() ?: 0L
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val totalAmountAlias = sum(Payments.amount).aliased("total_amount")
        val countAlias = count(Payments.id).aliased("count")
        val avgAmountAlias = avg(Payments.amount).aliased("avg_amount")

        return database.from(Payments)
            .select(
                Payments.method,
                totalAmountAlias,
                countAlias,
                avgAmountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Payments.method)
            .associate { row ->
                val method = row[Payments.method]!!.toEnumOrThrow<PaymentMethod>()
                method to PaymentMethodStats(
                    method = method,
                    totalAmount = Money(BigDecimal.valueOf((row[totalAmountAlias] as? Number)?.toDouble() ?: 0.0)),
                    count = (row[countAlias] as? Number)?.toLong() ?: 0L,
                    averageAmount = Money(BigDecimal.valueOf((row[avgAmountAlias] as? Number)?.toDouble() ?: 0.0))
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return database.from(Payments)
            .select()
            .where { Payments.orderId eq orderId.value }
            .map { row -> row.toPayment() }
            .singleOrNull()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        return database.from(Payments)
            .innerJoin(Orders, on = Payments.orderId eq Orders.id)
            .innerJoin(Customers, on = Orders.customerId eq Customers.id)
            .select()
            .where { Payments.status eq PaymentStatus.FAILED.name }
            .map { row ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(row[Payments.id]!!),
                        orderId = OrderId(row[Payments.orderId]!!),
                        amount = Money(row[Payments.amount]!!),
                        paymentDate = row[Payments.paymentDate]!!,
                        method = row[Payments.method]!!.toEnumOrThrow<PaymentMethod>(),
                        status = row[Payments.status]!!.toEnumOrThrow<PaymentStatus>()
                    ),
                    orderDate = row[Orders.orderDate].toString(),
                    customerName = row[Customers.name]!!,
                    customerEmail = row[Customers.email]!!
                )
            }
    }

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        require(minPaymentCount >= 0) { "minPaymentCount must be non-negative" }

        val totalAmountAlias = sum(Payments.amount).aliased("total_amount")
        val paymentCountAlias = count(Payments.id).aliased("payment_count")

        // 모든 고객의 통계를 조회
        val allCustomers = database.from(Customers)
            .innerJoin(Orders, on = Customers.id eq Orders.customerId)
            .innerJoin(Payments, on = Orders.id eq Payments.orderId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                totalAmountAlias,
                paymentCountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Customers.id, Customers.name, Customers.email)
            .map { row ->
                val totalAmount = Money(BigDecimal.valueOf((row[totalAmountAlias] as? Number)?.toDouble() ?: 0.0))
                val payCount = (row[paymentCountAlias] as? Number)?.toLong() ?: 0L

                Triple(
                    Triple(row[Customers.id]!!, row[Customers.name]!!, row[Customers.email]!!),
                    totalAmount,
                    payCount
                )
            }

        // 클라이언트 측에서 필터링하여 프리미엄 고객만 선택
        return allCustomers
            .filter { (_, totalAmount, payCount) ->
                totalAmount.amount >= minTotalAmount.amount || payCount >= minPaymentCount
            }
            .map { (customerInfo, totalAmount, payCount) ->
                val (customerId, customerName, customerEmail) = customerInfo

                // customerType 판단: 두 조건을 모두 만족하면 BOTH
                val customerType = when {
                    totalAmount.amount >= minTotalAmount.amount && payCount >= minPaymentCount ->
                        PremiumCustomerType.BOTH
                    totalAmount.amount >= minTotalAmount.amount ->
                        PremiumCustomerType.HIGH_VALUE
                    else ->
                        PremiumCustomerType.FREQUENT_PAYER
                }

                PremiumCustomerInfo(
                    customerId = customerId,
                    customerName = customerName,
                    customerEmail = customerEmail,
                    totalPaymentAmount = totalAmount,
                    paymentCount = payCount,
                    customerType = customerType
                )
            }
    }

    override fun findMonthlyPaymentTrends(): List<MonthlyPaymentTrend> {
        // Ktorm은 window function을 위한 Native SQL을 사용해야 합니다.
        val sql = """
            SELECT
                DATE_FORMAT(payment_date, '%Y-%m') as year_month,
                SUM(amount) as total_amount,
                COUNT(*) as payment_count,
                AVG(amount) as average_amount,
                LAG(SUM(amount)) OVER (ORDER BY DATE_FORMAT(payment_date, '%Y-%m')) as previous_month_amount
            FROM payment
            WHERE status = 'COMPLETED'
            GROUP BY DATE_FORMAT(payment_date, '%Y-%m')
            ORDER BY year_month
        """.trimIndent()

        return database.useConnection { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<MonthlyPaymentTrend>()
                    while (rs.next()) {
                        val yearMonth = rs.getString("year_month")
                        val totalAmount = Money(rs.getBigDecimal("total_amount"))
                        val paymentCount = rs.getLong("payment_count")
                        val averageAmount = Money(rs.getBigDecimal("average_amount"))
                        val previousMonthAmount = rs.getBigDecimal("previous_month_amount")?.let { Money(it) }

                        val growthRate = if (previousMonthAmount != null && previousMonthAmount.amount > BigDecimal.ZERO) {
                            ((totalAmount.amount - previousMonthAmount.amount) / previousMonthAmount.amount * BigDecimal(100)).toDouble()
                        } else {
                            null
                        }

                        results.add(
                            MonthlyPaymentTrend(
                                yearMonth = yearMonth,
                                totalAmount = totalAmount,
                                paymentCount = paymentCount,
                                averageAmount = averageAmount,
                                previousMonthAmount = previousMonthAmount,
                                growthRate = growthRate
                            )
                        )
                    }
                    results
                }
            }
        }
    }

    override fun findRecentPaymentsByAllCustomers(limit: Int): List<RecentPaymentInfo> {
        // Ktorm은 window function을 위한 Native SQL을 사용해야 합니다.
        val sql = """
            SELECT
                c.id as customer_id,
                c.name as customer_name,
                p.id as payment_id,
                p.order_id,
                p.amount,
                p.payment_date,
                p.method,
                p.status,
                o.order_date,
                ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY p.payment_date DESC) as row_num
            FROM customer c
            INNER JOIN orders o ON c.id = o.customer_id
            INNER JOIN payment p ON o.id = p.order_id
        """.trimIndent()

        return database.useConnection { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs ->
                    val results = mutableListOf<RecentPaymentInfo>()
                    while (rs.next()) {
                        val rowNum = rs.getInt("row_num")
                        if (rowNum <= limit) {
                            results.add(
                                RecentPaymentInfo(
                                    customerId = rs.getLong("customer_id"),
                                    customerName = rs.getString("customer_name"),
                                    payment = Payment(
                                        id = PaymentId(rs.getLong("payment_id")),
                                        orderId = OrderId(rs.getLong("order_id")),
                                        amount = Money(rs.getBigDecimal("amount")),
                                        paymentDate = rs.getTimestamp("payment_date").toLocalDateTime(),
                                        method = rs.getString("method").toEnumOrThrow<PaymentMethod>(),
                                        status = rs.getString("status").toEnumOrThrow<PaymentStatus>()
                                    ),
                                    orderDate = rs.getTimestamp("order_date").toString(),
                                    rowNumber = rowNum
                                )
                            )
                        }
                    }
                    results
                }
            }
        }
    }

    private fun QueryRowSet.toPayment() = Payment(
        id = PaymentId(this[Payments.id]!!),
        orderId = OrderId(this[Payments.orderId]!!),
        amount = Money(this[Payments.amount]!!),
        paymentDate = this[Payments.paymentDate]!!,
        method = this[Payments.method]!!.toEnumOrThrow<PaymentMethod>(),
        status = this[Payments.status]!!.toEnumOrThrow<PaymentStatus>()
    )
}
