package com.example.eshop.springdatajpa.repository

import com.example.eshop.springdatajpa.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface PaymentEntityRepository : JpaRepository<PaymentEntity, Long> {
    
    /**
     * 특정 주문의 결제 내역 조회
     */
    fun findByOrderId(orderId: Long): PaymentEntity?
    
    /**
     * 고객별 주문 통계 계산
     * 고객별 총 결제 금액, 평균 결제 금액, 결제 횟수 등을 집계
     */
    @Query("""
        SELECT 
            c.id,
            c.name,
            SUM(p.amount),
            AVG(p.amount),
            COUNT(p.id),
            COUNT(DISTINCT CASE WHEN o.status = 'DELIVERED' THEN o.id END)
        FROM PaymentEntity p
        JOIN OrderEntity o ON p.orderId = o.id
        JOIN CustomerEntity c ON o.customerId = c.id
        WHERE p.status = 'COMPLETED'
        GROUP BY c.id, c.name
    """)
    fun calculateCustomerOrderStatistics(): List<Array<Any>>
    
    /**
     * 결제 수단별 통계
     */
    @Query("""
        SELECT 
            p.method,
            SUM(p.amount),
            COUNT(p.id),
            AVG(p.amount)
        FROM PaymentEntity p
        WHERE p.status = 'COMPLETED'
        GROUP BY p.method
    """)
    fun calculatePaymentMethodStatistics(): List<Array<Any>>
    
    /**
     * 실패한 결제 내역 조회 (주문 및 고객 정보 포함)
     */
    @Query("""
        SELECT p, o.orderDate, c.name, c.email
        FROM PaymentEntity p
        JOIN OrderEntity o ON p.orderId = o.id
        JOIN CustomerEntity c ON o.customerId = c.id
        WHERE p.status = 'FAILED'
    """)
    fun findFailedPaymentsWithDetails(): List<Array<Any>>
    
    /**
     * 프리미엄 고객 조회 (UNION 사용)
     * 
     * 다음 두 그룹의 고객을 union으로 합쳐서 반환:
     * 1. 고액 결제자: 총 결제 금액이 minTotalAmount 이상인 고객
     * 2. 빈번한 결제자: 결제 횟수가 minPaymentCount 이상인 고객
     */
    @Query(value = """
        SELECT c.id, c.name, c.email, SUM(p.amount) as total_amount, COUNT(p.id) as payment_count, 'HIGH_VALUE' as customer_type
        FROM payment p
        JOIN orders o ON p.order_id = o.id
        JOIN customer c ON o.customer_id = c.id
        WHERE p.status = 'COMPLETED'
        GROUP BY c.id, c.name, c.email
        HAVING SUM(p.amount) >= :minTotalAmount
        UNION
        SELECT c.id, c.name, c.email, SUM(p.amount) as total_amount, COUNT(p.id) as payment_count, 'FREQUENT_PAYER' as customer_type
        FROM payment p
        JOIN orders o ON p.order_id = o.id
        JOIN customer c ON o.customer_id = c.id
        WHERE p.status = 'COMPLETED'
        GROUP BY c.id, c.name, c.email
        HAVING COUNT(p.id) >= :minPaymentCount
    """, nativeQuery = true)
    fun findPremiumCustomersWithUnion(
        @Param("minTotalAmount") minTotalAmount: BigDecimal,
        @Param("minPaymentCount") minPaymentCount: Long
    ): List<Array<Any>>

    /**
     * 월별 결제 추이 분석 (Native Query)
     *
     * Window function LAG()를 사용하여 이전 달 데이터와 비교
     * CTE를 사용하여 월별 집계 후 전월 대비 데이터 계산
     */
    @Query(value = """
        WITH monthly_stats AS (
            SELECT
                strftime('%Y-%m', payment_date) as year_month,
                SUM(amount) as total_amount,
                COUNT(*) as payment_count,
                AVG(amount) as average_amount
            FROM payment
            WHERE status = 'COMPLETED'
            GROUP BY strftime('%Y-%m', payment_date)
        )
        SELECT
            year_month,
            total_amount,
            payment_count,
            average_amount,
            LAG(total_amount) OVER (ORDER BY year_month) as previous_month_amount
        FROM monthly_stats
        ORDER BY year_month
    """, nativeQuery = true)
    fun findMonthlyPaymentTrends(): List<Array<Any>>

    /**
     * 고객별 최근 결제 내역 조회 (Native Query)
     *
     * ROW_NUMBER() window function을 사용하여
     * 각 고객의 결제를 최신순으로 순위 매김
     */
    @Query(value = """
        WITH ranked_payments AS (
            SELECT
                p.id as payment_id,
                p.order_id,
                p.amount,
                p.payment_date,
                p.method,
                p.status,
                c.id as customer_id,
                c.name as customer_name,
                o.order_date,
                ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY p.payment_date DESC) as row_num
            FROM payment p
            INNER JOIN orders o ON p.order_id = o.id
            INNER JOIN customer c ON o.customer_id = c.id
            WHERE p.status = 'COMPLETED'
        )
        SELECT *
        FROM ranked_payments
        WHERE row_num <= :limit
        ORDER BY customer_id, row_num
    """, nativeQuery = true)
    fun findRecentPaymentsByAllCustomers(@Param("limit") limit: Int): List<Array<Any>>
}

