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

