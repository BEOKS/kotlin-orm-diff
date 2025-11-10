package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.schema.OrderItemFields
import com.example.eshop.jooq.schema.ProductFields
import com.example.eshop.jooq.schema.Tables
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal

class JooqOrderItemRepository(private val dsl: DSLContext) : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        dsl.insertInto(DSL.table(Tables.ORDER_ITEM))
            .set(DSL.field(OrderItemFields.ID), orderItem.id.value)
            .set(DSL.field(OrderItemFields.ORDER_ID), orderItem.orderId.value)
            .set(DSL.field(OrderItemFields.PRODUCT_ID), orderItem.productId.value)
            .set(DSL.field(OrderItemFields.QUANTITY), orderItem.quantity)
            .set(DSL.field(OrderItemFields.PRICE), orderItem.price.amount)
            .execute()
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return dsl.select()
            .from(Tables.ORDER_ITEM)
            .where(DSL.field(OrderItemFields.ID).eq(id.value))
            .fetchOne()
            ?.let { mapToOrderItem(it) }
    }

    override fun findAll(): List<OrderItem> {
        return dsl.select()
            .from(Tables.ORDER_ITEM)
            .fetch()
            .map { mapToOrderItem(it) }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        dsl.update(DSL.table(Tables.ORDER_ITEM))
            .set(DSL.field(OrderItemFields.ORDER_ID), orderItem.orderId.value)
            .set(DSL.field(OrderItemFields.PRODUCT_ID), orderItem.productId.value)
            .set(DSL.field(OrderItemFields.QUANTITY), orderItem.quantity)
            .set(DSL.field(OrderItemFields.PRICE), orderItem.price.amount)
            .where(DSL.field(OrderItemFields.ID).eq(orderItem.id.value))
            .execute()
        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val deleted = dsl.deleteFrom(DSL.table(Tables.ORDER_ITEM))
            .where(DSL.field(OrderItemFields.ID).eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        val oi = DSL.table(Tables.ORDER_ITEM).asTable("oi")
        val p = DSL.table(Tables.PRODUCT).asTable("p")

        return dsl.select(
            oi.field(OrderItemFields.ID),
            oi.field(OrderItemFields.ORDER_ID),
            oi.field(OrderItemFields.PRODUCT_ID),
            oi.field(OrderItemFields.QUANTITY),
            oi.field(OrderItemFields.PRICE),
            p.field(ProductFields.NAME),
            p.field(ProductFields.CATEGORY)
        )
            .from(oi)
            .join(p).on(oi.field(OrderItemFields.PRODUCT_ID).eq(p.field(ProductFields.ID)))
            .where(oi.field(OrderItemFields.ORDER_ID).eq(orderId.value))
            .fetch()
            .map { record ->
                OrderItemWithProduct(
                    orderItem = OrderItem(
                        id = OrderItemId(record.get(oi.field(OrderItemFields.ID), Long::class.java)!!),
                        orderId = OrderId(record.get(oi.field(OrderItemFields.ORDER_ID), Long::class.java)!!),
                        productId = ProductId(record.get(oi.field(OrderItemFields.PRODUCT_ID), Long::class.java)!!),
                        quantity = record.get(oi.field(OrderItemFields.QUANTITY), Int::class.java)!!,
                        price = Money(record.get(oi.field(OrderItemFields.PRICE), BigDecimal::class.java)!!)
                    ),
                    productName = record.get(p.field(ProductFields.NAME), String::class.java)!!,
                    productCategory = record.get(p.field(ProductFields.CATEGORY), String::class.java)!!
                )
            }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        return dsl.select(
            DSL.field(OrderItemFields.PRODUCT_ID),
            DSL.sum(DSL.field(OrderItemFields.QUANTITY, Int::class.java)).`as`("total_quantity"),
            DSL.sum(
                DSL.field(OrderItemFields.QUANTITY, Int::class.java)
                    .mul(DSL.field(OrderItemFields.PRICE, BigDecimal::class.java))
            ).`as`("total_revenue"),
            DSL.count().`as`("order_count")
        )
            .from(Tables.ORDER_ITEM)
            .where(DSL.field(OrderItemFields.PRODUCT_ID).eq(productId.value))
            .groupBy(DSL.field(OrderItemFields.PRODUCT_ID))
            .fetchOne()
            ?.let { record ->
                ProductSalesStatistics(
                    productId = ProductId(record.get(OrderItemFields.PRODUCT_ID, Long::class.java)!!),
                    totalQuantity = record.get("total_quantity", Int::class.java) ?: 0,
                    totalRevenue = Money(record.get("total_revenue", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    orderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

    private fun mapToOrderItem(record: org.jooq.Record): OrderItem {
        return OrderItem(
            id = OrderItemId(record.get(OrderItemFields.ID, Long::class.java)!!),
            orderId = OrderId(record.get(OrderItemFields.ORDER_ID, Long::class.java)!!),
            productId = ProductId(record.get(OrderItemFields.PRODUCT_ID, Long::class.java)!!),
            quantity = record.get(OrderItemFields.QUANTITY, Int::class.java)!!,
            price = Money(record.get(OrderItemFields.PRICE, BigDecimal::class.java)!!)
        )
    }
}

