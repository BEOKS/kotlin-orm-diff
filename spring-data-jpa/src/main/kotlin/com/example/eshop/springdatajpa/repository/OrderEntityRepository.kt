package com.example.eshop.springdatajpa.repository

import com.example.eshop.springdatajpa.entity.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderEntityRepository : JpaRepository<OrderEntity, Long> {

    /**
     * 특정 고객의 주문 조회
     */
    fun findByCustomerId(customerId: Long): List<OrderEntity>

    /**
     * 특정 고객의 주문과 관련 정보 한번에 조회 (N+1 문제 해결)
     * Order, Payment status, OrderItem count, Customer name을 한 쿼리로 조회
     */
    @Query("""
        SELECT o, p.status, COUNT(oi.id), c.name
        FROM OrderEntity o
        LEFT JOIN PaymentEntity p ON p.orderId = o.id
        LEFT JOIN OrderItemEntity oi ON oi.orderId = o.id
        LEFT JOIN CustomerEntity c ON c.id = o.customerId
        WHERE o.customerId = :customerId
        GROUP BY o.id, o.customerId, o.orderDate, o.totalAmount, o.status, p.status, c.name
    """)
    fun findOrdersWithDetailsByCustomerId(@Param("customerId") customerId: Long): List<Array<Any>>

    /**
     * 미결제 주문 조회 (Payment가 없거나 PENDING 상태인 주문)
     */
    @Query("""
        SELECT o FROM OrderEntity o
        WHERE NOT EXISTS (
            SELECT 1 FROM PaymentEntity p
            WHERE p.orderId = o.id AND p.status = 'COMPLETED'
        )
    """)
    fun findUnpaidOrders(): List<OrderEntity>

    /**
     * 상태별 주문 개수 집계
     */
    @Query("""
        SELECT o.status, COUNT(o) FROM OrderEntity o
        GROUP BY o.status
    """)
    fun countOrdersByStatus(): List<Array<Any>>
}

