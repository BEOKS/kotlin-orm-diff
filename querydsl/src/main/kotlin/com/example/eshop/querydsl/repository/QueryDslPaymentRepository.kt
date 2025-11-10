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
    private val qPayment = QPaymentEntity.paymentEntity
    private val qOrder = QOrderEntity.orderEntity
    private val qCustomer = QCustomerEntity.customerEntity

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
        return queryFactory
            .selectFrom(qPayment)
            .where(qPayment.id.eq(id.value))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(): List<Payment> {
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
        // Dirty Checking will automatically generate UPDATE query
        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val entity = em.find(PaymentEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
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
                customerId = tuple.get(qCustomer.id)!!,
                customerName = tuple.get(qCustomer.name)!!,
                totalPayments = Money(tuple.get(qPayment.amount.sum()) ?: java.math.BigDecimal.ZERO),
                averagePayment = Money(tuple.get(qPayment.amount.avg())?.let { java.math.BigDecimal.valueOf(it) } ?: java.math.BigDecimal.ZERO),
                paymentCount = tuple.get(qPayment.id.count()) ?: 0L,
                completedOrderCount = tuple.get(qOrder.id.countDistinct()) ?: 0L
            )
        }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
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
        return queryFactory
            .selectFrom(qPayment)
            .where(qPayment.orderId.eq(orderId.value))
            .fetchFirst()
            ?.toDomain()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
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

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        // Single query with OR condition - more efficient than UNION
        return queryFactory
            .select(
                qCustomer.id,
                qCustomer.name,
                qCustomer.email,
                qPayment.amount.sum(),
                qPayment.id.count()
            )
            .from(qCustomer)
            .join(qOrder).on(qCustomer.id.eq(qOrder.customerId))
            .join(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(qPayment.status.eq(PaymentStatus.COMPLETED.name))
            .groupBy(qCustomer.id, qCustomer.name, qCustomer.email)
            .having(
                qPayment.amount.sum().goe(minTotalAmount.amount)
                    .or(qPayment.id.count().goe(minPaymentCount))
            )
            .fetch()
            .map { tuple ->
                val totalAmount = Money(tuple.get(qPayment.amount.sum()) ?: java.math.BigDecimal.ZERO)
                val count = tuple.get(qPayment.id.count()) ?: 0L

                PremiumCustomerInfo(
                    customerId = tuple.get(qCustomer.id)!!,
                    customerName = tuple.get(qCustomer.name)!!,
                    customerEmail = tuple.get(qCustomer.email)!!,
                    totalPaymentAmount = totalAmount,
                    paymentCount = count,
                    customerType = when {
                        totalAmount.amount >= minTotalAmount.amount && count >= minPaymentCount
                            -> PremiumCustomerType.BOTH
                        totalAmount.amount >= minTotalAmount.amount
                            -> PremiumCustomerType.HIGH_VALUE
                        else -> PremiumCustomerType.FREQUENT_PAYER
                    }
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

