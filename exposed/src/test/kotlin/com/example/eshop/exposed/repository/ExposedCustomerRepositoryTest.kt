package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.exposed.TestDatabase
import com.example.eshop.exposed.table.Orders
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedCustomerRepositoryTest {

    private lateinit var db: Database
    private lateinit var repository: ExposedCustomerRepository

    @BeforeAll
    fun setupAll() {
        db = TestDatabase.connect()
        TestDatabase.initSchema(db)
        repository = ExposedCustomerRepository()
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(db)
    }

    @Test
    fun `should save and find customer by id`() {
        transaction(db) {
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
    }

    @Test
    fun `should find all customers`() {
        transaction(db) {
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
    }

    @Test
    fun `should update customer`() {
        transaction(db) {
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
    }

    @Test
    fun `should delete customer`() {
        transaction(db) {
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
    }

    @Test
    fun `should find customers with high value orders`() {
        transaction(db) {
            // Given
            setupCustomersWithOrders()

            // When
            val customers = repository.findCustomersWithHighValueOrders(Money(1000.0))

            // Then
            Assertions.assertTrue(customers.isNotEmpty())
            println("✓ Found ${customers.size} customers with high value orders")
            customers.forEach { println("  - ${it.name} (${it.email})") }
        }
    }

    private fun setupCustomersWithOrders() {
        repository.save(Customer(
            id = CustomerId(1L),
            name = "High Value Customer",
            email = "high@example.com",
            address = "789 Luxury Blvd",
            registeredDate = LocalDate.parse("2024-01-01")
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
            it[orderDate] = LocalDateTime.parse("2024-06-15T14:30:00")
            it[totalAmount] = java.math.BigDecimal("800.00")
            it[status] = "DELIVERED"
        }
    }
}

