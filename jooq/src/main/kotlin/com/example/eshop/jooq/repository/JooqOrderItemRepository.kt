package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.generated.tables.references.ORDER_ITEM
import com.example.eshop.jooq.generated.tables.references.PRODUCT
import com.example.eshop.jooq.util.MoneyConstants
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class JooqOrderItemRepository(private val dsl: DSLContext) : OrderItemRepository {

    // Private mapper function
    private fun mapToOrderItem(record: Record): OrderItem? {
        return try {
            OrderItem(
                id = OrderItemId(record.getValue(ORDER_ITEM.ID) ?: return null),
                orderId = OrderId(record.getValue(ORDER_ITEM.ORDER_ID) ?: return null),
                productId = ProductId(record.getValue(ORDER_ITEM.PRODUCT_ID) ?: return null),
                quantity = record.getValue(ORDER_ITEM.QUANTITY) ?: return null,
                price = Money(record.getValue(ORDER_ITEM.PRICE) ?: return null)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to map record to OrderItem" }
            null
        }
    }

    override fun save(orderItem: OrderItem): OrderItem {
        logger.debug { "Saving order item: ${orderItem.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                // Check for duplicate
                val exists = ctx.fetchExists(
                    ctx.selectFrom(ORDER_ITEM).where(ORDER_ITEM.ID.eq(orderItem.id.value))
                )

                if (exists) {
                    logger.warn { "OrderItem with id ${orderItem.id.value} already exists" }
                    throw IllegalStateException("OrderItem with id ${orderItem.id.value} already exists")
                }

                ctx.insertInto(ORDER_ITEM)
                    .set(ORDER_ITEM.ID, orderItem.id.value)
                    .set(ORDER_ITEM.ORDER_ID, orderItem.orderId.value)
                    .set(ORDER_ITEM.PRODUCT_ID, orderItem.productId.value)
                    .set(ORDER_ITEM.QUANTITY, orderItem.quantity)
                    .set(ORDER_ITEM.PRICE, orderItem.price.amount)
                    .execute()

                logger.info { "Successfully saved order item: ${orderItem.id.value}" }
                orderItem
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save order item: ${orderItem.id.value}" }
            throw e
        }
    }

    override fun findById(id: OrderItemId): OrderItem? {
        logger.debug { "Finding order item by id: ${id.value}" }

        return dsl.select(ORDER_ITEM.ID, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID,
            ORDER_ITEM.QUANTITY, ORDER_ITEM.PRICE)
            .from(ORDER_ITEM)
            .where(ORDER_ITEM.ID.eq(id.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found order item: ${id.value}" }
                mapToOrderItem(it)
            }
            ?: run {
                logger.debug { "Order item not found: ${id.value}" }
                null
            }
    }

    override fun findAll(): List<OrderItem> {
        logger.debug { "Finding all order items" }

        return dsl.select(ORDER_ITEM.ID, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID,
            ORDER_ITEM.QUANTITY, ORDER_ITEM.PRICE)
            .from(ORDER_ITEM)
            .fetch()
            .mapNotNull { mapToOrderItem(it) }
            .also { logger.debug { "Found ${it.size} order items" } }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        logger.debug { "Updating order item: ${orderItem.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val rowsAffected = ctx.update(ORDER_ITEM)
                    .set(ORDER_ITEM.ORDER_ID, orderItem.orderId.value)
                    .set(ORDER_ITEM.PRODUCT_ID, orderItem.productId.value)
                    .set(ORDER_ITEM.QUANTITY, orderItem.quantity)
                    .set(ORDER_ITEM.PRICE, orderItem.price.amount)
                    .where(ORDER_ITEM.ID.eq(orderItem.id.value))
                    .execute()

                if (rowsAffected == 0) {
                    logger.warn { "OrderItem with id ${orderItem.id.value} not found for update" }
                    throw IllegalStateException("OrderItem with id ${orderItem.id.value} not found")
                }

                logger.info { "Successfully updated order item: ${orderItem.id.value}" }
                orderItem
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update order item: ${orderItem.id.value}" }
            throw e
        }
    }

    override fun delete(id: OrderItemId): Boolean {
        logger.debug { "Deleting order item: ${id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val deleted = ctx.deleteFrom(ORDER_ITEM)
                    .where(ORDER_ITEM.ID.eq(id.value))
                    .execute()

                val result = deleted > 0
                if (result) {
                    logger.info { "Successfully deleted order item: ${id.value}" }
                } else {
                    logger.warn { "Order item not found for deletion: ${id.value}" }
                }
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete order item: ${id.value}" }
            throw e
        }
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        logger.debug { "Finding order items with product for order: ${orderId.value}" }

        val oi = ORDER_ITEM.`as`("oi")
        val p = PRODUCT.`as`("p")

        return dsl.select(oi.ID, oi.ORDER_ID, oi.PRODUCT_ID, oi.QUANTITY, oi.PRICE,
            p.NAME, p.CATEGORY)
            .from(oi)
            .join(p).on(oi.PRODUCT_ID.eq(p.ID))
            .where(oi.ORDER_ID.eq(orderId.value))
            .fetch()
            .mapNotNull { record ->
                try {
                    val orderItem = OrderItem(
                        id = OrderItemId(record.getValue(oi.ID) ?: return@mapNotNull null),
                        orderId = OrderId(record.getValue(oi.ORDER_ID) ?: return@mapNotNull null),
                        productId = ProductId(record.getValue(oi.PRODUCT_ID) ?: return@mapNotNull null),
                        quantity = record.getValue(oi.QUANTITY) ?: return@mapNotNull null,
                        price = Money(record.getValue(oi.PRICE) ?: return@mapNotNull null)
                    )

                    OrderItemWithProduct(
                        orderItem = orderItem,
                        productName = record.getValue(p.NAME) ?: return@mapNotNull null,
                        productCategory = record.getValue(p.CATEGORY) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map order item with product" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} order items with product" } }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        logger.debug { "Calculating product sales statistics for product: ${productId.value}" }

        val totalQty = DSL.sum(ORDER_ITEM.QUANTITY).`as`("total_quantity")
        val totalRev = DSL.sum(ORDER_ITEM.QUANTITY.mul(ORDER_ITEM.PRICE)).`as`("total_revenue")
        val orderCnt = DSL.count().`as`("order_count")

        return dsl.select(ORDER_ITEM.PRODUCT_ID, totalQty, totalRev, orderCnt)
            .from(ORDER_ITEM)
            .where(ORDER_ITEM.PRODUCT_ID.eq(productId.value))
            .groupBy(ORDER_ITEM.PRODUCT_ID)
            .fetchOne()
            ?.let { record ->
                try {
                    ProductSalesStatistics(
                        productId = ProductId(record.getValue(ORDER_ITEM.PRODUCT_ID) ?: return null),
                        totalQuantity = record.get("total_quantity", Long::class.java)?.toInt() ?: 0,
                        totalRevenue = Money(
                            (record.get("total_revenue", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        orderCount = record.get("order_count", Long::class.java) ?: 0L
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map product sales statistics" }
                    null
                }
            }
            ?: run {
                logger.debug { "No sales statistics found for product: ${productId.value}" }
                null
            }
    }
}
