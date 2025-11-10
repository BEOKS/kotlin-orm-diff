package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaCustomerRepositoryTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repository: JpaCustomerRepository

    @BeforeAll
    fun setupAll() {
        emf = Persistence.createEntityManagerFactory("test-unit")
    }

    @BeforeEach
    fun setup() {
        em = emf.createEntityManager()
        repository = JpaCustomerRepository(em)
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
    fun `should save and find customer by id`() {
        // Given
        val customer = Customer(
            id = CustomerId(1L),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )

        // When
        repository.save(customer)
        em.flush()
        val found = repository.findById(CustomerId(1L))

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertEquals(customer.name, found?.name)
        Assertions.assertEquals(customer.email, found?.email)
        println("✓ Customer saved and retrieved: ${found?.name}")
    }

    @Test
    fun `should find all customers`() {
        // Given
        val customer1 = Customer(
            id = CustomerId(1L),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        val customer2 = Customer(
            id = CustomerId(2L),
            name = "Jane Smith",
            email = "jane@example.com",
            address = "456 Oak Ave",
            registeredDate = LocalDate.now()
        )

        // When
        repository.save(customer1)
        repository.save(customer2)
        em.flush()
        val all = repository.findAll()

        // Then
        Assertions.assertEquals(2, all.size)
        println("✓ Found ${all.size} customers")
    }

    @Test
    fun `should update customer`() {
        // Given
        val customer = Customer(
            id = CustomerId(1L),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        repository.save(customer)
        em.flush()

        // When
        val updated = customer.copy(name = "John Updated")
        repository.update(updated)
        em.flush()
        val found = repository.findById(CustomerId(1L))

        // Then
        Assertions.assertEquals("John Updated", found?.name)
        println("✓ Customer updated: ${found?.name}")
    }

    @Test
    fun `should delete customer`() {
        // Given
        val customer = Customer(
            id = CustomerId(1L),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        repository.save(customer)
        em.flush()

        // When
        val deleted = repository.delete(CustomerId(1L))
        em.flush()
        val found = repository.findById(CustomerId(1L))

        // Then
        Assertions.assertTrue(deleted)
        Assertions.assertNull(found)
        println("✓ Customer deleted successfully")
    }

    @Test
    fun `should find customers with high value orders`() {
        // Given
        setupCustomersWithOrders()
        em.flush()

        // When
        val customers = repository.findCustomersWithHighValueOrders(Money(1000.0))

        // Then
        Assertions.assertTrue(customers.isNotEmpty())
        println("✓ Found ${customers.size} customers with high value orders")
        customers.forEach { println("  - ${it.name} (${it.email})") }
    }

    private fun setupCustomersWithOrders() {
        repository.save(Customer(
            id = CustomerId(1L),
            name = "High Value Customer",
            email = "high@example.com",
            address = "789 Luxury Blvd",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (2, 1, '2024-06-15 14:30:00', 800.00, 'DELIVERED')
        """).executeUpdate()
    }
}

