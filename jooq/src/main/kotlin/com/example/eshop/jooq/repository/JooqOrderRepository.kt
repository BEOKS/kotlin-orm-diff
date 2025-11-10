package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.generated.tables.references.ORDERS
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import com.example.eshop.jooq.generated.tables.references.PAYMENT
import com.example.eshop.jooq.generated.tables.references.ORDER_ITEM
import com.example.eshop.jooq.generated.tables.references.PRODUCT
import mu.KotlinLogging
import org.jooq.*
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

    override fun searchOrders(criteria: OrderSearchCriteria): List<OrderSearchResult> {
        logger.debug { "Searching orders with criteria: $criteria" }

        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")
        val oi = ORDER_ITEM.`as`("oi")
        val pr = PRODUCT.`as`("pr")

        // 동적으로 WHERE 조건 생성
        val conditions = mutableListOf<Condition>()

        // Order 테이블 조건
        criteria.orderDateFrom?.let {
            conditions.add(o.ORDER_DATE.ge(it))
        }
        criteria.orderDateTo?.let {
            conditions.add(o.ORDER_DATE.le(it))
        }
        criteria.minTotalAmount?.let {
            conditions.add(o.TOTAL_AMOUNT.ge(it.amount))
        }
        criteria.maxTotalAmount?.let {
            conditions.add(o.TOTAL_AMOUNT.le(it.amount))
        }
        criteria.orderStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            conditions.add(o.STATUS.`in`(statuses.map { it.name }))
        }

        // Customer 테이블 조건
        criteria.customerName?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.NAME.containsIgnoreCase(it))
        }
        criteria.customerEmail?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.EMAIL.containsIgnoreCase(it))
        }
        criteria.customerAddress?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.ADDRESS.containsIgnoreCase(it))
        }
        criteria.customerRegisteredDateFrom?.let {
            conditions.add(c.REGISTERED_DATE.ge(it))
        }
        criteria.customerRegisteredDateTo?.let {
            conditions.add(c.REGISTERED_DATE.le(it))
        }

        // Product 테이블 조건
        criteria.productName?.takeIf { it.isNotBlank() }?.let {
            conditions.add(pr.NAME.containsIgnoreCase(it))
        }
        criteria.productCategory?.takeIf { it.isNotBlank() }?.let {
            conditions.add(pr.CATEGORY.eq(it))
        }
        criteria.minProductPrice?.let {
            conditions.add(pr.PRICE.ge(it.amount))
        }
        criteria.maxProductPrice?.let {
            conditions.add(pr.PRICE.le(it.amount))
        }

        // Payment 테이블 조건
        criteria.paymentMethods?.takeIf { it.isNotEmpty() }?.let { methods ->
            conditions.add(p.METHOD.`in`(methods.map { it.name }))
        }
        criteria.paymentStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            conditions.add(p.STATUS.`in`(statuses.map { it.name }))
        }
        criteria.paymentDateFrom?.let {
            conditions.add(p.PAYMENT_DATE.ge(it))
        }
        criteria.paymentDateTo?.let {
            conditions.add(p.PAYMENT_DATE.le(it))
        }

        // 정렬 필드 결정
        val sortField = when (criteria.sortBy) {
            OrderSortField.ORDER_DATE -> o.ORDER_DATE
            OrderSortField.TOTAL_AMOUNT -> o.TOTAL_AMOUNT
            OrderSortField.CUSTOMER_NAME -> c.NAME
            OrderSortField.ORDER_STATUS -> o.STATUS
        }

        // 정렬 방향 적용
        val orderBy = if (criteria.sortDirection == SortDirection.ASC) {
            sortField.asc()
        } else {
            sortField.desc()
        }

        // 메인 쿼리 구성
        val itemCountField = DSL.count(oi.ID).`as`("item_count")
        val productNamesField = DSL.groupConcat(pr.NAME).separator(", ").`as`("product_names")

        return dsl.select(
            o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS,
            c.NAME, c.EMAIL,
            itemCountField,
            productNamesField,
            p.STATUS
        )
            .from(o)
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .leftJoin(oi).on(o.ID.eq(oi.ORDER_ID))
            .leftJoin(pr).on(oi.PRODUCT_ID.eq(pr.ID))
            .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
            .where(conditions)
            .groupBy(o.ID, o.CUSTOMER_ID, o.ORDER_DATE, o.TOTAL_AMOUNT, o.STATUS,
                c.NAME, c.EMAIL, p.STATUS)
            .orderBy(orderBy)
            .limit(criteria.limit)
            .offset(criteria.offset)
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

                    val productNamesStr = record.get("product_names", String::class.java)
                    val productNames = productNamesStr?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()

                    OrderSearchResult(
                        order = order,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null,
                        customerEmail = record.getValue(c.EMAIL) ?: return@mapNotNull null,
                        itemCount = record.get("item_count", Long::class.java)?.toInt() ?: 0,
                        productNames = productNames,
                        paymentStatus = record.getValue(p.STATUS)
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map order search result" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} orders matching criteria" } }
    }

    override fun countOrders(criteria: OrderSearchCriteria): Long {
        logger.debug { "Counting orders with criteria: $criteria" }

        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")
        val p = PAYMENT.`as`("p")
        val oi = ORDER_ITEM.`as`("oi")
        val pr = PRODUCT.`as`("pr")

        // 동적으로 WHERE 조건 생성 (searchOrders와 동일한 로직)
        val conditions = mutableListOf<Condition>()

        // Order 테이블 조건
        criteria.orderDateFrom?.let {
            conditions.add(o.ORDER_DATE.ge(it))
        }
        criteria.orderDateTo?.let {
            conditions.add(o.ORDER_DATE.le(it))
        }
        criteria.minTotalAmount?.let {
            conditions.add(o.TOTAL_AMOUNT.ge(it.amount))
        }
        criteria.maxTotalAmount?.let {
            conditions.add(o.TOTAL_AMOUNT.le(it.amount))
        }
        criteria.orderStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            conditions.add(o.STATUS.`in`(statuses.map { it.name }))
        }

        // Customer 테이블 조건
        criteria.customerName?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.NAME.containsIgnoreCase(it))
        }
        criteria.customerEmail?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.EMAIL.containsIgnoreCase(it))
        }
        criteria.customerAddress?.takeIf { it.isNotBlank() }?.let {
            conditions.add(c.ADDRESS.containsIgnoreCase(it))
        }
        criteria.customerRegisteredDateFrom?.let {
            conditions.add(c.REGISTERED_DATE.ge(it))
        }
        criteria.customerRegisteredDateTo?.let {
            conditions.add(c.REGISTERED_DATE.le(it))
        }

        // Product 테이블 조건
        criteria.productName?.takeIf { it.isNotBlank() }?.let {
            conditions.add(pr.NAME.containsIgnoreCase(it))
        }
        criteria.productCategory?.takeIf { it.isNotBlank() }?.let {
            conditions.add(pr.CATEGORY.eq(it))
        }
        criteria.minProductPrice?.let {
            conditions.add(pr.PRICE.ge(it.amount))
        }
        criteria.maxProductPrice?.let {
            conditions.add(pr.PRICE.le(it.amount))
        }

        // Payment 테이블 조건
        criteria.paymentMethods?.takeIf { it.isNotEmpty() }?.let { methods ->
            conditions.add(p.METHOD.`in`(methods.map { it.name }))
        }
        criteria.paymentStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            conditions.add(p.STATUS.`in`(statuses.map { it.name }))
        }
        criteria.paymentDateFrom?.let {
            conditions.add(p.PAYMENT_DATE.ge(it))
        }
        criteria.paymentDateTo?.let {
            conditions.add(p.PAYMENT_DATE.le(it))
        }

        // COUNT 쿼리 (DISTINCT 사용하여 중복 제거)
        return dsl.selectCount()
            .from(
                dsl.selectDistinct(o.ID)
                    .from(o)
                    .join(c).on(o.CUSTOMER_ID.eq(c.ID))
                    .leftJoin(oi).on(o.ID.eq(oi.ORDER_ID))
                    .leftJoin(pr).on(oi.PRODUCT_ID.eq(pr.ID))
                    .leftJoin(p).on(o.ID.eq(p.ORDER_ID))
                    .where(conditions)
                    .asTable("distinct_orders")
            )
            .fetchOne(0, Long::class.java) ?: 0L
            .also { logger.debug { "Counted $it orders matching criteria" } }
    }
}
