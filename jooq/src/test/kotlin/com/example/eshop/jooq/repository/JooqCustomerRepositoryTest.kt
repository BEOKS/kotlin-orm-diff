package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.jooq.TestDatabase
import org.jooq.DSLContext
import org.junit.jupiter.api.*
import java.sql.Connection
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqCustomerRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var dsl: DSLContext
    private lateinit var repository: JooqCustomerRepository

    @BeforeAll
    fun setupAll() {
        connection = TestDatabase.createConnection()
        dsl = TestDatabase.createDSLContext(connection)
        TestDatabase.initSchema(connection)
        repository = JooqCustomerRepository(dsl)
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

        // When
        val updated = customer.copy(name = "John Updated")
        repository.update(updated)
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

        // When
        val deleted = repository.delete(CustomerId(1L))
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

        // When
        val customers = repository.findCustomersWithHighValueOrders(Money(1000.0))

        // Then
        Assertions.assertTrue(customers.isNotEmpty())
        println("✓ Found ${customers.size} customers with high value orders")
        customers.forEach { println("  - ${it.name} (${it.email})") }
    }

    private fun setupCustomersWithOrders() {
        // Customer
        dsl.execute("""
            INSERT INTO customer (id, name, email, address, registered_date) 
            VALUES (1, 'High Value Customer', 'high@example.com', '789 Luxury Blvd', '2024-01-01')
        """)

        // Orders
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 500.00, 'DELIVERED')
        """)
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (2, 1, '2024-06-15 14:30:00', 800.00, 'DELIVERED')
        """)
    }
}

