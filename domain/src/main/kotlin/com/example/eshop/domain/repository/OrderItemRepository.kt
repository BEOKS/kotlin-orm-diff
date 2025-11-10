package com.example.eshop.domain.repository

import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId

interface OrderItemRepository {
    // CRUD operations
    fun save(orderItem: OrderItem): OrderItem
    fun findById(id: OrderItemId): OrderItem?
    fun findAll(): List<OrderItem>
    fun update(orderItem: OrderItem): OrderItem
    fun delete(id: OrderItemId): Boolean

    // Complex queries
    /**
     * 특정 주문의 모든 항목 조회 (상품 정보 포함)
     */
    fun findByOrderIdWithProduct(orderId: OrderId): List<OrderItemWithProduct>

    /**
     * 특정 상품이 포함된 주문 항목 통계
     * 총 판매 수량과 총 판매 금액 계산
     */
    fun calculateProductSalesStatistics(productId: ProductId): ProductSalesStatistics?
}

/**
 * 주문 항목과 상품 정보
 */
data class OrderItemWithProduct(
    val orderItem: OrderItem,
    val productName: String,
    val productCategory: String
)

/**
 * 상품 판매 통계
 */
data class ProductSalesStatistics(
    val productId: ProductId,
    val totalQuantity: Int,
    val totalRevenue: Money,
    val orderCount: Long
)

