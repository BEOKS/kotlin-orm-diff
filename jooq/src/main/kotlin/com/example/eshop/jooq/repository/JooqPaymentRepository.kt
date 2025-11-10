package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.generated.tables.references.PAYMENT
import com.example.eshop.jooq.generated.tables.references.ORDERS
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal

class JooqPaymentRepository(private val dsl: DSLContext) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        dsl.insertInto(PAYMENT)
            .set(PAYMENT.ID, payment.id.value)
            .set(PAYMENT.ORDER_ID, payment.orderId.value)
            .set(PAYMENT.AMOUNT, payment.amount.amount)
            .set(PAYMENT.PAYMENT_DATE, payment.paymentDate)
            .set(PAYMENT.METHOD, payment.method.name)
            .set(PAYMENT.STATUS, payment.status.name)
            .execute()
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT, PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .where(PAYMENT.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Payment(
                    id = PaymentId(record.getValue(PAYMENT.ID)!!),
                    orderId = OrderId(record.getValue(PAYMENT.ORDER_ID)!!),
                    amount = Money(record.getValue(PAYMENT.AMOUNT)!!),
                    paymentDate = record.getValue(PAYMENT.PAYMENT_DATE)!!,
                    method = PaymentMethod.valueOf(record.getValue(PAYMENT.METHOD)!!),
                    status = PaymentStatus.valueOf(record.getValue(PAYMENT.STATUS)!!)
                )
            }
    }

    override fun findAll(): List<Payment> {
        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT, PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .fetch()
            .map { record ->
                Payment(
                    id = PaymentId(record.getValue(PAYMENT.ID)!!),
                    orderId = OrderId(record.getValue(PAYMENT.ORDER_ID)!!),
                    amount = Money(record.getValue(PAYMENT.AMOUNT)!!),
                    paymentDate = record.getValue(PAYMENT.PAYMENT_DATE)!!,
                    method = PaymentMethod.valueOf(record.getValue(PAYMENT.METHOD)!!),
                    status = PaymentStatus.valueOf(record.getValue(PAYMENT.STATUS)!!)
                )
            }
    }

    override fun update(payment: Payment): Payment {
        dsl.update(PAYMENT)
            .set(PAYMENT.ORDER_ID, payment.orderId.value)
            .set(PAYMENT.AMOUNT, payment.amount.amount)
            .set(PAYMENT.PAYMENT_DATE, payment.paymentDate)
            .set(PAYMENT.METHOD, payment.method.name)
            .set(PAYMENT.STATUS, payment.status.name)
            .where(PAYMENT.ID.eq(payment.id.value))
            .execute()
        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val deleted = dsl.deleteFrom(PAYMENT)
            .where(PAYMENT.ID.eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")
        val p = PAYMENT.`as`("p")
        
        val totalPay = DSL.sum(p.AMOUNT).`as`("total_payments")
        val avgPay = DSL.avg(p.AMOUNT).`as`("avg_payment")
        val payCnt = DSL.count(p.ID).`as`("payment_count")
        val ordCnt = DSL.countDistinct(o.ID).`as`("order_count")

        return dsl.select(c.ID, c.NAME, totalPay, avgPay, payCnt, ordCnt)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .join(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.ID, c.NAME)
            .fetch()
            .map { record ->
                CustomerStatistics(
                    customerId = record.getValue(c.ID)!!,
                    customerName = record.getValue(c.NAME)!!,
                    totalPayments = Money((record.get("total_payments", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    averagePayment = Money((record.get("avg_payment", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                    completedOrderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val totalAmt = DSL.sum(PAYMENT.AMOUNT).`as`("total_amount")
        val cnt = DSL.count().`as`("count")
        val avgAmt = DSL.avg(PAYMENT.AMOUNT).`as`("avg_amount")

        return dsl.select(PAYMENT.METHOD, totalAmt, cnt, avgAmt)
            .from(PAYMENT)
            .where(PAYMENT.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(PAYMENT.METHOD)
            .fetch()
            .associate { record ->
                val payMethod = PaymentMethod.valueOf(record.getValue(PAYMENT.METHOD)!!)
                payMethod to PaymentMethodStats(
                    method = payMethod,
                    totalAmount = Money((record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    count = record.get("count", Long::class.java) ?: 0L,
                    averageAmount = Money((record.get("avg_amount", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP))
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT, PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .where(PAYMENT.ORDER_ID.eq(orderId.value))
            .fetchOne()
            ?.let { record ->
                Payment(
                    id = PaymentId(record.getValue(PAYMENT.ID)!!),
                    orderId = OrderId(record.getValue(PAYMENT.ORDER_ID)!!),
                    amount = Money(record.getValue(PAYMENT.AMOUNT)!!),
                    paymentDate = record.getValue(PAYMENT.PAYMENT_DATE)!!,
                    method = PaymentMethod.valueOf(record.getValue(PAYMENT.METHOD)!!),
                    status = PaymentStatus.valueOf(record.getValue(PAYMENT.STATUS)!!)
                )
            }
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val p = PAYMENT.`as`("p")
        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")

        return dsl.select(p.ID, p.ORDER_ID, p.AMOUNT, p.PAYMENT_DATE, p.METHOD, p.STATUS, o.ORDER_DATE, c.NAME, c.EMAIL)
            .from(p)
            .join(o).on(p.ORDER_ID.eq(o.ID))
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .where(p.STATUS.eq(PaymentStatus.FAILED.name))
            .fetch()
            .map { record ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(record.getValue(p.ID)!!),
                        orderId = OrderId(record.getValue(p.ORDER_ID)!!),
                        amount = Money(record.getValue(p.AMOUNT)!!),
                        paymentDate = record.getValue(p.PAYMENT_DATE)!!,
                        method = PaymentMethod.valueOf(record.getValue(p.METHOD)!!),
                        status = PaymentStatus.valueOf(record.getValue(p.STATUS)!!)
                    ),
                    orderDate = record.getValue(o.ORDER_DATE)!!.toString(),
                    customerName = record.getValue(c.NAME)!!,
                    customerEmail = record.getValue(c.EMAIL)!!
                )
            }
    }

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")
        val p = PAYMENT.`as`("p")

        val totalAmount = DSL.sum(p.AMOUNT).`as`("total_amount")
        val paymentCount = DSL.count(p.ID).`as`("payment_count")

        // 첫 번째 쿼리: 고액 결제자 (총 결제 금액이 minTotalAmount 이상)
        val highValueQuery = dsl
            .select(c.ID, c.NAME, c.EMAIL, totalAmount, paymentCount)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .join(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.ID, c.NAME, c.EMAIL)
            .having(DSL.sum(p.AMOUNT).ge(minTotalAmount.amount))

        // 두 번째 쿼리: 빈번한 결제자 (결제 횟수가 minPaymentCount 이상)
        val frequentPayerQuery = dsl
            .select(c.ID, c.NAME, c.EMAIL, totalAmount, paymentCount)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .join(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.ID, c.NAME, c.EMAIL)
            .having(DSL.count(p.ID).ge(minPaymentCount.toInt()))

        // UNION: jOOQ는 union을 지원합니다
        val unionResults = highValueQuery.union(frequentPayerQuery).fetch()

        return unionResults.map { record ->
            val totalAmt = Money((record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP))
            val payCnt = record.get("payment_count", Long::class.java) ?: 0L
            
            val customerType = when {
                totalAmt.amount >= minTotalAmount.amount && payCnt >= minPaymentCount -> PremiumCustomerType.BOTH
                totalAmt.amount >= minTotalAmount.amount -> PremiumCustomerType.HIGH_VALUE
                else -> PremiumCustomerType.FREQUENT_PAYER
            }
            
            PremiumCustomerInfo(
                customerId = record.getValue(c.ID)!!,
                customerName = record.getValue(c.NAME)!!,
                customerEmail = record.getValue(c.EMAIL)!!,
                totalPaymentAmount = totalAmt,
                paymentCount = payCnt,
                customerType = customerType
            )
        }
    }

}
