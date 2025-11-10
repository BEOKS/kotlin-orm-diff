package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.exposed.table.Customers
import com.example.eshop.exposed.table.Orders
import com.example.eshop.exposed.table.Payments
import com.example.eshop.exposed.util.toEnumOrThrow
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

class ExposedPaymentRepository : PaymentRepository {

    override fun save(payment: Payment): Payment = transaction {
        Payments.insert {
            it[id] = payment.id.value
            it[orderId] = payment.orderId.value
            it[amount] = payment.amount.amount
            it[paymentDate] = payment.paymentDate
            it[method] = payment.method.name
            it[status] = payment.status.name
        }
        payment
    }

    override fun findById(id: PaymentId): Payment? = transaction {
        Payments.selectAll()
            .where { Payments.id eq id.value }
            .map { it.toPayment() }
            .singleOrNull()
    }

    override fun findAll(): List<Payment> = transaction {
        Payments.selectAll()
            .map { it.toPayment() }
    }

    override fun update(payment: Payment): Payment = transaction {
        val updatedRows = Payments.update({ Payments.id eq payment.id.value }) {
            it[orderId] = payment.orderId.value
            it[amount] = payment.amount.amount
            it[paymentDate] = payment.paymentDate
            it[method] = payment.method.name
            it[status] = payment.status.name
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Payment with id ${payment.id.value} not found")
        }
        payment
    }

    override fun delete(id: PaymentId): Boolean = transaction {
        val deleted = Payments.deleteWhere { Payments.id eq id.value }
        deleted > 0
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> = transaction {
        val totalPaymentsAlias = Payments.amount.sum()
        val avgPaymentAlias = Payments.amount.avg()
        val paymentCountAlias = Payments.id.count()
        val orderCountAlias = Orders.id.countDistinct()

        Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .join(Payments, JoinType.INNER, Orders.id, Payments.orderId)
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
                    customerId = row[Customers.id],
                    customerName = row[Customers.name],
                    totalPayments = Money(row[totalPaymentsAlias] ?: BigDecimal.ZERO),
                    averagePayment = Money(row[avgPaymentAlias] ?: BigDecimal.ZERO),
                    paymentCount = row[paymentCountAlias] ?: 0L,
                    completedOrderCount = row[orderCountAlias] ?: 0L
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> = transaction {
        val totalAmountAlias = Payments.amount.sum()
        val countAlias = Payments.id.count()
        val avgAmountAlias = Payments.amount.avg()

        Payments
            .select(
                Payments.method,
                totalAmountAlias,
                countAlias,
                avgAmountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Payments.method)
            .associate { row ->
                val method = row[Payments.method].toEnumOrThrow<PaymentMethod>()
                method to PaymentMethodStats(
                    method = method,
                    totalAmount = Money(row[totalAmountAlias] ?: BigDecimal.ZERO),
                    count = row[countAlias] ?: 0L,
                    averageAmount = Money(row[avgAmountAlias] ?: BigDecimal.ZERO)
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? = transaction {
        Payments.selectAll()
            .where { Payments.orderId eq orderId.value }
            .map { it.toPayment() }
            .singleOrNull()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> = transaction {
        Payments
            .join(Orders, JoinType.INNER, Payments.orderId, Orders.id)
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .selectAll()
            .where { Payments.status eq PaymentStatus.FAILED.name }
            .map { row ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(row[Payments.id]),
                        orderId = OrderId(row[Payments.orderId]),
                        amount = Money(row[Payments.amount]),
                        paymentDate = row[Payments.paymentDate],
                        method = row[Payments.method].toEnumOrThrow<PaymentMethod>(),
                        status = row[Payments.status].toEnumOrThrow<PaymentStatus>()
                    ),
                    orderDate = row[Orders.orderDate].toString(),
                    customerName = row[Customers.name],
                    customerEmail = row[Customers.email]
                )
            }
    }

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> = transaction {
        require(minPaymentCount >= 0) { "minPaymentCount must be non-negative" }

        // Exposed는 union()과 unionAll() 메서드를 지원합니다.
        // https://www.jetbrains.com/help/exposed/dsl-joining-tables.html#union

        val totalAmountAlias = Payments.amount.sum()
        val paymentCountAlias = Payments.id.count()

        // 첫 번째 쿼리: 고액 결제자 (총 결제 금액이 minTotalAmount 이상)
        val highValueQuery = Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .join(Payments, JoinType.INNER, Orders.id, Payments.orderId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                totalAmountAlias,
                paymentCountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Customers.id, Customers.name, Customers.email)
            .having { totalAmountAlias greaterEq minTotalAmount.amount }

        // 두 번째 쿼리: 빈번한 결제자 (결제 횟수가 minPaymentCount 이상)
        val frequentPayerQuery = Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .join(Payments, JoinType.INNER, Orders.id, Payments.orderId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                totalAmountAlias,
                paymentCountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Customers.id, Customers.name, Customers.email)
            .having { paymentCountAlias greaterEq minPaymentCount }

        // UNION: 중복을 제거하고 두 쿼리 결과를 결합
        highValueQuery.union(frequentPayerQuery)
            .map { row ->
                val totalAmount = Money(row[totalAmountAlias] ?: BigDecimal.ZERO)
                val payCount = row[paymentCountAlias] ?: 0L

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
                    customerId = row[Customers.id],
                    customerName = row[Customers.name],
                    customerEmail = row[Customers.email],
                    totalPaymentAmount = totalAmount,
                    paymentCount = payCount,
                    customerType = customerType
                )
            }
    }

    override fun findMonthlyPaymentTrends(): List<MonthlyPaymentTrend> = transaction {
        // Exposed는 Native Query를 exec() 메서드로 실행할 수 있습니다.
        // https://jetbrains.github.io/Exposed/deep-dive-into-dsl.html#execute-custom-sql

        val sql = """
            WITH monthly_stats AS (
                SELECT
                    strftime('%Y-%m', payment_date) as year_month,
                    SUM(amount) as total_amount,
                    COUNT(*) as payment_count,
                    AVG(amount) as average_amount
                FROM payments
                WHERE status = 'COMPLETED'
                GROUP BY strftime('%Y-%m', payment_date)
            )
            SELECT
                year_month,
                total_amount,
                payment_count,
                average_amount,
                LAG(total_amount) OVER (ORDER BY year_month) as previous_month_amount
            FROM monthly_stats
            ORDER BY year_month
        """.trimIndent()

        val results = mutableListOf<MonthlyPaymentTrend>()

        exec(sql) { rs ->
            while (rs.next()) {
                val totalAmount = rs.getBigDecimal("total_amount")
                val previousMonthAmount = rs.getBigDecimal("previous_month_amount")

                val growthRate = if (previousMonthAmount != null && previousMonthAmount > BigDecimal.ZERO) {
                    ((totalAmount - previousMonthAmount) / previousMonthAmount * BigDecimal(100)).toDouble()
                } else null

                results.add(
                    MonthlyPaymentTrend(
                        yearMonth = rs.getString("year_month"),
                        totalAmount = Money(totalAmount),
                        paymentCount = rs.getLong("payment_count"),
                        averageAmount = Money(rs.getBigDecimal("average_amount")),
                        previousMonthAmount = previousMonthAmount?.let { Money(it) },
                        growthRate = growthRate
                    )
                )
            }
        }

        results
    }

    override fun findRecentPaymentsByAllCustomers(limit: Int): List<RecentPaymentInfo> = transaction {
        require(limit > 0) { "limit must be positive" }

        val sql = """
            WITH ranked_payments AS (
                SELECT
                    p.id as payment_id,
                    p.order_id,
                    p.amount,
                    p.payment_date,
                    p.method,
                    p.status,
                    c.id as customer_id,
                    c.name as customer_name,
                    o.order_date,
                    ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY p.payment_date DESC) as row_num
                FROM payments p
                INNER JOIN orders o ON p.order_id = o.id
                INNER JOIN customers c ON o.customer_id = c.id
                WHERE p.status = 'COMPLETED'
            )
            SELECT *
            FROM ranked_payments
            WHERE row_num <= ?
            ORDER BY customer_id, row_num
        """.trimIndent()

        val results = mutableListOf<RecentPaymentInfo>()

        exec(sql, listOf(IntegerColumnType() to limit)) { rs ->
            while (rs.next()) {
                results.add(
                    RecentPaymentInfo(
                        customerId = rs.getLong("customer_id"),
                        customerName = rs.getString("customer_name"),
                        payment = Payment(
                            id = PaymentId(rs.getLong("payment_id")),
                            orderId = OrderId(rs.getLong("order_id")),
                            amount = Money(rs.getBigDecimal("amount")),
                            paymentDate = rs.getObject("payment_date", java.time.LocalDateTime::class.java),
                            method = rs.getString("method").toEnumOrThrow<PaymentMethod>(),
                            status = rs.getString("status").toEnumOrThrow<PaymentStatus>()
                        ),
                        orderDate = rs.getString("order_date"),
                        rowNumber = rs.getInt("row_num")
                    )
                )
            }
        }

        results
    }

    private fun ResultRow.toPayment() = Payment(
        id = PaymentId(this[Payments.id]),
        orderId = OrderId(this[Payments.orderId]),
        amount = Money(this[Payments.amount]),
        paymentDate = this[Payments.paymentDate],
        method = this[Payments.method].toEnumOrThrow<PaymentMethod>(),
        status = this[Payments.status].toEnumOrThrow<PaymentStatus>()
    )
}

