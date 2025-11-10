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
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal

class JooqOrderItemRepository(private val dsl: DSLContext) : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        dsl.insertInto(ORDER_ITEM)
            .set(ORDER_ITEM.ID, orderItem.id.value)
            .set(ORDER_ITEM.ORDER_ID, orderItem.orderId.value)
            .set(ORDER_ITEM.PRODUCT_ID, orderItem.productId.value)
            .set(ORDER_ITEM.QUANTITY, orderItem.quantity)
            .set(ORDER_ITEM.PRICE, orderItem.price.amount)
            .execute()
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return dsl.select(ORDER_ITEM.ID, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID, ORDER_ITEM.QUANTITY, ORDER_ITEM.PRICE)
            .from(ORDER_ITEM)
            .where(ORDER_ITEM.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                OrderItem(
                    id = OrderItemId(record.getValue(ORDER_ITEM.ID)!!),
                    orderId = OrderId(record.getValue(ORDER_ITEM.ORDER_ID)!!),
                    productId = ProductId(record.getValue(ORDER_ITEM.PRODUCT_ID)!!),
                    quantity = record.getValue(ORDER_ITEM.QUANTITY)!!,
                    price = Money(record.getValue(ORDER_ITEM.PRICE)!!)
                )
            }
    }

    override fun findAll(): List<OrderItem> {
        return dsl.select(ORDER_ITEM.ID, ORDER_ITEM.ORDER_ID, ORDER_ITEM.PRODUCT_ID, ORDER_ITEM.QUANTITY, ORDER_ITEM.PRICE)
            .from(ORDER_ITEM)
            .fetch()
            .map { record ->
                OrderItem(
                    id = OrderItemId(record.getValue(ORDER_ITEM.ID)!!),
                    orderId = OrderId(record.getValue(ORDER_ITEM.ORDER_ID)!!),
                    productId = ProductId(record.getValue(ORDER_ITEM.PRODUCT_ID)!!),
                    quantity = record.getValue(ORDER_ITEM.QUANTITY)!!,
                    price = Money(record.getValue(ORDER_ITEM.PRICE)!!)
                )
            }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        dsl.update(ORDER_ITEM)
            .set(ORDER_ITEM.ORDER_ID, orderItem.orderId.value)
            .set(ORDER_ITEM.PRODUCT_ID, orderItem.productId.value)
            .set(ORDER_ITEM.QUANTITY, orderItem.quantity)
            .set(ORDER_ITEM.PRICE, orderItem.price.amount)
            .where(ORDER_ITEM.ID.eq(orderItem.id.value))
            .execute()
        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val deleted = dsl.deleteFrom(ORDER_ITEM)
            .where(ORDER_ITEM.ID.eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        val oi = ORDER_ITEM.`as`("oi")
        val p = PRODUCT.`as`("p")

        return dsl.select(oi.ID, oi.ORDER_ID, oi.PRODUCT_ID, oi.QUANTITY, oi.PRICE, p.NAME, p.CATEGORY)
            .from(oi)
            .join(p).on(oi.PRODUCT_ID.eq(p.ID))
            .where(oi.ORDER_ID.eq(orderId.value))
            .fetch()
            .map { record ->
                OrderItemWithProduct(
                    orderItem = OrderItem(
                        id = OrderItemId(record.getValue(oi.ID)!!),
                        orderId = OrderId(record.getValue(oi.ORDER_ID)!!),
                        productId = ProductId(record.getValue(oi.PRODUCT_ID)!!),
                        quantity = record.getValue(oi.QUANTITY)!!,
                        price = Money(record.getValue(oi.PRICE)!!)
                    ),
                    productName = record.getValue(p.NAME)!!,
                    productCategory = record.getValue(p.CATEGORY)!!
                )
            }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        val totalQty = DSL.sum(ORDER_ITEM.QUANTITY).`as`("total_quantity")
        val totalRev = DSL.sum(ORDER_ITEM.QUANTITY.mul(ORDER_ITEM.PRICE)).`as`("total_revenue")
        val orderCnt = DSL.count().`as`("order_count")

        return dsl.select(ORDER_ITEM.PRODUCT_ID, totalQty, totalRev, orderCnt)
            .from(ORDER_ITEM)
            .where(ORDER_ITEM.PRODUCT_ID.eq(productId.value))
            .groupBy(ORDER_ITEM.PRODUCT_ID)
            .fetchOne()
            ?.let { record ->
                ProductSalesStatistics(
                    productId = ProductId(record.getValue(ORDER_ITEM.PRODUCT_ID)!!),
                    totalQuantity = record.get("total_quantity", Int::class.java) ?: 0,
                    totalRevenue = Money(record.get("total_revenue", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    orderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

}
