package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.entity.OrderItem
import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.querydsl.TestDatabase
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryDslOrderSearchTest {

    private lateinit var em: EntityManager
    private lateinit var orderRepository: QueryDslOrderRepository
    private lateinit var customerRepository: QueryDslCustomerRepository
    private lateinit var productRepository: QueryDslProductRepository

    @BeforeAll
    fun setupAll() {
        em = TestDatabase.createEntityManager()
        orderRepository = QueryDslOrderRepository(em)
        customerRepository = QueryDslCustomerRepository(em)
        productRepository = QueryDslProductRepository(em)
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(em)
        setupTestData()
    }

    @AfterAll
    fun teardown() {
        em.close()
    }

    private fun setupTestData() {
        em.transaction.begin()

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

        // Order Items (using native SQL for simplicity)
        em.createNativeQuery(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 1L)
            setParameter(2, 1L)
            setParameter(3, 1L)
            setParameter(4, 1)
            setParameter(5, 1000.toBigDecimal())
        }.executeUpdate()

        em.createNativeQuery(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 2L)
            setParameter(2, 1L)
            setParameter(3, 2L)
            setParameter(4, 1)
            setParameter(5, 50.toBigDecimal())
        }.executeUpdate()

        em.createNativeQuery(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 3L)
            setParameter(2, 2L)
            setParameter(3, 3L)
            setParameter(4, 1)
            setParameter(5, 30.toBigDecimal())
        }.executeUpdate()

        em.createNativeQuery(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 4L)
            setParameter(2, 3L)
            setParameter(3, 2L)
            setParameter(4, 1)
            setParameter(5, 50.toBigDecimal())
        }.executeUpdate()

        // Payments (using native SQL)
        em.createNativeQuery(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 1L)
            setParameter(2, 1L)
            setParameter(3, 1050.toBigDecimal())
            setParameter(4, LocalDateTime.of(2024, 10, 1, 10, 5))
            setParameter(5, PaymentMethod.CREDIT_CARD.name)
            setParameter(6, PaymentStatus.COMPLETED.name)
        }.executeUpdate()

        em.createNativeQuery(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)"
        ).apply {
            setParameter(1, 3L)
            setParameter(2, 3L)
            setParameter(3, 50.toBigDecimal())
            setParameter(4, LocalDateTime.of(2024, 11, 1, 9, 20))
            setParameter(5, PaymentMethod.BANK_TRANSFER.name)
            setParameter(6, PaymentStatus.COMPLETED.name)
        }.executeUpdate()

        em.transaction.commit()
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
        Assertions.assertTrue(results[0].productNames.any { it.contains("Laptop") })
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
        Assertions.assertTrue(results.isNotEmpty())
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
