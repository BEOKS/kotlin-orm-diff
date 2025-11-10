package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.TestDatabase
import org.jooq.DSLContext
import org.junit.jupiter.api.*
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqOrderSearchTest {

    private lateinit var connection: Connection
    private lateinit var dsl: DSLContext
    private lateinit var orderRepository: JooqOrderRepository
    private lateinit var customerRepository: JooqCustomerRepository
    private lateinit var productRepository: JooqProductRepository
    private lateinit var orderItemRepository: JooqOrderItemRepository
    private lateinit var paymentRepository: JooqPaymentRepository

    @BeforeAll
    fun setupAll() {
        connection = TestDatabase.createConnection()
        dsl = TestDatabase.createDSLContext(connection)
        TestDatabase.initSchema(connection)

        orderRepository = JooqOrderRepository(dsl)
        customerRepository = JooqCustomerRepository(dsl)
        productRepository = JooqProductRepository(dsl)
        orderItemRepository = JooqOrderItemRepository(dsl)
        paymentRepository = JooqPaymentRepository(dsl)
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(dsl)
        setupTestData()
    }

    @AfterAll
    fun teardown() {
        connection.close()
    }

    private fun setupTestData() {
        // Customers
        val customer1 = Customer(
            id = CustomerId(1L),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St, Seoul",
            registeredDate = LocalDate.of(2024, 1, 1)
        )
        val customer2 = Customer(
            id = CustomerId(2L),
            name = "Jane Smith",
            email = "jane@example.com",
            address = "456 Oak Ave, Busan",
            registeredDate = LocalDate.of(2024, 6, 1)
        )
        customerRepository.save(customer1)
        customerRepository.save(customer2)

        // Products
        val product1 = Product(
            id = ProductId(1L),
            name = "Laptop",
            price = Money(1000.toBigDecimal()),
            stock = 10,
            category = "Electronics"
        )
        val product2 = Product(
            id = ProductId(2L),
            name = "Mouse",
            price = Money(50.toBigDecimal()),
            stock = 100,
            category = "Electronics"
        )
        val product3 = Product(
            id = ProductId(3L),
            name = "Book",
            price = Money(30.toBigDecimal()),
            stock = 50,
            category = "Books"
        )
        productRepository.save(product1)
        productRepository.save(product2)
        productRepository.save(product3)

        // Orders
        val order1 = Order(
            id = OrderId(1L),
            customerId = CustomerId(1L),
            orderDate = LocalDateTime.of(2024, 10, 1, 10, 0),
            totalAmount = Money(1050.toBigDecimal()),
            status = OrderStatus.DELIVERED
        )
        val order2 = Order(
            id = OrderId(2L),
            customerId = CustomerId(2L),
            orderDate = LocalDateTime.of(2024, 10, 15, 14, 30),
            totalAmount = Money(30.toBigDecimal()),
            status = OrderStatus.PENDING
        )
        val order3 = Order(
            id = OrderId(3L),
            customerId = CustomerId(1L),
            orderDate = LocalDateTime.of(2024, 11, 1, 9, 15),
            totalAmount = Money(50.toBigDecimal()),
            status = OrderStatus.DELIVERED
        )
        orderRepository.save(order1)
        orderRepository.save(order2)
        orderRepository.save(order3)

        // Order Items
        val orderItem1 = OrderItem(
            id = OrderItemId(1L),
            orderId = OrderId(1L),
            productId = ProductId(1L),
            quantity = 1,
            price = Money(1000.toBigDecimal())
        )
        val orderItem2 = OrderItem(
            id = OrderItemId(2L),
            orderId = OrderId(1L),
            productId = ProductId(2L),
            quantity = 1,
            price = Money(50.toBigDecimal())
        )
        val orderItem3 = OrderItem(
            id = OrderItemId(3L),
            orderId = OrderId(2L),
            productId = ProductId(3L),
            quantity = 1,
            price = Money(30.toBigDecimal())
        )
        val orderItem4 = OrderItem(
            id = OrderItemId(4L),
            orderId = OrderId(3L),
            productId = ProductId(2L),
            quantity = 1,
            price = Money(50.toBigDecimal())
        )
        orderItemRepository.save(orderItem1)
        orderItemRepository.save(orderItem2)
        orderItemRepository.save(orderItem3)
        orderItemRepository.save(orderItem4)

        // Payments
        val payment1 = Payment(
            id = PaymentId(1L),
            orderId = OrderId(1L),
            amount = Money(1050.toBigDecimal()),
            paymentDate = LocalDateTime.of(2024, 10, 1, 10, 5),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.COMPLETED
        )
        val payment3 = Payment(
            id = PaymentId(3L),
            orderId = OrderId(3L),
            amount = Money(50.toBigDecimal()),
            paymentDate = LocalDateTime.of(2024, 11, 1, 9, 20),
            method = PaymentMethod.BANK_TRANSFER,
            status = PaymentStatus.COMPLETED
        )
        paymentRepository.save(payment1)
        paymentRepository.save(payment3)
    }

    @Test
    fun `should search orders by customer name`() {
        // Given
        val criteria = OrderSearchCriteria(
            customerName = "John"
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(2, results.size)
        Assertions.assertTrue(results.all { it.customerName.contains("John") })
        println("✓ Found ${results.size} orders for customer 'John'")
        results.forEach { println("  - Order ${it.order.id.value}: ${it.customerName}") }
    }

    @Test
    fun `should search orders by order status`() {
        // Given
        val criteria = OrderSearchCriteria(
            orderStatuses = listOf(OrderStatus.DELIVERED)
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(2, results.size)
        Assertions.assertTrue(results.all { it.order.status == OrderStatus.DELIVERED })
        println("✓ Found ${results.size} DELIVERED orders")
    }

    @Test
    fun `should search orders by total amount range`() {
        // Given
        val criteria = OrderSearchCriteria(
            minTotalAmount = Money(100.toBigDecimal()),
            maxTotalAmount = Money(2000.toBigDecimal())
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(1, results.size)
        Assertions.assertEquals(OrderId(1L), results[0].order.id)
        println("✓ Found ${results.size} orders with amount between 100 and 2000")
    }

    @Test
    fun `should search orders by product name`() {
        // Given
        val criteria = OrderSearchCriteria(
            productName = "Laptop"
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(1, results.size)
        Assertions.assertTrue(results[0].productNames.contains("Laptop"))
        println("✓ Found ${results.size} orders containing 'Laptop'")
        println("  - Products: ${results[0].productNames.joinToString(", ")}")
    }

    @Test
    fun `should search orders by product category`() {
        // Given
        val criteria = OrderSearchCriteria(
            productCategory = "Electronics"
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(2, results.size)
        println("✓ Found ${results.size} orders with Electronics products")
    }

    @Test
    fun `should search orders by payment status`() {
        // Given
        val criteria = OrderSearchCriteria(
            paymentStatuses = listOf(PaymentStatus.COMPLETED)
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(2, results.size)
        Assertions.assertTrue(results.all { it.paymentStatus == PaymentStatus.COMPLETED.name })
        println("✓ Found ${results.size} orders with COMPLETED payment")
    }

    @Test
    fun `should search orders by date range`() {
        // Given
        val criteria = OrderSearchCriteria(
            orderDateFrom = LocalDateTime.of(2024, 10, 10, 0, 0),
            orderDateTo = LocalDateTime.of(2024, 10, 31, 23, 59)
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(1, results.size)
        Assertions.assertEquals(OrderId(2L), results[0].order.id)
        println("✓ Found ${results.size} orders in October 2024 (after 10th)")
    }

    @Test
    fun `should search orders with multiple criteria`() {
        // Given
        val criteria = OrderSearchCriteria(
            customerName = "John",
            orderStatuses = listOf(OrderStatus.DELIVERED),
            productCategory = "Electronics"
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(2, results.size)
        println("✓ Found ${results.size} orders matching multiple criteria")
        results.forEach {
            println("  - Order ${it.order.id.value}: ${it.customerName}, ${it.order.status}, products: ${it.productNames.joinToString(", ")}")
        }
    }

    @Test
    fun `should search with pagination`() {
        // Given
        val criteria1 = OrderSearchCriteria(
            offset = 0,
            limit = 2,
            sortBy = OrderSortField.ORDER_DATE,
            sortDirection = SortDirection.ASC
        )
        val criteria2 = OrderSearchCriteria(
            offset = 2,
            limit = 2,
            sortBy = OrderSortField.ORDER_DATE,
            sortDirection = SortDirection.ASC
        )

        // When
        val page1 = orderRepository.searchOrders(criteria1)
        val page2 = orderRepository.searchOrders(criteria2)

        // Then
        Assertions.assertEquals(2, page1.size)
        Assertions.assertEquals(1, page2.size)
        println("✓ Pagination works: Page 1 has ${page1.size} orders, Page 2 has ${page2.size} orders")
    }

    @Test
    fun `should count orders matching criteria`() {
        // Given
        val criteria = OrderSearchCriteria(
            customerName = "John"
        )

        // When
        val count = orderRepository.countOrders(criteria)

        // Then
        Assertions.assertEquals(2L, count)
        println("✓ Counted $count orders for customer 'John'")
    }

    @Test
    fun `should handle empty search results`() {
        // Given
        val criteria = OrderSearchCriteria(
            customerName = "NonExistentCustomer"
        )

        // When
        val results = orderRepository.searchOrders(criteria)
        val count = orderRepository.countOrders(criteria)

        // Then
        Assertions.assertEquals(0, results.size)
        Assertions.assertEquals(0L, count)
        println("✓ Empty search results handled correctly")
    }

    @Test
    fun `should sort by different fields`() {
        // Given - Sort by customer name descending
        val criteria = OrderSearchCriteria(
            sortBy = OrderSortField.CUSTOMER_NAME,
            sortDirection = SortDirection.DESC
        )

        // When
        val results = orderRepository.searchOrders(criteria)

        // Then
        Assertions.assertEquals(3, results.size)
        Assertions.assertEquals("John Doe", results[0].customerName)
        Assertions.assertEquals("Jane Smith", results[2].customerName)
        println("✓ Sorting by customer name (DESC) works")
        results.forEach { println("  - ${it.customerName}") }
    }
}
