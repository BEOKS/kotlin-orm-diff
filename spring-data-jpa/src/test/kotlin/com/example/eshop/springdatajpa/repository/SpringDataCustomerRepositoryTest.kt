package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJpaTest
@ActiveProfiles("test")
@Import(SpringDataCustomerRepository::class)
class SpringDataCustomerRepositoryTest {

    @Autowired
    private lateinit var repository: SpringDataCustomerRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        // Clean up
        jdbcTemplate.execute("DELETE FROM order_item")
        jdbcTemplate.execute("DELETE FROM payment")
        jdbcTemplate.execute("DELETE FROM orders")
        jdbcTemplate.execute("DELETE FROM product")
        jdbcTemplate.execute("DELETE FROM customer")
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

    @Test
    fun `should find customers with orders in period`() {
        // Given
        setupCustomersWithOrders()

        // When
        val customers = repository.findCustomersWithOrdersInPeriod("2024-01-01", "2024-12-31")

        // Then
        Assertions.assertTrue(customers.isNotEmpty())
        println("✓ Found ${customers.size} customers with orders in period")
        customers.forEach { println("  - ${it.name}") }
    }

    private fun setupCustomersWithOrders() {
        repository.save(Customer(
            id = CustomerId(1L),
            name = "High Value Customer",
            email = "high@example.com",
            address = "789 Luxury Blvd",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            1L, 1L, "2024-06-01 10:00:00", 500.00, "DELIVERED"
        )
        
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            2L, 1L, "2024-06-15 14:30:00", 800.00, "DELIVERED"
        )
    }
}

