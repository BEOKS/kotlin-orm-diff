package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.repository.OrderItemRepository
import com.example.eshop.domain.repository.OrderItemWithProduct
import com.example.eshop.domain.repository.ProductSalesStatistics
import com.example.eshop.domain.valueobject.*
import com.example.eshop.springdatajpa.entity.OrderItemEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Transactional(readOnly = true)
class SpringDataOrderItemRepository(
    private val orderItemEntityRepository: OrderItemEntityRepository,
    private val productEntityRepository: ProductEntityRepository
) : OrderItemRepository {

    @Transactional
    override fun save(orderItem: OrderItem): OrderItem {
        // Check if entity exists
        val entity = if (orderItem.id.value != 0L && orderItemEntityRepository.existsById(orderItem.id.value)) {
            // Update existing entity
            orderItemEntityRepository.findById(orderItem.id.value).get().apply {
                orderId = orderItem.orderId.value
                productId = orderItem.productId.value
                quantity = orderItem.quantity
                price = orderItem.price.amount
            }
        } else {
            // Create new entity
            OrderItemEntity(
                id = orderItem.id.value,
                orderId = orderItem.orderId.value,
                productId = orderItem.productId.value,
                quantity = orderItem.quantity,
                price = orderItem.price.amount
            )
        }
        orderItemEntityRepository.save(entity)
        return orderItem
    }

    override fun findById(id: OrderItemId): OrderItem? {
        return orderItemEntityRepository.findById(id.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<OrderItem> {
        return orderItemEntityRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun update(orderItem: OrderItem): OrderItem {
        val entity = orderItemEntityRepository.findById(orderItem.id.value)
            .orElseThrow { IllegalArgumentException("OrderItem not found: ${orderItem.id.value}") }

        entity.orderId = orderItem.orderId.value
        entity.productId = orderItem.productId.value
        entity.quantity = orderItem.quantity
        entity.price = orderItem.price.amount
        orderItemEntityRepository.save(entity)
        return orderItem
    }

    @Transactional
    override fun delete(id: OrderItemId): Boolean {
        return if (orderItemEntityRepository.existsById(id.value)) {
            orderItemEntityRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }

    override fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct> {
        // N+1 문제 해결: 한 번의 쿼리로 OrderItem과 Product 정보 조회
        return orderItemEntityRepository.findByOrderIdWithProduct(orderId.value).map { row ->
            val orderItem = (row[0] as OrderItemEntity).toDomain()
            val productName = row[1] as String
            val productCategory = row[2] as String

            OrderItemWithProduct(
                orderItem = orderItem,
                productName = productName,
                productCategory = productCategory
            )
        }
    }

    override fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics? {
        val result = orderItemEntityRepository.calculateProductSalesStatistics(productId.value)
            ?: return null
        
        val totalQuantity = (result[0] as? Number)?.toInt() ?: 0
        val totalRevenue = (result[1] as? BigDecimal) ?: BigDecimal.ZERO
        val orderCount = (result[2] as? Number)?.toLong() ?: 0L
        
        if (totalQuantity == 0) return null
        
        return ProductSalesStatistics(
            productId = productId,
            totalQuantity = totalQuantity,
            totalRevenue = Money(totalRevenue),
            orderCount = orderCount
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

