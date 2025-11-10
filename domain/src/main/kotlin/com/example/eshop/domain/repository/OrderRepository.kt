package com.example.eshop.domain.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderStatus

interface OrderRepository {
    // CRUD operations
    fun save(order: Order): Order
    fun findById(id: OrderId): Order?
    fun findAll(): List<Order>
    fun update(order: Order): Order
    fun delete(id: OrderId): Boolean

    // Complex queries
    /**
     * 주문-결제-상품 정보 조인 조회
     * 특정 고객의 주문과 관련된 결제 정보 및 주문 항목 정보를 함께 조회
     */
    fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails>

    /**
     * 미결제 주문 상세 정보 조회
     * Payment가 없거나 PENDING 상태인 주문과 고객 정보를 함께 조회
     */
    fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer>

    /**
     * 상태별 주문 통계 조회
     */
    fun countOrdersByStatus(): Map<OrderStatus, Long>

    /**
     * 동적 검색 조건을 이용한 주문 검색
     * 여러 테이블(Order, Customer, Product, Payment)의 컬럼을 조합하여 검색
     */
    fun searchOrders(criteria: OrderSearchCriteria): List<OrderSearchResult>

    /**
     * 동적 검색 조건에 매칭되는 주문 개수 조회
     */
    fun countOrders(criteria: OrderSearchCriteria): Long
}

/**
 * 주문 상세 정보 (주문 + 결제 + 주문항목)
 */
data class OrderWithDetails(
    val order: Order,
    val paymentStatus: String?,
    val itemCount: Int,
    val customerName: String
)

/**
 * 주문과 고객 정보
 */
data class OrderWithCustomer(
    val order: Order,
    val customerName: String,
    val customerEmail: String
)

