package com.example.eshop.domain.examples

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderSearchCriteria
import com.example.eshop.domain.repository.OrderSearchResult
import com.example.eshop.domain.repository.OrderSortField
import com.example.eshop.domain.repository.SortDirection
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderStatus
import com.example.eshop.domain.valueobject.PaymentMethod
import com.example.eshop.domain.valueobject.PaymentStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주문 서비스 예시
 * 
 * OrderRepository를 사용하여 주문 엔티티를 조회하는 서비스 레이어 예시 코드입니다.
 * 
 * 사용 예시:
 * ```
 * val orderRepository: OrderRepository = ... // 구현체 주입
 * val orderService = OrderServiceExample(orderRepository)
 * 
 * // ID로 주문 조회
 * val order = orderService.findOrderById(OrderId(1L))
 * 
 * // 검색 조건으로 주문 리스트 조회
 * val criteria = OrderSearchCriteria(
 *     customerName = "John",
 *     orderStatuses = listOf(OrderStatus.DELIVERED)
 * )
 * val orders = orderService.searchOrders(criteria)
 * ```
 */
class OrderServiceExample(
    private val orderRepository: OrderRepository
) {
    /**
     * ID로 주문 엔티티를 조회합니다.
     * 
     * @param id 조회할 주문의 ID
     * @return 주문 엔티티, 존재하지 않으면 null
     * 
     * 사용 예시:
     * ```
     * // 기본 사용
     * val orderId = OrderId(1L)
     * val order = orderService.findOrderById(orderId)
     * 
     * if (order != null) {
     *     println("주문 ID: ${order.id.value}")
     *     println("주문 상태: ${order.status}")
     *     println("주문 금액: ${order.totalAmount.amount}")
     * } else {
     *     println("주문을 찾을 수 없습니다.")
     * }
     * 
     * // 엘비스 연산자 사용
     * val orderAmount = orderService.findOrderById(OrderId(1L))?.totalAmount
     *     ?: throw IllegalArgumentException("주문을 찾을 수 없습니다.")
     * 
     * // 안전한 호출 체이닝
     * orderService.findOrderById(OrderId(1L))
     *     ?.let { order ->
     *         if (order.isCompleted()) {
     *             println("주문이 완료되었습니다.")
     *         }
     *     }
     * ```
     */
    fun findOrderById(id: OrderId): Order? {
        return orderRepository.findById(id)
    }

    /**
     * 검색 조건을 사용하여 주문 리스트를 조회합니다.
     * 
     * @param criteria 검색 조건 (OrderSearchCriteria)
     * @return 검색 조건에 맞는 주문 리스트 (OrderSearchResult)
     * 
     * 사용 예시:
     * ```
     * // 1. 고객명으로 검색
     * val criteria1 = OrderSearchCriteria(
     *     customerName = "John"
     * )
     * val orders1 = orderService.searchOrders(criteria1)
     * 
     * // 2. 주문 상태로 필터링
     * val criteria2 = OrderSearchCriteria(
     *     orderStatuses = listOf(OrderStatus.DELIVERED, OrderStatus.PENDING)
     * )
     * val orders2 = orderService.searchOrders(criteria2)
     * 
     * // 3. 날짜 범위로 검색
     * val criteria3 = OrderSearchCriteria(
     *     orderDateFrom = LocalDateTime.of(2024, 1, 1, 0, 0),
     *     orderDateTo = LocalDateTime.of(2024, 12, 31, 23, 59)
     * )
     * val orders3 = orderService.searchOrders(criteria3)
     * 
     * // 4. 금액 범위로 검색
     * val criteria4 = OrderSearchCriteria(
     *     minTotalAmount = Money(100.toBigDecimal()),
     *     maxTotalAmount = Money(1000.toBigDecimal())
     * )
     * val orders4 = orderService.searchOrders(criteria4)
     * 
     * // 5. 상품명으로 검색
     * val criteria5 = OrderSearchCriteria(
     *     productName = "Laptop"
     * )
     * val orders5 = orderService.searchOrders(criteria5)
     * 
     * // 6. 상품 카테고리로 검색
     * val criteria6 = OrderSearchCriteria(
     *     productCategory = "Electronics"
     * )
     * val orders6 = orderService.searchOrders(criteria6)
     * 
     * // 7. 결제 상태로 검색
     * val criteria7 = OrderSearchCriteria(
     *     paymentStatuses = listOf(PaymentStatus.COMPLETED)
     * )
     * val orders7 = orderService.searchOrders(criteria7)
     * 
     * // 8. 결제 방법으로 검색
     * val criteria8 = OrderSearchCriteria(
     *     paymentMethods = listOf(PaymentMethod.CREDIT_CARD, PaymentMethod.BANK_TRANSFER)
     * )
     * val orders8 = orderService.searchOrders(criteria8)
     * 
     * // 9. 복합 조건 검색 (고객명 + 주문 상태 + 날짜 범위)
     * val criteria9 = OrderSearchCriteria(
     *     customerName = "John",
     *     orderStatuses = listOf(OrderStatus.DELIVERED),
     *     orderDateFrom = LocalDateTime.of(2024, 10, 1, 0, 0),
     *     orderDateTo = LocalDateTime.of(2024, 10, 31, 23, 59)
     * )
     * val orders9 = orderService.searchOrders(criteria9)
     * 
     * // 10. 페이징 처리
     * val criteria10 = OrderSearchCriteria(
     *     offset = 0,
     *     limit = 10,
     *     sortBy = OrderSortField.ORDER_DATE,
     *     sortDirection = SortDirection.DESC
     * )
     * val page1 = orderService.searchOrders(criteria10)
     * 
     * val criteria11 = OrderSearchCriteria(
     *     offset = 10,
     *     limit = 10,
     *     sortBy = OrderSortField.ORDER_DATE,
     *     sortDirection = SortDirection.DESC
     * )
     * val page2 = orderService.searchOrders(criteria11)
     * 
     * // 11. 정렬 옵션 사용
     * // 금액 기준 오름차순 정렬
     * val criteria12 = OrderSearchCriteria(
     *     sortBy = OrderSortField.TOTAL_AMOUNT,
     *     sortDirection = SortDirection.ASC
     * )
     * val orders12 = orderService.searchOrders(criteria12)
     * 
     * // 고객명 기준 내림차순 정렬
     * val criteria13 = OrderSearchCriteria(
     *     sortBy = OrderSortField.CUSTOMER_NAME,
     *     sortDirection = SortDirection.DESC
     * )
     * val orders13 = orderService.searchOrders(criteria13)
     * 
     * // 12. 고객 등록일 범위로 검색
     * val criteria14 = OrderSearchCriteria(
     *     customerRegisteredDateFrom = LocalDate.of(2024, 1, 1),
     *     customerRegisteredDateTo = LocalDate.of(2024, 12, 31)
     * )
     * val orders14 = orderService.searchOrders(criteria14)
     * 
     * // 13. 상품 가격 범위로 검색
     * val criteria15 = OrderSearchCriteria(
     *     minProductPrice = Money(50.toBigDecimal()),
     *     maxProductPrice = Money(500.toBigDecimal())
     * )
     * val orders15 = orderService.searchOrders(criteria15)
     * 
     * // 14. 결제 날짜 범위로 검색
     * val criteria16 = OrderSearchCriteria(
     *     paymentDateFrom = LocalDateTime.of(2024, 10, 1, 0, 0),
     *     paymentDateTo = LocalDateTime.of(2024, 10, 31, 23, 59)
     * )
     * val orders16 = orderService.searchOrders(criteria16)
     * 
     * // 15. 결과 처리 예시
     * val results = orderService.searchOrders(criteria1)
     * results.forEach { result ->
     *     println("주문 ID: ${result.order.id.value}")
     *     println("고객명: ${result.customerName}")
     *     println("고객 이메일: ${result.customerEmail}")
     *     println("주문 항목 수: ${result.itemCount}")
     *     println("상품명: ${result.productNames.joinToString(", ")}")
     *     println("결제 상태: ${result.paymentStatus}")
     *     println("---")
     * }
     * ```
     */
    fun searchOrders(criteria: OrderSearchCriteria): List<OrderSearchResult> {
        return orderRepository.searchOrders(criteria)
    }
}

