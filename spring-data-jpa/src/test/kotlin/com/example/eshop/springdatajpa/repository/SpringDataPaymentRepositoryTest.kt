package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.valueobject.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@DataJpaTest
@ActiveProfiles("test")
@Import(SpringDataPaymentRepository::class)
class SpringDataPaymentRepositoryTest {

    @Autowired
    private lateinit var repository: SpringDataPaymentRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM order_item")
        jdbcTemplate.execute("DELETE FROM payment")
        jdbcTemplate.execute("DELETE FROM orders")
        jdbcTemplate.execute("DELETE FROM product")
        jdbcTemplate.execute("DELETE FROM customer")
    }

    @Test
    fun `should save and find payment by id`() {
        // Given
        setupCustomerAndOrder()
        val payment = Payment(
            id = PaymentId(1L),
            orderId = OrderId(1L),
            amount = Money(500.0),
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
        println("✓ Payment saved and retrieved: ${found?.amount}")
    }

    @Test
    fun `should find all payments`() {
        // Given
        setupCustomerAndOrder()
        val payment1 = Payment(
            id = PaymentId(1L),
            orderId = OrderId(1L),
            amount = Money(500.0),
            paymentDate = LocalDateTime.now(),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.COMPLETED
        )
        val payment2 = Payment(
            id = PaymentId(2L),
            orderId = OrderId(1L),
            amount = Money(300.0),
            paymentDate = LocalDateTime.now(),
            method = PaymentMethod.PAYPAL,
            status = PaymentStatus.COMPLETED
        )

        // When
        repository.save(payment1)
        repository.save(payment2)
        val all = repository.findAll()

        // Then
        Assertions.assertEquals(2, all.size)
        println("✓ Found ${all.size} payments")
    }

    @Test
    fun `should calculate customer order statistics`() {
        // Given
        setupCustomersWithPayments()

        // When
        val statistics = repository.calculateCustomerOrderStatistics()

        // Then
        Assertions.assertTrue(statistics.isNotEmpty())
        println("✓ Customer order statistics:")
        statistics.forEach { stat ->
            println("  - ${stat.customerName}: total=${stat.totalPayments}, avg=${stat.averagePayment}, count=${stat.paymentCount}")
        }
    }

    @Test
    fun `should calculate payment method statistics`() {
        // Given
        setupCustomersWithPayments()

        // When
        val statistics = repository.calculatePaymentMethodStatistics()

        // Then
        Assertions.assertTrue(statistics.isNotEmpty())
        println("✓ Payment method statistics:")
        statistics.forEach { (method, stats) ->
            println("  - $method: total=${stats.totalAmount}, count=${stats.count}, avg=${stats.averageAmount}")
        }
    }

    @Test
    fun `should find payment by order id`() {
        // Given
        setupCustomerAndOrder()
        val payment = Payment(
            id = PaymentId(1L),
            orderId = OrderId(1L),
            amount = Money(500.0),
            paymentDate = LocalDateTime.now(),
            method = PaymentMethod.CREDIT_CARD,
            status = PaymentStatus.COMPLETED
        )
        repository.save(payment)

        // When
        val found = repository.findByOrderId(OrderId(1L))

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertEquals(payment.amount, found?.amount)
        println("✓ Payment found by order id: ${found?.amount}")
    }

    @Test
    fun `should find failed payments with details`() {
        // Given
        setupPaymentsWithFailures()

        // When
        val failedPayments = repository.findFailedPaymentsWithDetails()

        // Then
        Assertions.assertTrue(failedPayments.isNotEmpty())
        println("✓ Failed payments:")
        failedPayments.forEach { detail ->
            println("  - ${detail.customerName} (${detail.customerEmail}): ${detail.payment.amount}")
        }
    }

    @Test
    fun `should find premium customers with union`() {
        // Given
        setupCustomersWithPayments()

        // When
        val premiumCustomers = repository.findPremiumCustomersWithUnion(
            Money(500.0),
            2L
        )

        // Then
        Assertions.assertTrue(premiumCustomers.isNotEmpty())
        println("✓ Premium customers:")
        premiumCustomers.forEach { customer ->
            println("  - ${customer.customerName} (${customer.customerType}): total=${customer.totalPaymentAmount}, count=${customer.paymentCount}")
        }
    }

    private fun setupCustomerAndOrder() {
        jdbcTemplate.update(
            "INSERT INTO customer (id, name, email, address, registered_date) VALUES (?, ?, ?, ?, ?)",
            1L, "John Doe", "john@example.com", "123 Main St", "2024-01-01"
        )
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            1L, 1L, "2024-06-01 10:00:00", 500.00, "DELIVERED"
        )
    }

    private fun setupCustomersWithPayments() {
        // Customer 1
        jdbcTemplate.update(
            "INSERT INTO customer (id, name, email, address, registered_date) VALUES (?, ?, ?, ?, ?)",
            1L, "John Doe", "john@example.com", "123 Main St", "2024-01-01"
        )
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            1L, 1L, "2024-06-01 10:00:00", 500.00, "DELIVERED"
        )
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            2L, 1L, "2024-06-15 14:30:00", 800.00, "DELIVERED"
        )
        jdbcTemplate.update(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)",
            1L, 1L, 500.00, "2024-06-01 11:00:00", "CREDIT_CARD", "COMPLETED"
        )
        jdbcTemplate.update(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)",
            2L, 2L, 800.00, "2024-06-15 15:00:00", "PAYPAL", "COMPLETED"
        )

        // Customer 2
        jdbcTemplate.update(
            "INSERT INTO customer (id, name, email, address, registered_date) VALUES (?, ?, ?, ?, ?)",
            2L, "Jane Smith", "jane@example.com", "456 Oak Ave", "2024-02-01"
        )
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            3L, 2L, "2024-07-01 09:00:00", 300.00, "DELIVERED"
        )
        jdbcTemplate.update(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)",
            3L, 3L, 300.00, "2024-07-01 10:00:00", "CREDIT_CARD", "COMPLETED"
        )
    }

    private fun setupPaymentsWithFailures() {
        setupCustomerAndOrder()
        
        jdbcTemplate.update(
            "INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (?, ?, ?, ?, ?, ?)",
            1L, 1L, 500.00, "2024-06-01 11:00:00", "CREDIT_CARD", "FAILED"
        )
    }
}

