package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(SpringDataProductRepository::class)
class SpringDataProductRepositoryTest {

    @Autowired
    private lateinit var repository: SpringDataProductRepository

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
    fun `should save and find product by id`() {
        // Given
        val product = Product(
            id = ProductId(1L),
            name = "Laptop",
            price = Money(1200.0),
            stock = 50,
            category = "Electronics"
        )

        // When
        repository.save(product)
        val found = repository.findById(ProductId(1L))

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertEquals(product.name, found?.name)
        Assertions.assertEquals(product.price, found?.price)
        println("✓ Product saved and retrieved: ${found?.name}")
    }

    @Test
    fun `should find all products`() {
        // Given
        val product1 = Product(
            id = ProductId(1L),
            name = "Laptop",
            price = Money(1200.0),
            stock = 50,
            category = "Electronics"
        )
        val product2 = Product(
            id = ProductId(2L),
            name = "Mouse",
            price = Money(25.0),
            stock = 200,
            category = "Electronics"
        )

        // When
        repository.save(product1)
        repository.save(product2)
        val all = repository.findAll()

        // Then
        Assertions.assertEquals(2, all.size)
        println("✓ Found ${all.size} products")
    }

    @Test
    fun `should update product`() {
        // Given
        val product = Product(
            id = ProductId(1L),
            name = "Laptop",
            price = Money(1200.0),
            stock = 50,
            category = "Electronics"
        )
        repository.save(product)

        // When
        val updated = product.copy(price = Money(1100.0), stock = 45)
        repository.update(updated)
        val found = repository.findById(ProductId(1L))

        // Then
        Assertions.assertEquals(Money(1100.0), found?.price)
        Assertions.assertEquals(45, found?.stock)
        println("✓ Product updated: ${found?.name} - ${found?.price}")
    }

    @Test
    fun `should delete product`() {
        // Given
        val product = Product(
            id = ProductId(1L),
            name = "Laptop",
            price = Money(1200.0),
            stock = 50,
            category = "Electronics"
        )
        repository.save(product)

        // When
        val deleted = repository.delete(ProductId(1L))
        val found = repository.findById(ProductId(1L))

        // Then
        Assertions.assertTrue(deleted)
        Assertions.assertNull(found)
        println("✓ Product deleted successfully")
    }

    @Test
    fun `should find products with low stock by category`() {
        // Given
        setupProductsWithVariousStock()

        // When
        val lowStockProducts = repository.findProductsLowStockByCategory(10)

        // Then
        Assertions.assertTrue(lowStockProducts.isNotEmpty())
        println("✓ Found low stock products by category:")
        lowStockProducts.forEach { (category, products) ->
            println("  - $category: ${products.size} products")
            products.forEach { p -> println("    * ${p.name} (stock: ${p.stock})") }
        }
    }

    @Test
    fun `should find top selling products`() {
        // Given
        setupProductsWithOrders()

        // When
        val topProducts = repository.findTopSellingProducts(3)

        // Then
        Assertions.assertTrue(topProducts.isNotEmpty())
        println("✓ Top selling products:")
        topProducts.forEach { println("  - ${it.name}") }
    }

    private fun setupProductsWithVariousStock() {
        repository.save(Product(ProductId(1L), "Laptop", Money(1200.0), 5, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(25.0), 8, "Electronics"))
        repository.save(Product(ProductId(3L), "Desk", Money(300.0), 3, "Furniture"))
        repository.save(Product(ProductId(4L), "Chair", Money(150.0), 50, "Furniture"))
    }

    private fun setupProductsWithOrders() {
        // Products
        repository.save(Product(ProductId(1L), "Laptop", Money(1200.0), 50, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(25.0), 200, "Electronics"))
        repository.save(Product(ProductId(3L), "Keyboard", Money(75.0), 100, "Electronics"))

        // Customer
        jdbcTemplate.update(
            "INSERT INTO customer (id, name, email, address, registered_date) VALUES (?, ?, ?, ?, ?)",
            1L, "John Doe", "john@example.com", "123 Main St", "2024-01-01"
        )

        // Orders
        jdbcTemplate.update(
            "INSERT INTO orders (id, customer_id, order_date, total_amount, status) VALUES (?, ?, ?, ?, ?)",
            1L, 1L, "2024-06-01 10:00:00", 500.00, "DELIVERED"
        )

        // Order items
        jdbcTemplate.update(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)",
            1L, 1L, 1L, 10, 1200.00
        )
        jdbcTemplate.update(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)",
            2L, 1L, 2L, 5, 25.00
        )
        jdbcTemplate.update(
            "INSERT INTO order_item (id, order_id, product_id, quantity, price) VALUES (?, ?, ?, ?, ?)",
            3L, 1L, 3L, 3, 75.00
        )
    }
}

