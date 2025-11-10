package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jpa.entity.OrderItemEntity
import jakarta.persistence.EntityManager

class JpaOrderItemRepository(private val em: EntityManager) : OrderItemRepository {

    override fun save(orderItem: OrderItem): OrderItem {
        val entity = OrderItemEntity(
            id = orderItem.id.value,
            orderId = orderItem.orderId.value,
            productId = orderItem.productId.value,
            quantity = orderItem.quantity,
            price = orderItem.price.amount
        )
        em.persist(entity)
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return em.find(OrderItemEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<OrderItem> {
        return em.createQuery("SELECT oi FROM OrderItemEntity oi", OrderItemEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(orderItem: OrderItem): OrderItem {
        val entity = em.find(OrderItemEntity::class.java, orderItem.id.value)
            ?: throw IllegalArgumentException("OrderItem not found: ${orderItem.id.value}")
        
        entity.orderId = orderItem.orderId.value
        entity.productId = orderItem.productId.value
        entity.quantity = orderItem.quantity
        entity.price = orderItem.price.amount
        em.merge(entity)
        return orderItem
    }

    override fun delete(id: OrderItemId): Boolean {
        val entity = em.find(OrderItemEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        val query = em.createQuery("""
            SELECT oi, p.name, p.category
            FROM OrderItemEntity oi
            JOIN ProductEntity p ON oi.productId = p.id
            WHERE oi.orderId = :orderId
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
                oi.productId,
                SUM(oi.quantity),
                SUM(oi.quantity * oi.price),
                COUNT(oi)
            FROM OrderItemEntity oi
            WHERE oi.productId = :productId
            GROUP BY oi.productId
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

