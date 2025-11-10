package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.generated.tables.references.ORDERS
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import com.example.eshop.jooq.generated.tables.references.PAYMENT
import com.example.eshop.jooq.generated.tables.references.ORDER_ITEM
import org.jooq.DSLContext
import org.jooq.impl.DSL

class JooqOrderRepository(private val dsl: DSLContext) : OrderRepository {

    override fun save(order: Order): Order {
        dsl.insertInto(ORDERS)
            .set(ORDERS.ID, order.id.value)
            .set(ORDERS.CUSTOMER_ID, order.customerId.value)
            .set(ORDERS.ORDER_DATE, order.orderDate)
            .set(ORDERS.TOTAL_AMOUNT, order.totalAmount.amount)
            .set(ORDERS.STATUS, order.status.name)
            .execute()
        return order
    }

    override fun findById(id: OrderId): Order? {
        return dsl.select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.ORDER_DATE, ORDERS.TOTAL_AMOUNT, ORDERS.STATUS)
            .from(ORDERS)
            .where(ORDERS.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Order(
                    id = OrderId(record.getValue(ORDERS.ID)!!),
                    customerId = CustomerId(record.getValue(ORDERS.CUSTOMER_ID)!!),
                    orderDate = record.getValue(ORDERS.ORDER_DATE)!!,
                    totalAmount = Money(record.getValue(ORDERS.TOTAL_AMOUNT)!!),
                    status = OrderStatus.valueOf(record.getValue(ORDERS.STATUS)!!)
                )
            }
    }

    override fun findAll(): List<Order> {
        return dsl.select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.ORDER_DATE, ORDERS.TOTAL_AMOUNT, ORDERS.STATUS)
            .from(ORDERS)
            .fetch()
            .map { record ->
                Order(
                    id = OrderId(record.getValue(ORDERS.ID)!!),
                    customerId = CustomerId(record.getValue(ORDERS.CUSTOMER_ID)!!),
                    orderDate = record.getValue(ORDERS.ORDER_DATE)!!,
                    totalAmount = Money(record.getValue(ORDERS.TOTAL_AMOUNT)!!),
                    status = OrderStatus.valueOf(record.getValue(ORDERS.STATUS)!!)
                )
            }
    }

    override fun update(order: Order): Order {
        dsl.update(ORDERS)
            .set(ORDERS.CUSTOMER_ID, order.customerId.value)
            .set(ORDERS.ORDER_DATE, order.orderDate)
            .set(ORDERS.TOTAL_AMOUNT, order.totalAmount.amount)
            .set(ORDERS.STATUS, order.status.name)
            .where(ORDERS.ID.eq(order.id.value))
            .execute()
        return order
    }

    override fun delete(id: OrderId): Boolean {
        val deleted = dsl.deleteFrom(ORDERS)
            .where(ORDERS.ID.eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")
        val oi = ORDER_ITEM.`as`("oi")
        val itemCount = DSL.count(oi.ID).`as`("item_count")

        return dsl.select(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS, c.NAME, p.STATUS, itemCount)
            .from(o)
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
            .leftJoin(oi).on(o.ID.eq(oi.ORDER_ID))
            .where(o.CUSTOMER_ID.eq(customerId.value))
            .groupBy(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS, c.NAME, p.STATUS)
            .fetch()
            .map { record ->
                OrderWithDetails(
                    order = Order(
                        id = OrderId(record.getValue(o.ID)!!),
                        customerId = CustomerId(record.getValue(o.CUSTOMER_ID)!!),
                        orderDate = record.getValue(o.ORDER_DATE)!!,
                        totalAmount = Money(record.getValue(o.TOTAL_AMOUNT)!!),
                        status = OrderStatus.valueOf(record.getValue(o.STATUS)!!)
                    ),
                    paymentStatus = record.get(p.STATUS),
                    itemCount = record.get("item_count", Long::class.java)?.toInt() ?: 0,
                    customerName = record.getValue(c.NAME)!!
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")

        return dsl.select(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS, c.NAME, c.EMAIL)
            .from(o)
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.ID.isNull.or(p.STATUS.eq(PaymentStatus.PENDING.name)))
            .fetch()
            .map { record ->
                OrderWithCustomer(
                    order = Order(
                        id = OrderId(record.getValue(o.ID)!!),
                        customerId = CustomerId(record.getValue(o.CUSTOMER_ID)!!),
                        orderDate = record.getValue(o.ORDER_DATE)!!,
                        totalAmount = Money(record.getValue(o.TOTAL_AMOUNT)!!),
                        status = OrderStatus.valueOf(record.getValue(o.STATUS)!!)
                    ),
                    customerName = record.getValue(c.NAME)!!,
                    customerEmail = record.getValue(c.EMAIL)!!
                )
            }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        return dsl.select(ORDERS.STATUS, DSL.count())
            .from(ORDERS)
            .groupBy(ORDERS.STATUS)
            .fetch()
            .associate { record ->
                OrderStatus.valueOf(record.getValue(ORDERS.STATUS)!!) to
                        record.get(1, Long::class.java)!!
            }
    }

}
