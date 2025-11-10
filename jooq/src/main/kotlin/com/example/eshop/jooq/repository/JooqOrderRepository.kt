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
        return dsl.select()
            .from(Tables.ORDER)
            .where(DSL.field(OrderFields.ID).eq(id.value))
            .fetchOne()
            ?.let { mapToOrder(it) }
    }

    override fun findAll(): List<Order> {
        return dsl.select()
            .from(Tables.ORDER)
            .fetch()
            .map { mapToOrder(it) }
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
        val o = DSL.table(Tables.ORDER).asTable("o")
        val c = DSL.table(Tables.CUSTOMER).asTable("c")
        val p = DSL.table(Tables.PAYMENT).asTable("p")
        val oi = DSL.table(Tables.ORDER_ITEM).asTable("oi")

        return dsl.select(
            o.field(OrderFields.ID),
            o.field(OrderFields.CUSTOMER_ID),
            o.field(OrderFields.ORDER_DATE),
            o.field(OrderFields.TOTAL_AMOUNT),
            o.field(OrderFields.STATUS),
            c.field(CustomerFields.NAME),
            p.field(PaymentFields.STATUS),
            DSL.count(oi.field(OrderItemFields.ID)).`as`("item_count")
        )
            .from(o)
            .join(c).on(o.field(OrderFields.CUSTOMER_ID).eq(c.field(CustomerFields.ID)))
            .leftJoin(p).on(o.field(OrderFields.ID).eq(p.field(PaymentFields.ORDER_ID)))
            .leftJoin(oi).on(o.field(OrderFields.ID).eq(oi.field(OrderItemFields.ORDER_ID)))
            .where(o.field(OrderFields.CUSTOMER_ID).eq(customerId.value))
            .groupBy(
                o.field(OrderFields.ID),
                o.field(OrderFields.CUSTOMER_ID),
                o.field(OrderFields.ORDER_DATE),
                o.field(OrderFields.TOTAL_AMOUNT),
                o.field(OrderFields.STATUS),
                c.field(CustomerFields.NAME),
                p.field(PaymentFields.STATUS)
            )
            .fetch()
            .map { record ->
                OrderWithDetails(
                    order = Order(
                        id = OrderId(record.get(o.field(OrderFields.ID), Long::class.java)!!),
                        customerId = CustomerId(record.get(o.field(OrderFields.CUSTOMER_ID), Long::class.java)!!),
                        orderDate = record.get(o.field(OrderFields.ORDER_DATE), LocalDateTime::class.java)!!,
                        totalAmount = Money(record.get(o.field(OrderFields.TOTAL_AMOUNT), BigDecimal::class.java)!!),
                        status = OrderStatus.valueOf(record.get(o.field(OrderFields.STATUS), String::class.java)!!)
                    ),
                    paymentStatus = record.get(p.field(PaymentFields.STATUS), String::class.java),
                    itemCount = record.get("item_count", Int::class.java) ?: 0,
                    customerName = record.get(c.field(CustomerFields.NAME), String::class.java)!!
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        val o = DSL.table(Tables.ORDER).asTable("o")
        val c = DSL.table(Tables.CUSTOMER).asTable("c")
        val p = DSL.table(Tables.PAYMENT).asTable("p")

        return dsl.select(
            o.field(OrderFields.ID),
            o.field(OrderFields.CUSTOMER_ID),
            o.field(OrderFields.ORDER_DATE),
            o.field(OrderFields.TOTAL_AMOUNT),
            o.field(OrderFields.STATUS),
            c.field(CustomerFields.NAME),
            c.field(CustomerFields.EMAIL)
        )
            .from(o)
            .join(c).on(o.field(OrderFields.CUSTOMER_ID).eq(c.field(CustomerFields.ID)))
            .leftJoin(p).on(o.field(OrderFields.ID).eq(p.field(PaymentFields.ORDER_ID)))
            .where(
                p.field(PaymentFields.ID).isNull
                    .or(p.field(PaymentFields.STATUS).eq(PaymentStatus.PENDING.name))
            )
            .fetch()
            .map { record ->
                OrderWithCustomer(
                    order = Order(
                        id = OrderId(record.get(o.field(OrderFields.ID), Long::class.java)!!),
                        customerId = CustomerId(record.get(o.field(OrderFields.CUSTOMER_ID), Long::class.java)!!),
                        orderDate = record.get(o.field(OrderFields.ORDER_DATE), LocalDateTime::class.java)!!,
                        totalAmount = Money(record.get(o.field(OrderFields.TOTAL_AMOUNT), BigDecimal::class.java)!!),
                        status = OrderStatus.valueOf(record.get(o.field(OrderFields.STATUS), String::class.java)!!)
                    ),
                    customerName = record.get(c.field(CustomerFields.NAME), String::class.java)!!,
                    customerEmail = record.get(c.field(CustomerFields.EMAIL), String::class.java)!!
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

    private fun mapToOrder(record: org.jooq.Record): Order {
        return Order(
            id = OrderId(record.get(OrderFields.ID, Long::class.java)!!),
            customerId = CustomerId(record.get(OrderFields.CUSTOMER_ID, Long::class.java)!!),
            orderDate = record.get(OrderFields.ORDER_DATE, LocalDateTime::class.java)!!,
            totalAmount = Money(record.get(OrderFields.TOTAL_AMOUNT, BigDecimal::class.java)!!),
            status = OrderStatus.valueOf(record.get(OrderFields.STATUS, String::class.java)!!)
        )
    }
}

