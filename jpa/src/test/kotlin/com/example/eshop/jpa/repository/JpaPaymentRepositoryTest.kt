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

    @Test
    fun `should find premium customers using UNION with JPA`() {
        // Given
        setupPremiumCustomersData()
        em.flush()

        // When
        val minTotalAmount = Money(1000.00)
        val minPaymentCount = 3L
        val premiumCustomers = repository.findPremiumCustomersWithUnion(minTotalAmount, minPaymentCount)

        // Then
        Assertions.assertTrue(premiumCustomers.isNotEmpty())
        println("✓ [JPA] Premium customers (UNION of high-value and frequent payers):")
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

    private fun setupPremiumCustomersData() {
        val customerRepo = JpaCustomerRepository(em)
        
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
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (1, 1, '2024-06-01 10:00:00', 800.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (2, 1, '2024-06-02 10:00:00', 700.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (1, 1, 800.00, '2024-06-01 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (2, 2, 700.00, '2024-06-02 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()

        // Customer 2: 4 small payments (total 400.00)
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (3, 2, '2024-06-03 10:00:00', 100.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (4, 2, '2024-06-04 10:00:00', 100.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (5, 2, '2024-06-05 10:00:00', 100.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (6, 2, '2024-06-06 10:00:00', 100.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (3, 3, 100.00, '2024-06-03 10:05:00', 'PAYPAL', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (4, 4, 100.00, '2024-06-04 10:05:00', 'PAYPAL', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (5, 5, 100.00, '2024-06-05 10:05:00', 'PAYPAL', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (6, 6, 100.00, '2024-06-06 10:05:00', 'PAYPAL', 'COMPLETED')").executeUpdate()

        // Customer 3: 5 medium payments (total 2000.00)
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (7, 3, '2024-06-07 10:00:00', 400.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (8, 3, '2024-06-08 10:00:00', 400.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (9, 3, '2024-06-09 10:00:00', 400.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (10, 3, '2024-06-10 10:00:00', 400.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (11, 3, '2024-06-11 10:00:00', 400.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (7, 7, 400.00, '2024-06-07 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (8, 8, 400.00, '2024-06-08 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (9, 9, 400.00, '2024-06-09 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (10, 10, 400.00, '2024-06-10 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (11, 11, 400.00, '2024-06-11 10:05:00', 'CREDIT_CARD', 'COMPLETED')").executeUpdate()

        // Customer 4: 1 small payment (total 50.00)
        em.createNativeQuery("INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (12, 4, '2024-06-12 10:00:00', 50.00, 'DELIVERED')").executeUpdate()
        em.createNativeQuery("INSERT INTO payment (id, order_id, amount, payment_date, method, status) VALUES (12, 12, 50.00, '2024-06-12 10:05:00', 'BANK_TRANSFER', 'COMPLETED')").executeUpdate()
    }
}

