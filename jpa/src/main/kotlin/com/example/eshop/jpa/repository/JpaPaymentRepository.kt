package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jpa.entity.OrderEntity
import com.example.eshop.jpa.entity.PaymentEntity
import jakarta.persistence.EntityManager

class JpaPaymentRepository(private val em: EntityManager) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        // Find the order entity to establish relationship
        val order = em.find(OrderEntity::class.java, payment.orderId.value)
            ?: throw IllegalArgumentException("Order not found: ${payment.orderId.value}")

        val entity = PaymentEntity(
            id = 0, // Auto-generated
            order = order,
            amount = payment.amount.amount,
            paymentDate = payment.paymentDate,
            method = payment.method.name,
            status = payment.status.name
        )
        em.persist(entity)
        em.flush() // Ensure ID is generated

        // Return new Payment with generated ID
        return Payment(
            id = PaymentId(entity.id),
            orderId = payment.orderId,
            amount = payment.amount,
            paymentDate = payment.paymentDate,
            method = payment.method,
            status = payment.status
        )
    }

    override fun findById(id: PaymentId): Payment? {
        return em.find(PaymentEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<Payment> {
        return em.createQuery("SELECT p FROM PaymentEntity p ORDER BY p.id", PaymentEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(payment: Payment): Payment {
        val entity = em.find(PaymentEntity::class.java, payment.id.value)
            ?: throw IllegalArgumentException("Payment not found: ${payment.id.value}")

        // Find the order entity if it changed
        val order = em.find(OrderEntity::class.java, payment.orderId.value)
            ?: throw IllegalArgumentException("Order not found: ${payment.orderId.value}")

        // Dirty checking - no need for merge()
        entity.order = order
        entity.amount = payment.amount.amount
        entity.paymentDate = payment.paymentDate
        entity.method = payment.method.name
        entity.status = payment.status.name

        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val entity = em.find(PaymentEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        val query = em.createQuery("""
            SELECT
                p.order.customer.id,
                p.order.customer.name,
                SUM(p.amount),
                AVG(p.amount),
                COUNT(p.id),
                COUNT(DISTINCT p.order.id)
            FROM PaymentEntity p
            WHERE p.status = 'COMPLETED'
            GROUP BY p.order.customer.id, p.order.customer.name
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            CustomerStatistics(
                customerId = row[0] as Long,
                customerName = row[1] as String,
                totalPayments = Money(row[2] as java.math.BigDecimal),
                averagePayment = Money(java.math.BigDecimal.valueOf(row[3] as Double)),
                paymentCount = row[4] as Long,
                completedOrderCount = row[5] as Long
            )
        }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val query = em.createQuery("""
            SELECT
                p.method,
                SUM(p.amount),
                COUNT(p),
                AVG(p.amount)
            FROM PaymentEntity p
            WHERE p.status = 'COMPLETED'
            GROUP BY p.method
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).associate { row ->
            val method = PaymentMethod.valueOf(row[0] as String)
            method to PaymentMethodStats(
                method = method,
                totalAmount = Money(row[1] as java.math.BigDecimal),
                count = row[2] as Long,
                averageAmount = Money(java.math.BigDecimal.valueOf(row[3] as Double))
            )
        }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return em.createQuery("""
            SELECT p FROM PaymentEntity p
            WHERE p.order.id = :orderId
        """, PaymentEntity::class.java)
            .setParameter("orderId", orderId.value)
            .resultList
            .firstOrNull()
            ?.toDomain()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val query = em.createQuery("""
            SELECT p, p.order.orderDate, p.order.customer.name, p.order.customer.email
            FROM PaymentEntity p
            WHERE p.status = 'FAILED'
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            val paymentEntity = row[0] as PaymentEntity
            PaymentWithDetails(
                payment = paymentEntity.toDomain(),
                orderDate = (row[1] as java.time.LocalDateTime).toString(),
                customerName = row[2] as String,
                customerEmail = row[3] as String
            )
        }
    }

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        // JPQL doesn't support UNION, so we split into two queries and merge in application

        // Query 1: High value customers
        val highValueQuery = em.createQuery("""
            SELECT
                p.order.customer.id,
                p.order.customer.name,
                p.order.customer.email,
                SUM(p.amount),
                COUNT(p.id)
            FROM PaymentEntity p
            WHERE p.status = 'COMPLETED'
            GROUP BY p.order.customer.id, p.order.customer.name, p.order.customer.email
            HAVING SUM(p.amount) >= :minTotalAmount
        """)
        highValueQuery.setParameter("minTotalAmount", minTotalAmount.amount)

        @Suppress("UNCHECKED_CAST")
        val highValueCustomers = (highValueQuery.resultList as List<Array<Any>>).map { row ->
            val totalAmount = Money(row[3] as java.math.BigDecimal)
            val paymentCount = row[4] as Long

            PremiumCustomerInfo(
                customerId = row[0] as Long,
                customerName = row[1] as String,
                customerEmail = row[2] as String,
                totalPaymentAmount = totalAmount,
                paymentCount = paymentCount,
                customerType = if (paymentCount >= minPaymentCount) PremiumCustomerType.BOTH else PremiumCustomerType.HIGH_VALUE
            )
        }

        // Query 2: Frequent payers
        val frequentPayerQuery = em.createQuery("""
            SELECT
                p.order.customer.id,
                p.order.customer.name,
                p.order.customer.email,
                SUM(p.amount),
                COUNT(p.id)
            FROM PaymentEntity p
            WHERE p.status = 'COMPLETED'
            GROUP BY p.order.customer.id, p.order.customer.name, p.order.customer.email
            HAVING COUNT(p.id) >= :minPaymentCount
        """)
        frequentPayerQuery.setParameter("minPaymentCount", minPaymentCount)

        @Suppress("UNCHECKED_CAST")
        val frequentPayers = (frequentPayerQuery.resultList as List<Array<Any>>).map { row ->
            val totalAmount = Money(row[3] as java.math.BigDecimal)
            val paymentCount = row[4] as Long

            PremiumCustomerInfo(
                customerId = row[0] as Long,
                customerName = row[1] as String,
                customerEmail = row[2] as String,
                totalPaymentAmount = totalAmount,
                paymentCount = paymentCount,
                customerType = if (totalAmount.amount >= minTotalAmount.amount) PremiumCustomerType.BOTH else PremiumCustomerType.FREQUENT_PAYER
            )
        }

        // Merge results, removing duplicates and updating type for BOTH
        val allCustomers = mutableMapOf<Long, PremiumCustomerInfo>()

        highValueCustomers.forEach { customer ->
            allCustomers[customer.customerId] = customer
        }

        frequentPayers.forEach { customer ->
            val existing = allCustomers[customer.customerId]
            if (existing != null && existing.customerType != PremiumCustomerType.BOTH) {
                // Update to BOTH if customer is in both categories
                allCustomers[customer.customerId] = customer.copy(customerType = PremiumCustomerType.BOTH)
            } else if (existing == null) {
                allCustomers[customer.customerId] = customer
            }
        }

        return allCustomers.values.sortedBy { it.customerId }
    }

    override fun findMonthlyPaymentTrends(): List<MonthlyPaymentTrend> {
        // JPA Native Query를 사용하여 window function 실행
        val query = em.createNativeQuery("""
            WITH monthly_stats AS (
                SELECT
                    strftime('%Y-%m', payment_date) as year_month,
                    SUM(amount) as total_amount,
                    COUNT(*) as payment_count,
                    AVG(amount) as average_amount
                FROM payment
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
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            val totalAmount = java.math.BigDecimal.valueOf((row[1] as Number).toDouble())
            val previousMonthAmount = row[4]?.let {
                java.math.BigDecimal.valueOf((it as Number).toDouble())
            }

            val growthRate = if (previousMonthAmount != null && previousMonthAmount > java.math.BigDecimal.ZERO) {
                ((totalAmount - previousMonthAmount) / previousMonthAmount * java.math.BigDecimal(100)).toDouble()
            } else null

            MonthlyPaymentTrend(
                yearMonth = row[0] as String,
                totalAmount = Money(totalAmount),
                paymentCount = (row[2] as Number).toLong(),
                averageAmount = Money(java.math.BigDecimal.valueOf((row[3] as Number).toDouble())),
                previousMonthAmount = previousMonthAmount?.let { Money(it) },
                growthRate = growthRate
            )
        }
    }

    override fun findRecentPaymentsByAllCustomers(limit: Int): List<RecentPaymentInfo> {
        require(limit > 0) { "limit must be positive" }

        // JPA Native Query를 사용하여 window function 실행
        val query = em.createNativeQuery("""
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
                FROM payment p
                INNER JOIN orders o ON p.order_id = o.id
                INNER JOIN customer c ON o.customer_id = c.id
                WHERE p.status = 'COMPLETED'
            )
            SELECT *
            FROM ranked_payments
            WHERE row_num <= ?
            ORDER BY customer_id, row_num
        """)
        query.setParameter(1, limit)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            val payment = Payment(
                id = PaymentId((row[0] as Number).toLong()),
                orderId = OrderId((row[1] as Number).toLong()),
                amount = Money(row[2] as java.math.BigDecimal),
                paymentDate = row[3] as java.time.LocalDateTime,
                method = PaymentMethod.valueOf(row[4] as String),
                status = PaymentStatus.valueOf(row[5] as String)
            )

            RecentPaymentInfo(
                customerId = (row[6] as Number).toLong(),
                customerName = row[7] as String,
                payment = payment,
                orderDate = (row[8] as java.time.LocalDateTime).toString(),
                rowNumber = (row[9] as Number).toInt()
            )
        }
    }

    private fun PaymentEntity.toDomain(): Payment {
        return Payment(
            id = PaymentId(id),
            orderId = OrderId(orderId),
            amount = Money(amount),
            paymentDate = paymentDate,
            method = PaymentMethod.valueOf(method),
            status = PaymentStatus.valueOf(status)
        )
    }
}
