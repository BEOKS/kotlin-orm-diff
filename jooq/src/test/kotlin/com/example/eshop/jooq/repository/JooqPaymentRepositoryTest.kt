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

    @Test
    fun `should find premium customers using UNION with jOOQ`() {
        // Given
        setupPremiumCustomersData()

        // When
        val minTotalAmount = Money(1000.00)
        val minPaymentCount = 3L
        val premiumCustomers = repository.findPremiumCustomersWithUnion(minTotalAmount, minPaymentCount)

        // Then
        Assertions.assertTrue(premiumCustomers.isNotEmpty())
        println("✓ [jOOQ] Premium customers (UNION of high-value and frequent payers):")
        premiumCustomers.forEach { customer ->
            println("  - ${customer.customerName} (${customer.customerEmail})")
            println("    Type: ${customer.customerType}")
            println("    Total Payments: $${customer.totalPaymentAmount}")
            println("    Payment Count: ${customer.paymentCount}")
            println()
        }
        
        // Verify that we have customers of different types
        val hasHighValue = premiumCustomers.any { it.customerType == com.example.eshop.domain.repository.PremiumCustomerType.HIGH_VALUE || it.customerType == com.example.eshop.domain.repository.PremiumCustomerType.BOTH }
        val hasFrequentPayer = premiumCustomers.any { it.customerType == com.example.eshop.domain.repository.PremiumCustomerType.FREQUENT_PAYER || it.customerType == com.example.eshop.domain.repository.PremiumCustomerType.BOTH }
        
        Assertions.assertTrue(hasHighValue, "Should have high-value customers")
        Assertions.assertTrue(hasFrequentPayer, "Should have frequent payers")
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

    private fun setupPremiumCustomersData() {
        // Customer 1: High-value payer (총 금액 높음, 횟수 적음)
        dsl.execute("INSERT INTO customer (id, name, email, address, registered_date) VALUES (1, 'High-Value Customer', 'highvalue@example.com', '123 Luxury St', '2024-01-01')")
        
        // Customer 2: Frequent payer (총 금액 낮음, 횟수 많음)
        dsl.execute("INSERT INTO customer (id, name, email, address, registered_date) VALUES (2, 'Frequent Payer', 'frequent@example.com', '456 Regular Ave', '2024-01-15')")
        
        // Customer 3: Both (총 금액 높음, 횟수 많음)
        dsl.execute("INSERT INTO customer (id, name, email, address, registered_date) VALUES (3, 'Premium Customer', 'premium@example.com', '789 Elite Blvd', '2024-02-01')")
        
        // Customer 4: Regular (둘 다 해당 안됨)
        dsl.execute("INSERT INTO customer (id, name, email, address, registered_date) VALUES (4, 'Regular Customer', 'regular@example.com', '321 Normal St', '2024-02-15')")

        // Customer 1: 2 large payments (total 1500.00)
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (1, 1, '2024-06-01 10:00:00', 800.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (2, 1, '2024-06-02 10:00:00', 700.00, 'DELIVERED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (1, 1, 800.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (2, 2, 700.00, '2024-06-02 10:05:00', 'CREDIT_CARD', 'COMPLETED')")

        // Customer 2: 4 small payments (total 400.00)
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (3, 2, '2024-06-03 10:00:00', 100.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (4, 2, '2024-06-04 10:00:00', 100.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (5, 2, '2024-06-05 10:00:00', 100.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (6, 2, '2024-06-06 10:00:00', 100.00, 'DELIVERED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (3, 3, 100.00, '2024-06-03 10:05:00', 'PAYPAL', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (4, 4, 100.00, '2024-06-04 10:05:00', 'PAYPAL', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (5, 5, 100.00, '2024-06-05 10:05:00', 'PAYPAL', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (6, 6, 100.00, '2024-06-06 10:05:00', 'PAYPAL', 'COMPLETED')")

        // Customer 3: 5 medium payments (total 2000.00)
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (7, 3, '2024-06-07 10:00:00', 400.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (8, 3, '2024-06-08 10:00:00', 400.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (9, 3, '2024-06-09 10:00:00', 400.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (10, 3, '2024-06-10 10:00:00', 400.00, 'DELIVERED')")
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (11, 3, '2024-06-11 10:00:00', 400.00, 'DELIVERED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (7, 7, 400.00, '2024-06-07 10:05:00', 'CREDIT_CARD', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (8, 8, 400.00, '2024-06-08 10:05:00', 'CREDIT_CARD', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (9, 9, 400.00, '2024-06-09 10:05:00', 'CREDIT_CARD', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (10, 10, 400.00, '2024-06-10 10:05:00', 'CREDIT_CARD', 'COMPLETED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (11, 11, 400.00, '2024-06-11 10:05:00', 'CREDIT_CARD', 'COMPLETED')")

        // Customer 4: 1 small payment (total 50.00) - should not appear in results
        dsl.execute("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (12, 4, '2024-06-12 10:00:00', 50.00, 'DELIVERED')")
        dsl.execute("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (12, 12, 50.00, '2024-06-12 10:05:00', 'BANK_TRANSFER', 'COMPLETED')")
    }
}

