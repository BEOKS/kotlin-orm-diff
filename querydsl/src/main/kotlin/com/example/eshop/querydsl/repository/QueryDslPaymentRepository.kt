package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.querydsl.entity.PaymentEntity
import com.example.eshop.querydsl.entity.QCustomerEntity
import com.example.eshop.querydsl.entity.QOrderEntity
import com.example.eshop.querydsl.entity.QPaymentEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class QueryDslPaymentRepository(private val em: EntityManager) : PaymentRepository {

    private val queryFactory = JPAQueryFactory(em)

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
        val qPayment = QPaymentEntity.paymentEntity
        
        return queryFactory
            .selectFrom(qPayment)
            .where(qPayment.id.eq(id.value))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(): List<Payment> {
        val qPayment = QPaymentEntity.paymentEntity
        
        return queryFactory
            .selectFrom(qPayment)
            .fetch()
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
        val qCustomer = QCustomerEntity.customerEntity
        val qOrder = QOrderEntity.orderEntity
        val qPayment = QPaymentEntity.paymentEntity
        
        return queryFactory
            .select(
                Projections.constructor(
                    CustomerStatistics::class.java,
                    qCustomer.id,
                    qCustomer.name,
                    Projections.constructor(
                        Money::class.java,
                        qPayment.amount.sum().coalesce(java.math.BigDecimal.ZERO)
                    ),
                    Projections.constructor(
                        Money::class.java,
                        qPayment.amount.avg().coalesce(java.math.BigDecimal.ZERO)
                    ),
                    qPayment.id.count(),
                    qOrder.id.countDistinct()
                )
            )
            .from(qCustomer)
            .join(qOrder).on(qCustomer.id.eq(qOrder.customerId))
            .join(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(qPayment.status.eq(PaymentStatus.COMPLETED.name))
            .groupBy(qCustomer.id, qCustomer.name)
            .fetch()
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val qPayment = QPaymentEntity.paymentEntity
        
        val results = queryFactory
            .select(
                qPayment.method,
                qPayment.amount.sum(),
                qPayment.id.count(),
                qPayment.amount.avg()
            )
            .from(qPayment)
            .where(qPayment.status.eq(PaymentStatus.COMPLETED.name))
            .groupBy(qPayment.method)
            .fetch()
        
        return results.associate { tuple ->
            val method = PaymentMethod.valueOf(tuple.get(qPayment.method)!!)
            method to PaymentMethodStats(
                method = method,
                totalAmount = Money(tuple.get(qPayment.amount.sum()) ?: java.math.BigDecimal.ZERO),
                count = tuple.get(qPayment.id.count()) ?: 0L,
                averageAmount = Money(tuple.get(qPayment.amount.avg()) ?: java.math.BigDecimal.ZERO)
            )
        }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        val qPayment = QPaymentEntity.paymentEntity
        
        return queryFactory
            .selectFrom(qPayment)
            .where(qPayment.orderId.eq(orderId.value))
            .fetchFirst()
            ?.toDomain()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val qPayment = QPaymentEntity.paymentEntity
        val qOrder = QOrderEntity.orderEntity
        val qCustomer = QCustomerEntity.customerEntity
        
        return queryFactory
            .select(
                Projections.constructor(
                    PaymentWithDetails::class.java,
                    Projections.constructor(
                        Payment::class.java,
                        Projections.constructor(PaymentId::class.java, qPayment.id),
                        Projections.constructor(OrderId::class.java, qPayment.orderId),
                        Projections.constructor(Money::class.java, qPayment.amount),
                        qPayment.paymentDate,
                        PaymentMethod.valueOf(qPayment.method.stringValue()),
                        PaymentStatus.valueOf(qPayment.status.stringValue())
                    ),
                    qOrder.orderDate.stringValue(),
                    qCustomer.name,
                    qCustomer.email
                )
            )
            .from(qPayment)
            .join(qOrder).on(qPayment.orderId.eq(qOrder.id))
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .where(qPayment.status.eq(PaymentStatus.FAILED.name))
            .fetch()
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

