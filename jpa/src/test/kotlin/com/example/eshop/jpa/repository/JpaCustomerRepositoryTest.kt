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
            id = CustomerId(1), // Temporary ID, will be replaced
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )

        // When
        val saved = repository.save(customer)
        em.flush()
        val found = repository.findById(saved.id)

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertTrue(saved.id.value > 0)
        Assertions.assertEquals(saved.name, found?.name)
        Assertions.assertEquals(saved.email, found?.email)
        println("Customer saved and retrieved: ${found?.name} (ID: ${saved.id.value})")
    }

    @Test
    fun `should find all customers`() {
        // Given
        val customer1 = Customer(
            id = CustomerId(1),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        val customer2 = Customer(
            id = CustomerId(2),
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
        println("Found ${all.size} customers")
    }

    @Test
    fun `should update customer`() {
        // Given
        val customer = Customer(
            id = CustomerId(1),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        val saved = repository.save(customer)
        em.flush()

        // When
        val updated = saved.copy(name = "John Updated")
        repository.update(updated)
        em.flush()
        val found = repository.findById(saved.id)

        // Then
        Assertions.assertEquals("John Updated", found?.name)
        println("Customer updated: ${found?.name}")
    }

    @Test
    fun `should delete customer`() {
        // Given
        val customer = Customer(
            id = CustomerId(1),
            name = "John Doe",
            email = "john@example.com",
            address = "123 Main St",
            registeredDate = LocalDate.now()
        )
        val saved = repository.save(customer)
        em.flush()

        // When
        val deleted = repository.delete(saved.id)
        em.flush()
        val found = repository.findById(saved.id)

        // Then
        Assertions.assertTrue(deleted)
        Assertions.assertNull(found)
        println("Customer deleted successfully")
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
        println("Found ${customers.size} customers with high value orders")
        customers.forEach { println("  - ${it.name} (${it.email})") }
    }

    private fun setupCustomersWithOrders() {
        val saved = repository.save(Customer(
            id = CustomerId(1),
            name = "High Value Customer",
            email = "high@example.com",
            address = "789 Luxury Blvd",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status, version)
            VALUES (1, ${saved.id.value}, '2024-06-01 10:00:00', 500.00, 'DELIVERED', 0)
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status, version)
            VALUES (2, ${saved.id.value}, '2024-06-15 14:30:00', 800.00, 'DELIVERED', 0)
        """).executeUpdate()
    }
}
