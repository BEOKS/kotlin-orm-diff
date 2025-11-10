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
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

class ExposedOrderItemRepository : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem = transaction {
        OrderItems.insert {
            it[id] = orderItem.id.value
            it[orderId] = orderItem.orderId.value
            it[productId] = orderItem.productId.value
            it[quantity] = orderItem.quantity
            it[price] = orderItem.price.amount
        }
        orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? = transaction {
        OrderItems.selectAll()
            .where { OrderItems.id eq id.value }
            .map { it.toOrderItem() }
            .singleOrNull()
    }

    override fun findAll(): List<OrderItem> = transaction {
        OrderItems.selectAll()
            .map { it.toOrderItem() }
    }

    override fun update(orderItem: OrderItem): OrderItem = transaction {
        val updatedRows = OrderItems.update({ OrderItems.id eq orderItem.id.value }) {
            it[orderId] = orderItem.orderId.value
            it[productId] = orderItem.productId.value
            it[quantity] = orderItem.quantity
            it[price] = orderItem.price.amount
        }
        if (updatedRows == 0) {
            throw IllegalStateException("OrderItem with id ${orderItem.id.value} not found")
        }
        orderItem
    }

    override fun delete(id: OrderItemId): Boolean = transaction {
        val deleted = OrderItems.deleteWhere { OrderItems.id eq id.value }
        deleted > 0
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> = transaction {
        OrderItems
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

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? = transaction {
        val totalQuantityAlias = OrderItems.quantity.sum()
        val orderCountAlias = OrderItems.id.count()

        // Calculate total revenue using SQL: SUM(price * quantity)
        // We need to cast quantity to decimal for multiplication
        val quantityDecimal = OrderItems.quantity.castTo<BigDecimal>(DecimalColumnType(19, 2))
        val revenueExpression = OrderItems.price.times(quantityDecimal)
        val totalRevenueAlias = revenueExpression.sum()

        OrderItems
            .select(totalQuantityAlias, totalRevenueAlias, orderCountAlias)
            .where { OrderItems.productId eq productId.value }
            .firstOrNull()
            ?.let { row ->
                val totalQty = row[totalQuantityAlias] ?: return@transaction null
                ProductSalesStatistics(
                    productId = productId,
                    totalQuantity = totalQty.toInt(),
                    totalRevenue = Money(row[totalRevenueAlias] ?: BigDecimal.ZERO),
                    orderCount = row[orderCountAlias]
                )
            }
    }

    private fun ResultRow.toOrderItem() = OrderItem(
        id = OrderItemId(this[OrderItems.id]),
        orderId = OrderId(this[OrderItems.orderId]),
        productId = ProductId(this[OrderItems.productId]),
        quantity = this[OrderItems.quantity],
        price = Money(this[OrderItems.price])
    )
}
