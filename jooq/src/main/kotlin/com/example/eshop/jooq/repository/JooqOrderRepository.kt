package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.schema.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.LocalDateTime

class JooqOrderRepository(private val dsl: DSLContext) : OrderRepository {

    override fun save(order: Order): Order {
        dsl.insertInto(DSL.table(Tables.ORDER))
            .set(DSL.field(OrderFields.ID), order.id.value)
            .set(DSL.field(OrderFields.CUSTOMER_ID), order.customerId.value)
            .set(DSL.field(OrderFields.ORDER_DATE), order.orderDate)
            .set(DSL.field(OrderFields.TOTAL_AMOUNT), order.totalAmount.amount)
            .set(DSL.field(OrderFields.STATUS), order.status.name)
            .execute()
        return order
    }

    override fun findById(id: OrderId): Order? {
        val oId = DSL.field(OrderFields.ID, Long::class.java)
        val oCustomerId = DSL.field(OrderFields.CUSTOMER_ID, Long::class.java)
        val oOrderDate = DSL.field(OrderFields.ORDER_DATE, LocalDateTime::class.java)
        val oTotalAmount = DSL.field(OrderFields.TOTAL_AMOUNT, BigDecimal::class.java)
        val oStatus = DSL.field(OrderFields.STATUS, String::class.java)
        
        return dsl.select(oId, oCustomerId, oOrderDate, oTotalAmount, oStatus)
            .from(Tables.ORDER)
            .where(oId.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Order(
                    id = OrderId(record.getValue(oId)),
                    customerId = CustomerId(record.getValue(oCustomerId)),
                    orderDate = record.getValue(oOrderDate),
                    totalAmount = Money(record.getValue(oTotalAmount)),
                    status = OrderStatus.valueOf(record.getValue(oStatus))
                )
            }
    }

    override fun findAll(): List<Order> {
        val oId = DSL.field(OrderFields.ID, Long::class.java)
        val oCustomerId = DSL.field(OrderFields.CUSTOMER_ID, Long::class.java)
        val oOrderDate = DSL.field(OrderFields.ORDER_DATE, LocalDateTime::class.java)
        val oTotalAmount = DSL.field(OrderFields.TOTAL_AMOUNT, BigDecimal::class.java)
        val oStatus = DSL.field(OrderFields.STATUS, String::class.java)
        
        return dsl.select(oId, oCustomerId, oOrderDate, oTotalAmount, oStatus)
            .from(Tables.ORDER)
            .fetch()
            .map { record ->
                Order(
                    id = OrderId(record.getValue(oId)),
                    customerId = CustomerId(record.getValue(oCustomerId)),
                    orderDate = record.getValue(oOrderDate),
                    totalAmount = Money(record.getValue(oTotalAmount)),
                    status = OrderStatus.valueOf(record.getValue(oStatus))
                )
            }
    }

    override fun update(order: Order): Order {
        dsl.update(DSL.table(Tables.ORDER))
            .set(DSL.field(OrderFields.CUSTOMER_ID), order.customerId.value)
            .set(DSL.field(OrderFields.ORDER_DATE), order.orderDate)
            .set(DSL.field(OrderFields.TOTAL_AMOUNT), order.totalAmount.amount)
            .set(DSL.field(OrderFields.STATUS), order.status.name)
            .where(DSL.field(OrderFields.ID).eq(order.id.value))
            .execute()
        return order
    }

    override fun delete(id: OrderId): Boolean {
        val deleted = dsl.deleteFrom(DSL.table(Tables.ORDER))
            .where(DSL.field(OrderFields.ID).eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        val oId = DSL.field("o.${OrderFields.ID}", Long::class.java)
        val oCustId = DSL.field("o.${OrderFields.CUSTOMER_ID}", Long::class.java)
        val oDate = DSL.field("o.${OrderFields.ORDER_DATE}", LocalDateTime::class.java)
        val oAmount = DSL.field("o.${OrderFields.TOTAL_AMOUNT}", BigDecimal::class.java)
        val oStatus = DSL.field("o.${OrderFields.STATUS}", String::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val pStatus = DSL.field("p.${PaymentFields.STATUS}", String::class.java)
        val itemCount = DSL.count(DSL.field("oi.${OrderItemFields.ID}")).`as`("item_count")

        return dsl.select(oId, oCustId, oDate, oAmount, oStatus, cName, pStatus, itemCount)
            .from("${Tables.ORDER} o")
            .join("${Tables.CUSTOMER} c")
            .on("o.${OrderFields.CUSTOMER_ID} = c.${CustomerFields.ID}")
            .leftJoin("${Tables.PAYMENT} p")
            .on("o.${OrderFields.ID} = p.${PaymentFields.ORDER_ID}")
            .leftJoin("${Tables.ORDER_ITEM} oi")
            .on("o.${OrderFields.ID} = oi.${OrderItemFields.ORDER_ID}")
            .where(oCustId.eq(customerId.value))
            .groupBy(oId, oCustId, oDate, oAmount, oStatus, cName, pStatus)
            .fetch()
            .map { record ->
                OrderWithDetails(
                    order = Order(
                        id = OrderId(record.getValue(oId)),
                        customerId = CustomerId(record.getValue(oCustId)),
                        orderDate = record.getValue(oDate),
                        totalAmount = Money(record.getValue(oAmount)),
                        status = OrderStatus.valueOf(record.getValue(oStatus))
                    ),
                    paymentStatus = record.get(pStatus),
                    itemCount = record.get("item_count", Long::class.java)?.toInt() ?: 0,
                    customerName = record.getValue(cName)
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        val oId = DSL.field("o.${OrderFields.ID}", Long::class.java)
        val oCustId = DSL.field("o.${OrderFields.CUSTOMER_ID}", Long::class.java)
        val oDate = DSL.field("o.${OrderFields.ORDER_DATE}", LocalDateTime::class.java)
        val oAmount = DSL.field("o.${OrderFields.TOTAL_AMOUNT}", BigDecimal::class.java)
        val oStatus = DSL.field("o.${OrderFields.STATUS}", String::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val cEmail = DSL.field("c.${CustomerFields.EMAIL}", String::class.java)
        val pId = DSL.field("p.${PaymentFields.ID}", Long::class.java)
        val pStatus = DSL.field("p.${PaymentFields.STATUS}", String::class.java)

        return dsl.select(oId, oCustId, oDate, oAmount, oStatus, cName, cEmail)
            .from("${Tables.ORDER} o")
            .join("${Tables.CUSTOMER} c")
            .on("o.${OrderFields.CUSTOMER_ID} = c.${CustomerFields.ID}")
            .leftJoin("${Tables.PAYMENT} p")
            .on("o.${OrderFields.ID} = p.${PaymentFields.ORDER_ID}")
            .where(pId.isNull.or(pStatus.eq(PaymentStatus.PENDING.name)))
            .fetch()
            .map { record ->
                OrderWithCustomer(
                    order = Order(
                        id = OrderId(record.getValue(oId)),
                        customerId = CustomerId(record.getValue(oCustId)),
                        orderDate = record.getValue(oDate),
                        totalAmount = Money(record.getValue(oAmount)),
                        status = OrderStatus.valueOf(record.getValue(oStatus))
                    ),
                    customerName = record.getValue(cName),
                    customerEmail = record.getValue(cEmail)
                )
            }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        return dsl.select(
            DSL.field(OrderFields.STATUS),
            DSL.count()
        )
            .from(Tables.ORDER)
            .groupBy(DSL.field(OrderFields.STATUS))
            .fetch()
            .associate { record ->
                OrderStatus.valueOf(record.get(OrderFields.STATUS, String::class.java)!!) to
                        record.get(1, Long::class.java)!!
            }
    }

}
