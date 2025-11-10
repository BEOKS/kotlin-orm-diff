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
        
        val results = queryFactory
            .select(
                qCustomer.id,
                qCustomer.name,
                qPayment.amount.sum().coalesce(java.math.BigDecimal.ZERO),
                qPayment.amount.avg().coalesce(0.0),
                qPayment.id.count(),
                qOrder.id.countDistinct()
            )
            .from(qCustomer)
            .join(qOrder).on(qCustomer.id.eq(qOrder.customerId))
            .join(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(qPayment.status.eq(PaymentStatus.COMPLETED.name))
            .groupBy(qCustomer.id, qCustomer.name)
            .fetch()
        
        return results.map { tuple ->
            CustomerStatistics(
                customerId = tuple.get(0, Long::class.java)!!,
                customerName = tuple.get(1, String::class.java)!!,
                totalPayments = Money(tuple.get(2, java.math.BigDecimal::class.java) ?: java.math.BigDecimal.ZERO),
                averagePayment = Money(tuple.get(3, Double::class.java)?.let { java.math.BigDecimal.valueOf(it) } ?: java.math.BigDecimal.ZERO),
                paymentCount = tuple.get(4, Long::class.java) ?: 0L,
                completedOrderCount = tuple.get(5, Long::class.java) ?: 0L
            )
        }
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
                averageAmount = Money(tuple.get(qPayment.amount.avg())?.let { java.math.BigDecimal.valueOf(it) } ?: java.math.BigDecimal.ZERO)
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
        
        val results = queryFactory
            .select(
                qPayment.id,
                qPayment.orderId,
                qPayment.amount,
                qPayment.paymentDate,
                qPayment.method,
                qPayment.status,
                qOrder.orderDate,
                qCustomer.name,
                qCustomer.email
            )
            .from(qPayment)
            .join(qOrder).on(qPayment.orderId.eq(qOrder.id))
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .where(qPayment.status.eq(PaymentStatus.FAILED.name))
            .fetch()
        
        return results.map { tuple ->
            PaymentWithDetails(
                payment = Payment(
                    id = PaymentId(tuple.get(qPayment.id)!!),
                    orderId = OrderId(tuple.get(qPayment.orderId)!!),
                    amount = Money(tuple.get(qPayment.amount)!!),
                    paymentDate = tuple.get(qPayment.paymentDate)!!,
                    method = PaymentMethod.valueOf(tuple.get(qPayment.method)!!),
                    status = PaymentStatus.valueOf(tuple.get(qPayment.status)!!)
                ),
                orderDate = tuple.get(qOrder.orderDate)!!.toString(),
                customerName = tuple.get(qCustomer.name)!!,
                customerEmail = tuple.get(qCustomer.email)!!
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

