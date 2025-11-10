package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.valueobject.*
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaPaymentRepositoryTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repository: JpaPaymentRepository

    @BeforeAll
    fun setupAll() {
        emf = Persistence.createEntityManagerFactory("test-unit")
    }

    @BeforeEach
    fun setup() {
        em = emf.createEntityManager()
        repository = JpaPaymentRepository(em)
        em.transaction.begin()
    }

    @AfterEach
    fun teardown() {
        if (em.transaction.isActive) {
            em.transaction.rollback()
        }
        em.close()
    }

    @AfterAll
    fun teardownAll() {
        emf.close()
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
        em.flush()
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
        em.flush()

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
        em.flush()

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
        em.flush()

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
        JpaCustomerRepository(em).save(Customer(
            id = CustomerId(1L),
            name = "Test Customer",
            email = "test@example.com",
            address = "123 St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))
        
        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """).executeUpdate()
    }

    private fun setupCompleteOrdersWithPayments() {
        val customerRepo = JpaCustomerRepository(em)
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

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (2, 1, '2024-06-15 14:00:00', 300.00, 'DELIVERED')
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (3, 2, '2024-06-20 11:00:00', 750.00, 'DELIVERED')
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (1, 1, 500.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'COMPLETED')
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (2, 2, 300.00, '2024-06-15 14:05:00', 'PAYPAL', 'COMPLETED')
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (3, 3, 750.00, '2024-06-20 11:05:00', 'CREDIT_CARD', 'COMPLETED')
        """).executeUpdate()
    }

    private fun setupFailedPayments() {
        setupCustomerAndOrder()
        
        em.createNativeQuery("""
            INSERT INTO payment (id, order_id, amount, payment_date, method, status) 
            VALUES (1, 1, 500.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'FAILED')
        """).executeUpdate()
    }
}

