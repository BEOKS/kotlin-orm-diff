package com.example.eshop.ktorm.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.ktorm.table.OrderItems
import com.example.eshop.ktorm.table.Products
import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.math.BigDecimal

class KtormOrderItemRepository(private val database: Database) : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        database.insert(OrderItems) {
            set(it.id, orderItem.id.value)
            set(it.orderId, orderItem.orderId.value)
            set(it.productId, orderItem.productId.value)
            set(it.quantity, orderItem.quantity)
            set(it.price, orderItem.price.amount)
        }
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return database.from(OrderItems)
            .select()
            .where { OrderItems.id eq id.value }
            .map { row -> row.toOrderItem() }
            .singleOrNull()
    }

    override fun findAll(): List<OrderItem> {
        return database.from(OrderItems)
            .select()
            .map { row -> row.toOrderItem() }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        val updatedRows = database.update(OrderItems) {
            set(it.orderId, orderItem.orderId.value)
            set(it.productId, orderItem.productId.value)
            set(it.quantity, orderItem.quantity)
            set(it.price, orderItem.price.amount)
            where { it.id eq orderItem.id.value }
        }
        if (updatedRows == 0) {
            throw IllegalStateException("OrderItem with id ${orderItem.id.value} not found")
        }
        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val deleted = database.delete(OrderItems) { it.id eq id.value }
        return deleted > 0
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        return database.from(OrderItems)
            .innerJoin(Products, on = OrderItems.productId eq Products.id)
            .select()
            .where { OrderItems.orderId eq orderId.value }
            .map { row ->
                OrderItemWithProduct(
                    orderItem = OrderItem(
                        id = OrderItemId(row[OrderItems.id]!!),
                        orderId = OrderId(row[OrderItems.orderId]!!),
                        productId = ProductId(row[OrderItems.productId]!!),
                        quantity = row[OrderItems.quantity]!!,
                        price = Money(row[OrderItems.price]!!)
                    ),
                    productName = row[Products.name]!!,
                    productCategory = row[Products.category]!!
                )
            }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        // Use native SQL for price * quantity calculation
        val sql = """
            SELECT
                SUM(quantity) as total_quantity,
                SUM(price * quantity) as total_revenue,
                COUNT(*) as order_count
            FROM order_item
            WHERE product_id = ?
        """.trimIndent()

        return database.useConnection { conn ->
            conn.prepareStatement(sql).use { statement ->
                statement.setLong(1, productId.value)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        val totalQty = rs.getInt("total_quantity")
                        if (totalQty > 0) {
                            ProductSalesStatistics(
                                productId = productId,
                                totalQuantity = totalQty,
                                totalRevenue = Money(rs.getBigDecimal("total_revenue") ?: BigDecimal.ZERO),
                                orderCount = rs.getLong("order_count")
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun QueryRowSet.toOrderItem() = OrderItem(
        id = OrderItemId(this[OrderItems.id]!!),
        orderId = OrderId(this[OrderItems.orderId]!!),
        productId = ProductId(this[OrderItems.productId]!!),
        quantity = this[OrderItems.quantity]!!,
        price = Money(this[OrderItems.price]!!)
    )
}
