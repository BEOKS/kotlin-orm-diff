package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.exposed.table.OrderItems
import com.example.eshop.exposed.table.Products
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times

class ExposedOrderItemRepository : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        OrderItems.insert {
            it[id] = orderItem.id.value
            it[orderId] = orderItem.orderId.value
            it[productId] = orderItem.productId.value
            it[quantity] = orderItem.quantity
            it[price] = orderItem.price.amount
        }
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return OrderItems.selectAll()
            .where { OrderItems.id eq id.value }
            .map { it.toOrderItem() }
            .singleOrNull()
    }

    override fun findAll(): List<OrderItem> {
        return OrderItems.selectAll()
            .map { it.toOrderItem() }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        OrderItems.update({ OrderItems.id eq orderItem.id.value }) {
            it[orderId] = orderItem.orderId.value
            it[productId] = orderItem.productId.value
            it[quantity] = orderItem.quantity
            it[price] = orderItem.price.amount
        }
        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val deleted = OrderItems.deleteWhere { OrderItems.id eq id.value }
        return deleted > 0
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        return OrderItems
            .join(Products, JoinType.INNER, OrderItems.productId, Products.id)
            .selectAll()
            .where { OrderItems.orderId eq orderId.value }
            .map { row ->
                OrderItemWithProduct(
                    orderItem = OrderItem(
                        id = OrderItemId(row[OrderItems.id]),
                        orderId = OrderId(row[OrderItems.orderId]),
                        productId = ProductId(row[OrderItems.productId]),
                        quantity = row[OrderItems.quantity],
                        price = Money(row[OrderItems.price])
                    ),
                    productName = row[Products.name],
                    productCategory = row[Products.category]
                )
            }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        val totalQuantityAlias = OrderItems.quantity.sum()
        val totalRevenueAlias = (OrderItems.quantity.castTo<Long>(LongColumnType()) * OrderItems.price).sum()
        val orderCountAlias = OrderItems.id.count()
        
        return OrderItems
            .select(
                OrderItems.productId,
                totalQuantityAlias,
                totalRevenueAlias,
                orderCountAlias
            )
            .where { OrderItems.productId eq productId.value }
            .groupBy(OrderItems.productId)
            .map { row ->
                ProductSalesStatistics(
                    productId = ProductId(row[OrderItems.productId]),
                    totalQuantity = row[totalQuantityAlias]?.toInt() ?: 0,
                    totalRevenue = Money(row[totalRevenueAlias] ?: java.math.BigDecimal.ZERO),
                    orderCount = row[orderCountAlias]
                )
            }
            .singleOrNull()
    }

    private fun ResultRow.toOrderItem() = OrderItem(
        id = OrderItemId(this[OrderItems.id]),
        orderId = OrderId(this[OrderItems.orderId]),
        productId = ProductId(this[OrderItems.productId]),
        quantity = this[OrderItems.quantity],
        price = Money(this[OrderItems.price])
    )
}

