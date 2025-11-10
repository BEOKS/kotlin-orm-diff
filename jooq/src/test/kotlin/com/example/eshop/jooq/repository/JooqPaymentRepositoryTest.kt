package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.TestDatabase
import org.jooq.DSLContext
import org.junit.jupiter.api.*
import java.sql.Connection
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqPaymentRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var dsl: DSLContext
    private lateinit var repository: JooqPaymentRepository

    @BeforeAll
    fun setupAll() {
        connection = TestDatabase.createConnection()
        dsl = TestDatabase.createDSLContext(connection)
        TestDatabase.initSchema(connection)
        repository = JooqPaymentRepository(dsl)
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(dsl)
    }

    @AfterAll
    fun teardown() {
        connection.close()
    }

    @Test
    fun `should save and find payment by id`() {
        // Given
        setupCustomerAndOrder()
        val payment = Payment(
            id = PaymentId(1L),
            orderId = OrderId(1L),
            amount = Money(500.00),
            paymentDate = LocalDateTime.now(),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.COMPLETED
        )

        // When
        repository.save(payment)
        val found = repository.findById(PaymentId(1L))

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertEquals(payment.amount, found?.amount)
        Assertions.assertEquals(payment.method, found?.method)
        println("✓ Payment saved and retrieved: $${found?.amount} via ${found?.method}")
    }

    @Test
    fun `should calculate customer order statistics`() {
        // Given
        setupCompleteOrdersWithPayments()

        // When
        val statistics = repository.calculateCustomerOrderStatistics()

        // Then
        Assertions.assertTrue(statistics.isNotEmpty())
        println("✓ Customer order statistics:")
        statistics.forEach { stat ->
            println("  - ${stat.customerName}: ${stat.paymentCount} payments, Total: $${stat.totalPayments}, Avg: $${stat.averagePayment}")
        }
    }

    @Test
    fun `should calculate payment method statistics`() {
        // Given
        setupCompleteOrdersWithPayments()

        // When
        val methodStats = repository.calculatePaymentMethodStatistics()

        // Then
        Assertions.assertTrue(methodStats.isNotEmpty())
        println("✓ Payment method statistics:")
        methodStats.forEach { (method, stats) ->
            println("  - $method: ${stats.count} transactions, Total: $${stats.totalAmount}, Avg: $${stats.averageAmount}")
        }
    }

    @Test
    fun `should find failed payments with details`() {
        // Given
        setupFailedPayments()

        // When
        val failedPayments = repository.findFailedPaymentsWithDetails()

        // Then
        Assertions.assertTrue(failedPayments.isNotEmpty())
        println("✓ Failed payments:")
        failedPayments.forEach { detail ->
            println("  - Customer: ${detail.customerName} (${detail.customerEmail})")
            println("    Amount: $${detail.payment.amount}, Method: ${detail.payment.method}")
            println("    Order Date: ${detail.orderDate}")
        }
    }

    private fun setupCustomerAndOrder() {
        dsl.execute("""
            INSERT INTO customer (id, name, email, address, registered_date) 
            VALUES (1, 'Test Customer', 'test@example.com', '123 St', '2024-01-01')
        """)
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """)
    }

    private fun setupCompleteOrdersWithPayments() {
        dsl.execute("""
            INSERT INTO customer (id, name, email, address, registered_date) 
            VALUES (1, 'Alice Johnson', 'alice@example.com', '123 Main St', '2024-01-01')
        """)
        dsl.execute("""
            INSERT INTO customer (id, name, email, address, registered_date) 
            VALUES (2, 'Bob Smith', 'bob@example.com', '456 Oak Ave', '2024-01-15')
        """)

        // Orders for Alice
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """)
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (2, 1, '2024-06-15 14:00:00', 300.00, 'DELIVERED')
        """)

        // Orders for Bob
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (3, 2, '2024-06-20 11:00:00', 750.00, 'DELIVERED')
        """)

        // Payments
        dsl.execute("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (1, 1, 500.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'COMPLETED')
        """)
        dsl.execute("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (2, 2, 300.00, '2024-06-15 14:05:00', 'PAYPAL', 'COMPLETED')
        """)
        dsl.execute("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (3, 3, 750.00, '2024-06-20 11:05:00', 'CREDIT_CARD', 'COMPLETED')
        """)
    }

    private fun setupFailedPayments() {
        setupCustomerAndOrder()
        
        dsl.execute("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (1, 1, 500.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'FAILED')
        """)
    }
}

