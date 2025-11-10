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
        val pId = DSL.field(PaymentFields.ID, Long::class.java)
        val pOrderId = DSL.field(PaymentFields.ORDER_ID, Long::class.java)
        val pAmount = DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)
        val pPaymentDate = DSL.field(PaymentFields.PAYMENT_DATE, LocalDateTime::class.java)
        val pMethod = DSL.field(PaymentFields.METHOD, String::class.java)
        val pStatus = DSL.field(PaymentFields.STATUS, String::class.java)
        
        return dsl.select(pId, pOrderId, pAmount, pPaymentDate, pMethod, pStatus)
            .from(Tables.PAYMENT)
            .where(pId.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Payment(
                    id = PaymentId(record.getValue(pId)),
                    orderId = OrderId(record.getValue(pOrderId)),
                    amount = Money(record.getValue(pAmount)),
                    paymentDate = record.getValue(pPaymentDate),
                    method = PaymentMethod.valueOf(record.getValue(pMethod)),
                    status = PaymentStatus.valueOf(record.getValue(pStatus))
                )
            }
    }

    override fun findAll(): List<Payment> {
        val pId = DSL.field(PaymentFields.ID, Long::class.java)
        val pOrderId = DSL.field(PaymentFields.ORDER_ID, Long::class.java)
        val pAmount = DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)
        val pPaymentDate = DSL.field(PaymentFields.PAYMENT_DATE, LocalDateTime::class.java)
        val pMethod = DSL.field(PaymentFields.METHOD, String::class.java)
        val pStatus = DSL.field(PaymentFields.STATUS, String::class.java)
        
        return dsl.select(pId, pOrderId, pAmount, pPaymentDate, pMethod, pStatus)
            .from(Tables.PAYMENT)
            .fetch()
            .map { record ->
                Payment(
                    id = PaymentId(record.getValue(pId)),
                    orderId = OrderId(record.getValue(pOrderId)),
                    amount = Money(record.getValue(pAmount)),
                    paymentDate = record.getValue(pPaymentDate),
                    method = PaymentMethod.valueOf(record.getValue(pMethod)),
                    status = PaymentStatus.valueOf(record.getValue(pStatus))
                )
            }
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
        val cId = DSL.field("c.${CustomerFields.ID}", Long::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val pAmount = DSL.field("p.${PaymentFields.AMOUNT}", BigDecimal::class.java)
        val pId = DSL.field("p.${PaymentFields.ID}", Long::class.java)
        val oId = DSL.field("o.${OrderFields.ID}", Long::class.java)
        val pStatus = DSL.field("p.${PaymentFields.STATUS}", String::class.java)
        
        val totalPay = DSL.sum(pAmount).`as`("total_payments")
        val avgPay = DSL.avg(pAmount).`as`("avg_payment")
        val payCnt = DSL.count(pId).`as`("payment_count")
        val ordCnt = DSL.countDistinct(oId).`as`("order_count")

        return dsl.select(cId, cName, totalPay, avgPay, payCnt, ordCnt)
            .from("${Tables.CUSTOMER} c")
            .join("${Tables.ORDER} o")
            .on("c.${CustomerFields.ID} = o.${OrderFields.CUSTOMER_ID}")
            .join("${Tables.PAYMENT} p")
            .on("o.${OrderFields.ID} = p.${PaymentFields.ORDER_ID}")
            .where(pStatus.eq(PaymentStatus.COMPLETED.name))
            .groupBy(cId, cName)
            .fetch()
            .map { record ->
                CustomerStatistics(
                    customerId = record.getValue(cId),
                    customerName = record.getValue(cName),
                    totalPayments = Money((record.get("total_payments", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    averagePayment = Money((record.get("avg_payment", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                    completedOrderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val method = DSL.field(PaymentFields.METHOD, String::class.java)
        val amount = DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)
        val totalAmt = DSL.sum(amount).`as`("total_amount")
        val cnt = DSL.count().`as`("count")
        val avgAmt = DSL.avg(amount).`as`("avg_amount")

        return dsl.select(method, totalAmt, cnt, avgAmt)
            .from(Tables.PAYMENT)
            .where(DSL.field(PaymentFields.STATUS).eq(PaymentStatus.COMPLETED.name))
            .groupBy(method)
            .fetch()
            .associate { record ->
                val payMethod = PaymentMethod.valueOf(record.getValue(method))
                payMethod to PaymentMethodStats(
                    method = payMethod,
                    totalAmount = Money((record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)),
                    count = record.get("count", Long::class.java) ?: 0L,
                    averageAmount = Money((record.get("avg_amount", BigDecimal::class.java) ?: BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP))
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        val pId = DSL.field(PaymentFields.ID, Long::class.java)
        val pOrderId = DSL.field(PaymentFields.ORDER_ID, Long::class.java)
        val pAmount = DSL.field(PaymentFields.AMOUNT, BigDecimal::class.java)
        val pPaymentDate = DSL.field(PaymentFields.PAYMENT_DATE, LocalDateTime::class.java)
        val pMethod = DSL.field(PaymentFields.METHOD, String::class.java)
        val pStatus = DSL.field(PaymentFields.STATUS, String::class.java)
        
        return dsl.select(pId, pOrderId, pAmount, pPaymentDate, pMethod, pStatus)
            .from(Tables.PAYMENT)
            .where(pOrderId.eq(orderId.value))
            .fetchOne()
            ?.let { record ->
                Payment(
                    id = PaymentId(record.getValue(pId)),
                    orderId = OrderId(record.getValue(pOrderId)),
                    amount = Money(record.getValue(pAmount)),
                    paymentDate = record.getValue(pPaymentDate),
                    method = PaymentMethod.valueOf(record.getValue(pMethod)),
                    status = PaymentStatus.valueOf(record.getValue(pStatus))
                )
            }
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        val pId = DSL.field("p.${PaymentFields.ID}", Long::class.java)
        val pOrderId = DSL.field("p.${PaymentFields.ORDER_ID}", Long::class.java)
        val pAmount = DSL.field("p.${PaymentFields.AMOUNT}", BigDecimal::class.java)
        val pDate = DSL.field("p.${PaymentFields.PAYMENT_DATE}", LocalDateTime::class.java)
        val pMethod = DSL.field("p.${PaymentFields.METHOD}", String::class.java)
        val pStatus = DSL.field("p.${PaymentFields.STATUS}", String::class.java)
        val oDate = DSL.field("o.${OrderFields.ORDER_DATE}", LocalDateTime::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val cEmail = DSL.field("c.${CustomerFields.EMAIL}", String::class.java)

        return dsl.select(pId, pOrderId, pAmount, pDate, pMethod, pStatus, oDate, cName, cEmail)
            .from("${Tables.PAYMENT} p")
            .join("${Tables.ORDER} o")
            .on("p.${PaymentFields.ORDER_ID} = o.${OrderFields.ID}")
            .join("${Tables.CUSTOMER} c")
            .on("o.${OrderFields.CUSTOMER_ID} = c.${CustomerFields.ID}")
            .where(pStatus.eq(PaymentStatus.FAILED.name))
            .fetch()
            .map { record ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(record.getValue(pId)),
                        orderId = OrderId(record.getValue(pOrderId)),
                        amount = Money(record.getValue(pAmount)),
                        paymentDate = record.getValue(pDate),
                        method = PaymentMethod.valueOf(record.getValue(pMethod)),
                        status = PaymentStatus.valueOf(record.getValue(pStatus))
                    ),
                    orderDate = record.getValue(oDate).toString(),
                    customerName = record.getValue(cName),
                    customerEmail = record.getValue(cEmail)
                )
            }
    }

}
