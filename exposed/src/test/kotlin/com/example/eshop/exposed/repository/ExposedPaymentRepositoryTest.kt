package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.valueobject.*
import com.example.eshop.exposed.TestDatabase
import com.example.eshop.exposed.table.Orders
import com.example.eshop.exposed.table.Payments
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedPaymentRepositoryTest {

    private lateinit var db: Database
    private lateinit var repository: ExposedPaymentRepository

    @BeforeAll
    fun setupAll() {
        db = TestDatabase.connect()
        TestDatabase.initSchema(db)
        repository = ExposedPaymentRepository()
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(db)
    }

    @Test
    fun `should save and find payment by id`() {
        transaction(db) {
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
    }

    @Test
    fun `should calculate customer order statistics`() {
        transaction(db) {
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
    }

    @Test
    fun `should calculate payment method statistics`() {
        transaction(db) {
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
    }

    @Test
    fun `should find failed payments with details`() {
        transaction(db) {
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
    }

    @Test
    fun `should find premium customers using UNION with Exposed`() {
        transaction(db) {
            // Given
            setupPremiumCustomersData()

            // When
            val minTotalAmount = Money(1000.00)
            val minPaymentCount = 3L
            val premiumCustomers = repository.findPremiumCustomersWithUnion(minTotalAmount, minPaymentCount)

            // Then
            Assertions.assertTrue(premiumCustomers.isNotEmpty())
            println("✓ [Exposed] Premium customers (UNION of high-value and frequent payers):")
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
    }

    private fun setupCustomerAndOrder() {
        ExposedCustomerRepository().save(Customer(
            id = CustomerId(1L),
            name = "Test Customer",
            email = "test@example.com",
            address = "123 St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        Orders.insert {
            it[id] = 1L
            it[customerId] = 1L
            it[orderDate] = LocalDateTime.parse("2024-06-01T10:00:00")
            it[totalAmount] = java.math.BigDecimal("500.00")
            it[status] = "DELIVERED"
        }
    }

    private fun setupCompleteOrdersWithPayments() {
        val customerRepo = ExposedCustomerRepository()
        customerRepo.save(Customer(
            id = CustomerId(1L),
            name = "Alice Johnson",
            email = "alice@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))
        customerRepo.save(Customer(
            id = CustomerId(2L),
            name = "Bob Smith",
            email = "bob@example.com",
            address = "456 Oak Ave",
            registeredDate = LocalDate.parse("2024-01-15")
        ))

        Orders.insert {
            it[id] = 1L
            it[customerId] = 1L
            it[orderDate] = LocalDateTime.parse("2024-06-01T10:00:00")
            it[totalAmount] = java.math.BigDecimal("500.00")
            it[status] = "DELIVERED"
        }

        Orders.insert {
            it[id] = 2L
            it[customerId] = 1L
            it[orderDate] = LocalDateTime.parse("2024-06-15T14:00:00")
            it[totalAmount] = java.math.BigDecimal("300.00")
            it[status] = "DELIVERED"
        }

        Orders.insert {
            it[id] = 3L
            it[customerId] = 2L
            it[orderDate] = LocalDateTime.parse("2024-06-20T11:00:00")
            it[totalAmount] = java.math.BigDecimal("750.00")
            it[status] = "DELIVERED"
        }

        Payments.insert {
            it[id] = 1L
            it[orderId] = 1L
            it[amount] = java.math.BigDecimal("500.00")
            it[paymentDate] = LocalDateTime.parse("2024-06-01T10:05:00")
            it[method] = "CREDIT_CARD"
            it[status] = "COMPLETED"
        }

        Payments.insert {
            it[id] = 2L
            it[orderId] = 2L
            it[amount] = java.math.BigDecimal("300.00")
            it[paymentDate] = LocalDateTime.parse("2024-06-15T14:05:00")
            it[method] = "PAYPAL"
            it[status] = "COMPLETED"
        }

        Payments.insert {
            it[id] = 3L
            it[orderId] = 3L
            it[amount] = java.math.BigDecimal("750.00")
            it[paymentDate] = LocalDateTime.parse("2024-06-20T11:05:00")
            it[method] = "CREDIT_CARD"
            it[status] = "COMPLETED"
        }
    }

    private fun setupFailedPayments() {
        setupCustomerAndOrder()

        Payments.insert {
            it[id] = 1L
            it[orderId] = 1L
            it[amount] = java.math.BigDecimal("500.00")
            it[paymentDate] = LocalDateTime.parse("2024-06-01T10:05:00")
            it[method] = "CREDIT_CARD"
            it[status] = "FAILED"
        }
    }

    private fun setupPremiumCustomersData() {
        val customerRepo = ExposedCustomerRepository()
        
        // Customer 1: High-value payer
        customerRepo.save(Customer(
            id = CustomerId(1L),
            name = "High-Value Customer",
            email = "highvalue@example.com",
            address = "123 Luxury St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))
        
        // Customer 2: Frequent payer
        customerRepo.save(Customer(
            id = CustomerId(2L),
            name = "Frequent Payer",
            email = "frequent@example.com",
            address = "456 Regular Ave",
            registeredDate = LocalDate.parse("2024-01-15")
        ))
        
        // Customer 3: Both
        customerRepo.save(Customer(
            id = CustomerId(3L),
            name = "Premium Customer",
            email = "premium@example.com",
            address = "789 Elite Blvd",
            registeredDate = LocalDate.parse("2024-02-01")
        ))
        
        // Customer 4: Regular
        customerRepo.save(Customer(
            id = CustomerId(4L),
            name = "Regular Customer",
            email = "regular@example.com",
            address = "321 Normal St",
            registeredDate = LocalDate.parse("2024-02-15")
        ))

        // Customer 1: 2 large payments (total 1500.00)
        Orders.insert { it[id] = 1L; it[customerId] = 1L; it[orderDate] = LocalDateTime.parse("2024-06-01T10:00:00"); it[totalAmount] = java.math.BigDecimal("800.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 2L; it[customerId] = 1L; it[orderDate] = LocalDateTime.parse("2024-06-02T10:00:00"); it[totalAmount] = java.math.BigDecimal("700.00"); it[status] = "DELIVERED" }
        Payments.insert { it[id] = 1L; it[orderId] = 1L; it[amount] = java.math.BigDecimal("800.00"); it[paymentDate] = LocalDateTime.parse("2024-06-01T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 2L; it[orderId] = 2L; it[amount] = java.math.BigDecimal("700.00"); it[paymentDate] = LocalDateTime.parse("2024-06-02T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }

        // Customer 2: 4 small payments (total 400.00)
        Orders.insert { it[id] = 3L; it[customerId] = 2L; it[orderDate] = LocalDateTime.parse("2024-06-03T10:00:00"); it[totalAmount] = java.math.BigDecimal("100.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 4L; it[customerId] = 2L; it[orderDate] = LocalDateTime.parse("2024-06-04T10:00:00"); it[totalAmount] = java.math.BigDecimal("100.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 5L; it[customerId] = 2L; it[orderDate] = LocalDateTime.parse("2024-06-05T10:00:00"); it[totalAmount] = java.math.BigDecimal("100.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 6L; it[customerId] = 2L; it[orderDate] = LocalDateTime.parse("2024-06-06T10:00:00"); it[totalAmount] = java.math.BigDecimal("100.00"); it[status] = "DELIVERED" }
        Payments.insert { it[id] = 3L; it[orderId] = 3L; it[amount] = java.math.BigDecimal("100.00"); it[paymentDate] = LocalDateTime.parse("2024-06-03T10:05:00"); it[method] = "PAYPAL"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 4L; it[orderId] = 4L; it[amount] = java.math.BigDecimal("100.00"); it[paymentDate] = LocalDateTime.parse("2024-06-04T10:05:00"); it[method] = "PAYPAL"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 5L; it[orderId] = 5L; it[amount] = java.math.BigDecimal("100.00"); it[paymentDate] = LocalDateTime.parse("2024-06-05T10:05:00"); it[method] = "PAYPAL"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 6L; it[orderId] = 6L; it[amount] = java.math.BigDecimal("100.00"); it[paymentDate] = LocalDateTime.parse("2024-06-06T10:05:00"); it[method] = "PAYPAL"; it[status] = "COMPLETED" }

        // Customer 3: 5 medium payments (total 2000.00)
        Orders.insert { it[id] = 7L; it[customerId] = 3L; it[orderDate] = LocalDateTime.parse("2024-06-07T10:00:00"); it[totalAmount] = java.math.BigDecimal("400.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 8L; it[customerId] = 3L; it[orderDate] = LocalDateTime.parse("2024-06-08T10:00:00"); it[totalAmount] = java.math.BigDecimal("400.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 9L; it[customerId] = 3L; it[orderDate] = LocalDateTime.parse("2024-06-09T10:00:00"); it[totalAmount] = java.math.BigDecimal("400.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 10L; it[customerId] = 3L; it[orderDate] = LocalDateTime.parse("2024-06-10T10:00:00"); it[totalAmount] = java.math.BigDecimal("400.00"); it[status] = "DELIVERED" }
        Orders.insert { it[id] = 11L; it[customerId] = 3L; it[orderDate] = LocalDateTime.parse("2024-06-11T10:00:00"); it[totalAmount] = java.math.BigDecimal("400.00"); it[status] = "DELIVERED" }
        Payments.insert { it[id] = 7L; it[orderId] = 7L; it[amount] = java.math.BigDecimal("400.00"); it[paymentDate] = LocalDateTime.parse("2024-06-07T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 8L; it[orderId] = 8L; it[amount] = java.math.BigDecimal("400.00"); it[paymentDate] = LocalDateTime.parse("2024-06-08T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 9L; it[orderId] = 9L; it[amount] = java.math.BigDecimal("400.00"); it[paymentDate] = LocalDateTime.parse("2024-06-09T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 10L; it[orderId] = 10L; it[amount] = java.math.BigDecimal("400.00"); it[paymentDate] = LocalDateTime.parse("2024-06-10T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }
        Payments.insert { it[id] = 11L; it[orderId] = 11L; it[amount] = java.math.BigDecimal("400.00"); it[paymentDate] = LocalDateTime.parse("2024-06-11T10:05:00"); it[method] = "CREDIT_CARD"; it[status] = "COMPLETED" }

        // Customer 4: 1 small payment (total 50.00)
        Orders.insert { it[id] = 12L; it[customerId] = 4L; it[orderDate] = LocalDateTime.parse("2024-06-12T10:00:00"); it[totalAmount] = java.math.BigDecimal("50.00"); it[status] = "DELIVERED" }
        Payments.insert { it[id] = 12L; it[orderId] = 12L; it[amount] = java.math.BigDecimal("50.00"); it[paymentDate] = LocalDateTime.parse("2024-06-12T10:05:00"); it[method] = "BANK_TRANSFER"; it[status] = "COMPLETED" }
    }
}

