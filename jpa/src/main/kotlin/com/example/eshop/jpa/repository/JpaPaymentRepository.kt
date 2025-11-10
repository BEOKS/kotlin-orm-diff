package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jpa.entity.PaymentEntity
import jakarta.persistence.EntityManager

class JpaPaymentRepository(private val em: EntityManager) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = PaymentEntity(
            id = payment.id.value,
            orderId = payment.orderId.value,
            amount = payment.amount.amount,
            paymentDate = payment.paymentDate,
            method = payment.method.name,
            status = payment.status.name
        )
        em.persist(entity)
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return em.find(PaymentEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<Payment> {
        return em.createQuery("SELECT p FROM PaymentEntity p", PaymentEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(payment: Payment): Payment {
        val entity = em.find(PaymentEntity::class.java, payment.id.value)
            ?: throw IllegalArgumentException("Payment not found: ${payment.id.value}")
        
        entity.orderId = payment.orderId.value
        entity.amount = payment.amount.amount
        entity.paymentDate = payment.paymentDate
        entity.method = payment.method.name
        entity.status = payment.status.name
        em.merge(entity)
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
                c.id,
                c.name,
                SUM(p.amount),
                AVG(p.amount),
                COUNT(p.id),
                COUNT(DISTINCT o.id)
            FROM CustomerEntity c
            JOIN OrderEntity o ON c.id = o.customerId
            JOIN PaymentEntity p ON o.id = p.orderId
            WHERE p.status = 'COMPLETED'
            GROUP BY c.id, c.name
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
            WHERE p.orderId = :orderId
        """, PaymentEntity::class.java)
            .setParameter("orderId", orderId.value)
            .resultList
            .firstOrNull()
            ?.toDomain()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val query = em.createQuery("""
            SELECT p, o.orderDate, c.name, c.email
            FROM PaymentEntity p
            JOIN OrderEntity o ON p.orderId = o.id
            JOIN CustomerEntity c ON o.customerId = c.id
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

