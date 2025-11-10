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
}

