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
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

private val logger = KotlinLogging.logger {}

class JooqOrderRepository(private val dsl: DSLContext) : OrderRepository {

    // Private mapper function
    private fun mapToOrder(record: Record): Order? {
        return try {
            Order(
                id = OrderId(record.getValue(ORDERS.ID) ?: return null),
                customerId = CustomerId(record.getValue(ORDERS.CUSTOMER_ID) ?: return null),
                orderDate = record.getValue(ORDERS.ORDER_DATE) ?: return null,
                totalAmount = Money(record.getValue(ORDERS.TOTAL_AMOUNT) ?: return null),
                status = record.getValue(ORDERS.STATUS)?.let {
                    OrderStatus.valueOf(it)
                } ?: return null
            )
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Failed to map record to Order: Invalid enum value" }
            null
        } catch (e: Exception) {
            logger.error(e) { "Failed to map record to Order" }
            null
        }
    }

    override fun save(order: Order): Order {
        logger.debug { "Saving order: ${order.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                // Check for duplicate
                val exists = ctx.fetchExists(
                    ctx.selectFrom(ORDERS).where(ORDERS.ID.eq(order.id.value))
                )

                if (exists) {
                    logger.warn { "Order with id ${order.id.value} already exists" }
                    throw IllegalStateException("Order with id ${order.id.value} already exists")
                }

                ctx.insertInto(ORDERS)
                    .set(ORDERS.ID, order.id.value)
                    .set(ORDERS.CUSTOMER_ID, order.customerId.value)
                    .set(ORDERS.ORDER_DATE, order.orderDate)
                    .set(ORDERS.TOTAL_AMOUNT, order.totalAmount.amount)
                    .set(ORDERS.STATUS, order.status.name)
                    .execute()

                logger.info { "Successfully saved order: ${order.id.value}" }
                order
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save order: ${order.id.value}" }
            throw e
        }
    }

    override fun findById(id: OrderId): Order? {
        logger.debug { "Finding order by id: ${id.value}" }

        return dsl.select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.ORDER_DATE,
            ORDERS.TOTAL_AMOUNT, ORDERS.STATUS)
            .from(ORDERS)
            .where(ORDERS.ID.eq(id.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found order: ${id.value}" }
                mapToOrder(it)
            }
            ?: run {
                logger.debug { "Order not found: ${id.value}" }
                null
            }
    }

    override fun findAll(): List<Order> {
        logger.debug { "Finding all orders" }

        return dsl.select(ORDERS.ID, ORDERS.CUSTOMER_ID, ORDERS.ORDER_DATE,
            ORDERS.TOTAL_AMOUNT, ORDERS.STATUS)
            .from(ORDERS)
            .fetch()
            .mapNotNull { mapToOrder(it) }
            .also { logger.debug { "Found ${it.size} orders" } }
    }

    override fun update(order: Order): Order {
        logger.debug { "Updating order: ${order.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val rowsAffected = ctx.update(ORDERS)
                    .set(ORDERS.CUSTOMER_ID, order.customerId.value)
                    .set(ORDERS.ORDER_DATE, order.orderDate)
                    .set(ORDERS.TOTAL_AMOUNT, order.totalAmount.amount)
                    .set(ORDERS.STATUS, order.status.name)
                    .where(ORDERS.ID.eq(order.id.value))
                    .execute()

                if (rowsAffected == 0) {
                    logger.warn { "Order with id ${order.id.value} not found for update" }
                    throw IllegalStateException("Order with id ${order.id.value} not found")
                }

                logger.info { "Successfully updated order: ${order.id.value}" }
                order
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update order: ${order.id.value}" }
            throw e
        }
    }

    override fun delete(id: OrderId): Boolean {
        logger.debug { "Deleting order: ${id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val deleted = ctx.deleteFrom(ORDERS)
                    .where(ORDERS.ID.eq(id.value))
                    .execute()

                val result = deleted > 0
                if (result) {
                    logger.info { "Successfully deleted order: ${id.value}" }
                } else {
                    logger.warn { "Order not found for deletion: ${id.value}" }
                }
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete order: ${id.value}" }
            throw e
        }
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        logger.debug { "Finding orders with payment and items for customer: ${customerId.value}" }

        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")
        val oi = ORDER_ITEM.`as`("oi")
        val itemCount = DSL.count(oi.ID).`as`("item_count")

        return dsl.select(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS,
            c.NAME, p.STATUS, itemCount)
            .from(o)
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
            .leftJoin(oi).on(o.ID.eq(oi.ORDER_ID))
            .where(o.CUSTOMER_ID.eq(customerId.value))
            .groupBy(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS, c.NAME, p.STATUS)
            .fetch()
            .mapNotNull { record ->
                try {
                    val order = Order(
                        id = OrderId(record.getValue(o.ID) ?: return@mapNotNull null),
                        customerId = CustomerId(record.getValue(o.CUSTOMER_ID) ?: return@mapNotNull null),
                        orderDate = record.getValue(o.ORDER_DATE) ?: return@mapNotNull null,
                        totalAmount = Money(record.getValue(o.TOTAL_AMOUNT) ?: return@mapNotNull null),
                        status = record.getValue(o.STATUS)?.let { OrderStatus.valueOf(it) }
                            ?: return@mapNotNull null
                    )

                    OrderWithDetails(
                        order = order,
                        paymentStatus = record.get(p.STATUS),
                        itemCount = record.get("item_count", Long::class.java)?.toInt() ?: 0,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map order with details" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} orders with details" } }
    }

    /**
     * 미지급 주문을 조회합니다.
     *
     * 다음 조건 중 하나를 만족하는 주문을 반환합니다:
     * 1. 결제 레코드가 없는 주문 (payment가 null)
     * 2. 결제 상태가 PENDING인 주문
     */
    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        logger.debug { "Finding unpaid orders with details" }

        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")

        return dsl.select(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS,
            c.NAME, c.EMAIL)
            .from(o)
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
            .where(
                p.ID.isNull  // 결제 없음
                    .or(p.STATUS.eq(PaymentStatus.PENDING.name))  // 또는 대기 중
            )
            .fetch()
            .mapNotNull { record ->
                try {
                    val order = Order(
                        id = OrderId(record.getValue(o.ID) ?: return@mapNotNull null),
                        customerId = CustomerId(record.getValue(o.CUSTOMER_ID) ?: return@mapNotNull null),
                        orderDate = record.getValue(o.ORDER_DATE) ?: return@mapNotNull null,
                        totalAmount = Money(record.getValue(o.TOTAL_AMOUNT) ?: return@mapNotNull null),
                        status = record.getValue(o.STATUS)?.let { OrderStatus.valueOf(it) }
                            ?: return@mapNotNull null
                    )

                    OrderWithCustomer(
                        order = order,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null,
                        customerEmail = record.getValue(c.EMAIL) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map unpaid order" }
                    null
                }
            }
            .also { logger.info { "Found ${it.size} unpaid orders" } }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        logger.debug { "Counting orders by status" }

        val statusField = ORDERS.STATUS
        val countField = DSL.count().`as`("order_count")

        return dsl.select(statusField, countField)
            .from(ORDERS)
            .groupBy(statusField)
            .fetch()
            .mapNotNull { record ->
                try {
                    val statusStr = record.getValue(statusField) ?: return@mapNotNull null
                    val count = record.get("order_count", Long::class.java) ?: 0L
                    OrderStatus.valueOf(statusStr) to count
                } catch (e: IllegalArgumentException) {
                    logger.error(e) { "Invalid order status in count" }
                    null
                }
            }
            .toMap()
            .also { logger.debug { "Counted orders for ${it.size} statuses" } }
    }
}
