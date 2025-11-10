package com.example.eshop.domain.repository

import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderStatus
import com.example.eshop.domain.valueobject.PaymentMethod
import com.example.eshop.domain.valueobject.PaymentStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주문 검색 조건
 * 여러 테이블(Order, Customer, Product, Payment)의 컬럼을 조합하여 검색할 수 있습니다.
 */
data class OrderSearchCriteria(
    // Order 테이블 조건
    val orderDateFrom: LocalDateTime? = null,
    val orderDateTo: LocalDateTime? = null,
    val minTotalAmount: Money? = null,
    val maxTotalAmount: Money? = null,
    val orderStatuses: List<OrderStatus>? = null,

    // Customer 테이블 조건
    val customerName: String? = null,  // 부분 일치 검색
    val customerEmail: String? = null,  // 부분 일치 검색
    val customerAddress: String? = null,  // 부분 일치 검색
    val customerRegisteredDateFrom: LocalDate? = null,
    val customerRegisteredDateTo: LocalDate? = null,

    // Product 테이블 조건 (OrderItem을 통해 조인)
    val productName: String? = null,  // 부분 일치 검색
    val productCategory: String? = null,
    val minProductPrice: Money? = null,
    val maxProductPrice: Money? = null,

    // Payment 테이블 조건
    val paymentMethods: List<PaymentMethod>? = null,
    val paymentStatuses: List<PaymentStatus>? = null,
    val paymentDateFrom: LocalDateTime? = null,
    val paymentDateTo: LocalDateTime? = null,

    // 페이징 및 정렬
    val offset: Int = 0,
    val limit: Int = 20,
    val sortBy: OrderSortField = OrderSortField.ORDER_DATE,
    val sortDirection: SortDirection = SortDirection.DESC
) {
    init {
        require(offset >= 0) { "Offset must be non-negative" }
        require(limit > 0) { "Limit must be positive" }
        require(limit <= 1000) { "Limit must not exceed 1000" }
    }

    /**
     * 검색 조건이 하나라도 설정되어 있는지 확인
     */
    fun hasAnyFilter(): Boolean {
        return orderDateFrom != null ||
                orderDateTo != null ||
                minTotalAmount != null ||
                maxTotalAmount != null ||
                !orderStatuses.isNullOrEmpty() ||
                !customerName.isNullOrBlank() ||
                !customerEmail.isNullOrBlank() ||
                !customerAddress.isNullOrBlank() ||
                customerRegisteredDateFrom != null ||
                customerRegisteredDateTo != null ||
                !productName.isNullOrBlank() ||
                !productCategory.isNullOrBlank() ||
                minProductPrice != null ||
                maxProductPrice != null ||
                !paymentMethods.isNullOrEmpty() ||
                !paymentStatuses.isNullOrEmpty() ||
                paymentDateFrom != null ||
                paymentDateTo != null
    }
}

/**
 * 정렬 필드
 */
enum class OrderSortField {
    ORDER_DATE,
    TOTAL_AMOUNT,
    CUSTOMER_NAME,
    ORDER_STATUS
}

/**
 * 정렬 방향
 */
enum class SortDirection {
    ASC,
    DESC
}

/**
 * 주문 검색 결과
 * 여러 테이블의 정보를 포함
 */
data class OrderSearchResult(
    val order: com.example.eshop.domain.entity.Order,
    val customerName: String,
    val customerEmail: String,
    val itemCount: Int,
    val productNames: List<String>,
    val paymentStatus: String?
)
