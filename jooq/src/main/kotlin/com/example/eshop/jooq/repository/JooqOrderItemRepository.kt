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
        val oiId = DSL.field(OrderItemFields.ID, Long::class.java)
        val oiOrderId = DSL.field(OrderItemFields.ORDER_ID, Long::class.java)
        val oiProductId = DSL.field(OrderItemFields.PRODUCT_ID, Long::class.java)
        val oiQuantity = DSL.field(OrderItemFields.QUANTITY, Int::class.java)
        val oiPrice = DSL.field(OrderItemFields.PRICE, BigDecimal::class.java)
        
        return dsl.select(oiId, oiOrderId, oiProductId, oiQuantity, oiPrice)
            .from(Tables.ORDER_ITEM)
            .where(oiId.eq(id.value))
            .fetchOne()
            ?.let { record ->
                OrderItem(
                    id = OrderItemId(record.getValue(oiId)),
                    orderId = OrderId(record.getValue(oiOrderId)),
                    productId = ProductId(record.getValue(oiProductId)),
                    quantity = record.getValue(oiQuantity),
                    price = Money(record.getValue(oiPrice))
                )
            }
    }

    override fun findAll(): List<OrderItem> {
        val oiId = DSL.field(OrderItemFields.ID, Long::class.java)
        val oiOrderId = DSL.field(OrderItemFields.ORDER_ID, Long::class.java)
        val oiProductId = DSL.field(OrderItemFields.PRODUCT_ID, Long::class.java)
        val oiQuantity = DSL.field(OrderItemFields.QUANTITY, Int::class.java)
        val oiPrice = DSL.field(OrderItemFields.PRICE, BigDecimal::class.java)
        
        return dsl.select(oiId, oiOrderId, oiProductId, oiQuantity, oiPrice)
            .from(Tables.ORDER_ITEM)
            .fetch()
            .map { record ->
                OrderItem(
                    id = OrderItemId(record.getValue(oiId)),
                    orderId = OrderId(record.getValue(oiOrderId)),
                    productId = ProductId(record.getValue(oiProductId)),
                    quantity = record.getValue(oiQuantity),
                    price = Money(record.getValue(oiPrice))
                )
            }
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
        val oiId = DSL.field("oi.${OrderItemFields.ID}", Long::class.java)
        val oiOrderId = DSL.field("oi.${OrderItemFields.ORDER_ID}", Long::class.java)
        val oiProdId = DSL.field("oi.${OrderItemFields.PRODUCT_ID}", Long::class.java)
        val oiQty = DSL.field("oi.${OrderItemFields.QUANTITY}", Int::class.java)
        val oiPrice = DSL.field("oi.${OrderItemFields.PRICE}", BigDecimal::class.java)
        val pName = DSL.field("p.${ProductFields.NAME}", String::class.java)
        val pCategory = DSL.field("p.${ProductFields.CATEGORY}", String::class.java)

        return dsl.select(oiId, oiOrderId, oiProdId, oiQty, oiPrice, pName, pCategory)
            .from("${Tables.ORDER_ITEM} oi")
            .join("${Tables.PRODUCT} p")
            .on("oi.${OrderItemFields.PRODUCT_ID} = p.${ProductFields.ID}")
            .where(oiOrderId.eq(orderId.value))
            .fetch()
            .map { record ->
                OrderItemWithProduct(
                    orderItem = OrderItem(
                        id = OrderItemId(record.getValue(oiId)),
                        orderId = OrderId(record.getValue(oiOrderId)),
                        productId = ProductId(record.getValue(oiProdId)),
                        quantity = record.getValue(oiQty),
                        price = Money(record.getValue(oiPrice))
                    ),
                    productName = record.getValue(pName),
                    productCategory = record.getValue(pCategory)
                )
            }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        val prodId = DSL.field(OrderItemFields.PRODUCT_ID, Long::class.java)
        val qty = DSL.field(OrderItemFields.QUANTITY, Int::class.java)
        val price = DSL.field(OrderItemFields.PRICE, BigDecimal::class.java)
        val totalQty = DSL.sum(qty).`as`("total_quantity")
        val totalRev = DSL.sum(qty.mul(price)).`as`("total_revenue")
        val orderCnt = DSL.count().`as`("order_count")

        return dsl.select(prodId, totalQty, totalRev, orderCnt)
            .from(Tables.ORDER_ITEM)
            .where(prodId.eq(productId.value))
            .groupBy(prodId)
            .fetchOne()
            ?.let { record ->
                ProductSalesStatistics(
                    productId = ProductId(record.getValue(prodId)),
                    totalQuantity = record.get("total_quantity", Int::class.java) ?: 0,
                    totalRevenue = Money(record.get("total_revenue", BigDecimal::class.java) ?: BigDecimal.ZERO),
                    orderCount = record.get("order_count", Long::class.java) ?: 0L
                )
            }
    }

}
