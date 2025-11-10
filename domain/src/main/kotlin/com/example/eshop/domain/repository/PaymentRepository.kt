package com.example.eshop.domain.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.PaymentId
import com.example.eshop.domain.valueobject.PaymentMethod
import com.example.eshop.domain.valueobject.PaymentStatus

interface PaymentRepository {
    // CRUD operations
    fun save(payment: Payment): Payment
    fun findById(id: PaymentId): Payment?
    fun findAll(): List<Payment>
    fun update(payment: Payment): Payment
    fun delete(id: PaymentId): Boolean

    // Complex queries
    /**
     * 고객별 주문 통계 계산
     * 고객별 총 결제 금액, 평균 결제 금액, 결제 횟수 등을 집계
     */
    fun calculateCustomerOrderStatistics(): List<CustomerStatistics>

    /**
     * 결제 수단별 통계
     */
    fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats>

    /**
     * 특정 주문의 결제 내역 조회
     */
    fun findByOrderId(orderId: OrderId): Payment?

    /**
     * 실패한 결제 내역 조회 (주문 및 고객 정보 포함)
     */
    fun findFailedPaymentsWithDetails(): List<PaymentWithDetails>

    /**
     * 프리미엄 고객 조회 (UNION 사용)
     * 
     * 다음 두 그룹의 고객을 union으로 합쳐서 반환:
     * 1. 고액 결제자: 총 결제 금액이 minTotalAmount 이상인 고객
     * 2. 빈번한 결제자: 결제 횟수가 minPaymentCount 이상인 고객
     * 
     * UNION은 중복을 제거하므로, 두 조건을 모두 만족하는 고객도 한 번만 반환됩니다.
     */
    fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo>
}

/**
 * 고객 통계 정보
 */
data class CustomerStatistics(
    val customerId: Long,
    val customerName: String,
    val totalPayments: Money,
    val averagePayment: Money,
    val paymentCount: Long,
    val completedOrderCount: Long
)

/**
 * 결제 수단별 통계
 */
data class PaymentMethodStats(
    val method: PaymentMethod,
    val totalAmount: Money,
    val count: Long,
    val averageAmount: Money
)

/**
 * 결제 상세 정보 (결제 + 주문 + 고객)
 */
data class PaymentWithDetails(
    val payment: Payment,
    val orderDate: String,
    val customerName: String,
    val customerEmail: String
)

/**
 * 프리미엄 고객 정보
 */
data class PremiumCustomerInfo(
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val totalPaymentAmount: Money,
    val paymentCount: Long,
    val customerType: PremiumCustomerType
)

/**
 * 프리미엄 고객 유형
 */
enum class PremiumCustomerType {
    HIGH_VALUE,      // 고액 결제자
    FREQUENT_PAYER,  // 빈번한 결제자
    BOTH             // 둘 다 해당
}

