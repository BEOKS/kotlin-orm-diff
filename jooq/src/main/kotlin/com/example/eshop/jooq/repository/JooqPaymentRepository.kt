package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.schema.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.LocalDateTime

class JooqPaymentRepository(private val dsl: DSLContext) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        dsl.insertInto(DSL.table(Tables.PAYMENT))
            .set(DSL.field(PaymentFields.ID), payment.id.value)
            .set(DSL.field(PaymentFields.ORDER_ID), payment.orderId.value)
            .set(DSL.field(PaymentFields.AMOUNT), payment.amount.amount)
            .set(DSL.field(PaymentFields.PAYMENT_DATE), payment.paymentDate)
            .set(DSL.field(PaymentFields.METHOD), payment.method.name)
            .set(DSL.field(PaymentFields.STATUS), payment.status.name)
            .execute()
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return dsl.select()
            .from(Tables.PAYMENT)
            .where(DSL.field(PaymentFields.ID).eq(id.value))
            .fetchOne()
            ?.let { mapToPayment(it) }
    }

    override fun findAll(): List<Payment> {
        return dsl.select()
            .from(Tables.PAYMENT)
            .fetch()
            .map { mapToPayment(it) }
    }

    override fun update(payment: Payment): Payment {
        dsl.update(DSL.table(Tables.PAYMENT))
            .set(DSL.field(PaymentFields.ORDER_ID), payment.orderId.value)
            .set(DSL.field(PaymentFields.AMOUNT), payment.amount.amount)
            .set(DSL.field(PaymentFields.PAYMENT_DATE), payment.paymentDate)
            .set(DSL.field(PaymentFields.METHOD), payment.method.name)
            .set(DSL.field(PaymentFields.STATUS), payment.status.name)
            .where(DSL.field(PaymentFields.ID).eq(payment.id.value))
            .execute()
        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val deleted = dsl.deleteFrom(DSL.table(Tables.PAYMENT))
            .where(DSL.field(PaymentFields.ID).eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        val p = DSL.table(Tables.PAYMENT).asTable("p")
        val o = DSL.table(Tables.ORDER).asTable("o")
        val c = DSL.table(Tables.CUSTOMER).asTable("c")

        return dsl.select(
            c.field(CustomerFields.ID),
            c.field(CustomerFields.NAME),
            DSL.sum(p.field(PaymentFields.AMOUNT, BigDecimal::class.java)).`as`("total_payments"),
            DSL.avg(p.field(PaymentFields.AMOUNT, BigDecimal::class.java)).`as`("avg_payment"),
            DSL.count(p.field(PaymentFields.ID)).`as`("payment_count"),
            DSL.countDistinct(o.field(OrderFields.ID)).`as`("order_count")
        )
            .from(c)
            .join(o).on(c.field(CustomerFields.ID).eq(o.field(OrderFields.CUSTOMER_ID)))
            .join(p).on(o.field(OrderFields.ID).eq(p.field(PaymentFields.ORDER_ID)))
            .where(p.field(PaymentFields.STATUS).eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.field(CustomerFields.ID), c.field(CustomerFields.NAME))
            .fetch()
            .map { record ->
                CustomerStatistics(
                    customerId = record.get(c.field(CustomerFields.ID), Long::class.java)!!,
                    customerName = record.get(c.field(CustomerFields.NAME), String::class.java)!!,
                    totalPayments = Money(record.get("total_payments", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    averagePayment = Money(record.get("avg_payment", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                    completedOrderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        return dsl.select(
            DSL.field(PaymentFields.METHOD),
            DSL.sum(DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)).`as`("total_amount"),
            DSL.count().`as`("count"),
            DSL.avg(DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)).`as`("avg_amount")
        )
            .from(Tables.PAYMENT)
            .where(DSL.field(PaymentFields.STATUS).eq(PaymentStatus.COMPLETED.name))
            .groupBy(DSL.field(PaymentFields.METHOD))
            .fetch()
            .associate { record ->
                val method = PaymentMethod.valueOf(record.get(PaymentFields.METHOD, String::class.java)!!)
                method to PaymentMethodStats(
                    method = method,
                    totalAmount = Money(record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    count = record.get("count", Long::class.java) ?: 0L,
                    averageAmount = Money(record.get("avg_amount", BigDecimal::class.java) ?: BigDecimal.ZERO)
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return dsl.select()
            .from(Tables.PAYMENT)
            .where(DSL.field(PaymentFields.ORDER_ID).eq(orderId.value))
            .fetchOne()
            ?.let { mapToPayment(it) }
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val p = DSL.table(Tables.PAYMENT).asTable("p")
        val o = DSL.table(Tables.ORDER).asTable("o")
        val c = DSL.table(Tables.CUSTOMER).asTable("c")

        return dsl.select(
            p.field(PaymentFields.ID),
            p.field(PaymentFields.ORDER_ID),
            p.field(PaymentFields.AMOUNT),
            p.field(PaymentFields.PAYMENT_DATE),
            p.field(PaymentFields.METHOD),
            p.field(PaymentFields.STATUS),
            o.field(OrderFields.ORDER_DATE),
            c.field(CustomerFields.NAME),
            c.field(CustomerFields.EMAIL)
        )
            .from(p)
            .join(o).on(p.field(PaymentFields.ORDER_ID).eq(o.field(OrderFields.ID)))
            .join(c).on(o.field(OrderFields.CUSTOMER_ID).eq(c.field(CustomerFields.ID)))
            .where(p.field(PaymentFields.STATUS).eq(PaymentStatus.FAILED.name))
            .fetch()
            .map { record ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(record.get(p.field(PaymentFields.ID), Long::class.java)!!),
                        orderId = OrderId(record.get(p.field(PaymentFields.ORDER_ID), Long::class.java)!!),
                        amount = Money(record.get(p.field(PaymentFields.AMOUNT), BigDecimal::class.java)!!),
                        paymentDate = record.get(p.field(PaymentFields.PAYMENT_DATE), LocalDateTime::class.java)!!,
                        method = PaymentMethod.valueOf(record.get(p.field(PaymentFields.METHOD), String::class.java)!!),
                        status = PaymentStatus.valueOf(record.get(p.field(PaymentFields.STATUS), String::class.java)!!)
                    ),
                    orderDate = record.get(o.field(OrderFields.ORDER_DATE), LocalDateTime::class.java)!!.toString(),
                    customerName = record.get(c.field(CustomerFields.NAME), String::class.java)!!,
                    customerEmail = record.get(c.field(CustomerFields.EMAIL), String::class.java)!!
                )
            }
    }

    private fun mapToPayment(record: org.jooq.Record): Payment {
        return Payment(
            id = PaymentId(record.get(PaymentFields.ID, Long::class.java)!!),
            orderId = OrderId(record.get(PaymentFields.ORDER_ID, Long::class.java)!!),
            amount = Money(record.get(PaymentFields.AMOUNT, BigDecimal::class.java)!!),
            paymentDate = record.get(PaymentFields.PAYMENT_DATE, LocalDateTime::class.java)!!,
            method = PaymentMethod.valueOf(record.get(PaymentFields.METHOD, String::class.java)!!),
            status = PaymentStatus.valueOf(record.get(PaymentFields.STATUS, String::class.java)!!)
        )
    }
}

