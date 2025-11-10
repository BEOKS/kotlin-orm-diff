package com.example.eshop.springdatajpa.repository

import com.example.eshop.springdatajpa.entity.OrderItemEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderItemEntityRepository : JpaRepository<OrderItemEntity, Long> {

    /**
     * 특정 주문의 모든 항목 조회
     */
    fun findByOrderId(orderId: Long): List<OrderItemEntity>

    /**
     * 특정 주문의 주문 항목과 상품 정보를 한번에 조회 (N+1 문제 해결)
     */
    @Query("""
        SELECT oi, p.name, p.category
        FROM OrderItemEntity oi
        JOIN ProductEntity p ON oi.productId = p.id
        WHERE oi.orderId = :orderId
    """)
    fun findByOrderIdWithProduct(@Param("orderId") orderId: Long): List<Array<Any>>

    /**
     * 특정 상품이 포함된 주문 항목 통계
     * 총 판매 수량과 총 판매 금액 계산
     */
    @Query("""
        SELECT
            SUM(oi.quantity) as totalQuantity,
            SUM(oi.price * oi.quantity) as totalRevenue,
            COUNT(DISTINCT oi.orderId) as orderCount
        FROM OrderItemEntity oi
        WHERE oi.productId = :productId
    """)
    fun calculateProductSalesStatistics(@Param("productId") productId: Long): Array<Any>?
}

