package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jpa.entity.OrderEntity
import com.example.eshop.jpa.entity.OrderItemEntity
import com.example.eshop.jpa.entity.ProductEntity
import jakarta.persistence.EntityManager

class JpaOrderItemRepository(private val em: EntityManager) : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        // Find the order and product entities to establish relationships
        val order = em.find(OrderEntity::class.java, orderItem.orderId.value)
            ?: throw IllegalArgumentException("Order not found: ${orderItem.orderId.value}")
        val product = em.find(ProductEntity::class.java, orderItem.productId.value)
            ?: throw IllegalArgumentException("Product not found: ${orderItem.productId.value}")

        val entity = OrderItemEntity(
            id = 0, // Auto-generated
            order = order,
            product = product,
            quantity = orderItem.quantity,
            price = orderItem.price.amount
        )
        em.persist(entity)
        em.flush() // Ensure ID is generated

        // Return new OrderItem with generated ID
        return OrderItem(
            id = OrderItemId(entity.id),
            orderId = orderItem.orderId,
            productId = orderItem.productId,
            quantity = orderItem.quantity,
            price = orderItem.price
        )
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return em.find(OrderItemEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<OrderItem> {
        return em.createQuery("SELECT oi FROM OrderItemEntity oi ORDER BY oi.id", OrderItemEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        val entity = em.find(OrderItemEntity::class.java, orderItem.id.value)
            ?: throw IllegalArgumentException("OrderItem not found: ${orderItem.id.value}")

        // Find the order and product entities if they changed
        val order = em.find(OrderEntity::class.java, orderItem.orderId.value)
            ?: throw IllegalArgumentException("Order not found: ${orderItem.orderId.value}")
        val product = em.find(ProductEntity::class.java, orderItem.productId.value)
            ?: throw IllegalArgumentException("Product not found: ${orderItem.productId.value}")

        // Dirty checking - no need for merge()
        entity.order = order
        entity.product = product
        entity.quantity = orderItem.quantity
        entity.price = orderItem.price.amount

        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val entity = em.find(OrderItemEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        val query = em.createQuery("""
            SELECT oi, oi.product.name, oi.product.category
            FROM OrderItemEntity oi
            WHERE oi.order.id = :orderId
        """)

        query.setParameter("orderId", orderId.value)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            val entity = row[0] as OrderItemEntity
            OrderItemWithProduct(
                orderItem = entity.toDomain(),
                productName = row[1] as String,
                productCategory = row[2] as String
            )
        }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        val query = em.createQuery("""
            SELECT
                oi.product.id,
                SUM(oi.quantity),
                SUM(oi.quantity * oi.price),
                COUNT(oi)
            FROM OrderItemEntity oi
            WHERE oi.product.id = :productId
            GROUP BY oi.product.id
        """)

        query.setParameter("productId", productId.value)

        @Suppress("UNCHECKED_CAST")
        val result = query.resultList as List<Array<Any>>
        if (result.isEmpty()) return null

        val row = result[0]
        return ProductSalesStatistics(
            productId = ProductId(row[0] as Long),
            totalQuantity = (row[1] as Long).toInt(),
            totalRevenue = Money(row[2] as java.math.BigDecimal),
            orderCount = row[3] as Long
        )
    }

    private fun OrderItemEntity.toDomain(): OrderItem {
        return OrderItem(
            id = OrderItemId(id),
            orderId = OrderId(orderId),
            productId = ProductId(productId),
            quantity = quantity,
            price = Money(price)
        )
    }
}
