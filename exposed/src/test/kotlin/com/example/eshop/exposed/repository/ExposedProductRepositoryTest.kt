package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.exposed.TestDatabase
import com.example.eshop.exposed.table.OrderItems
import com.example.eshop.exposed.table.Orders
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedProductRepositoryTest {

    private lateinit var db: Database
    private lateinit var repository: ExposedProductRepository

    @BeforeAll
    fun setupAll() {
        db = TestDatabase.connect()
        TestDatabase.initSchema(db)
        repository = ExposedProductRepository()
    }

    @BeforeEach
    fun setup() {
        TestDatabase.cleanDatabase(db)
    }

    @Test
    fun `should save and find product by id`() {
        transaction(db) {
            // Given
            val product = Product(
                id = ProductId(1L),
                name = "Laptop",
                price = Money(999.99),
                stock = 50,
                category = "Electronics"
            )

            // When
            repository.save(product)
            val found = repository.findById(ProductId(1L))

            // Then
            Assertions.assertNotNull(found)
            Assertions.assertEquals(product.name, found?.name)
            Assertions.assertEquals(product.category, found?.category)
            println("✓ Product saved and retrieved: ${found?.name} - $${found?.price}")
        }
    }

    @Test
    fun `should find products with low stock by category`() {
        transaction(db) {
            // Given
            repository.save(Product(ProductId(1L), "Laptop", Money(999.99), 3, "Electronics"))
            repository.save(Product(ProductId(2L), "Mouse", Money(29.99), 5, "Electronics"))
            repository.save(Product(ProductId(3L), "Desk", Money(299.99), 2, "Furniture"))
            repository.save(Product(ProductId(4L), "Chair", Money(199.99), 50, "Furniture"))

            // When
            val lowStockProducts = repository.findProductsLowStockByCategory(5)

            // Then
            Assertions.assertTrue(lowStockProducts.isNotEmpty())
            println("✓ Low stock products by category:")
            lowStockProducts.forEach { (category, products) ->
                println("  Category: $category")
                products.forEach { product ->
                    println("    - ${product.name}: ${product.stock} units")
                }
            }
        }
    }

    @Test
    fun `should find top selling products`() {
        transaction(db) {
            // Given
            setupProductsWithOrders()

            // When
            val topProducts = repository.findTopSellingProducts(3)

            // Then
            Assertions.assertTrue(topProducts.isNotEmpty())
            println("✓ Top selling products:")
            topProducts.forEach { product ->
                println("  - ${product.name} (${product.category})")
            }
        }
    }

    private fun setupProductsWithOrders() {
        repository.save(Product(ProductId(1L), "Laptop", Money(999.99), 50, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(29.99), 100, "Electronics"))
        repository.save(Product(ProductId(3L), "Keyboard", Money(79.99), 75, "Electronics"))

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
            it[totalAmount] = java.math.BigDecimal("2000.00")
            it[status] = "DELIVERED"
        }

        OrderItems.insert {
            it[id] = 1L
            it[orderId] = 1L
            it[productId] = 1L
            it[quantity] = 10
            it[price] = java.math.BigDecimal("999.99")
        }

        OrderItems.insert {
            it[id] = 2L
            it[orderId] = 1L
            it[productId] = 2L
            it[quantity] = 5
            it[price] = java.math.BigDecimal("29.99")
        }

        OrderItems.insert {
            it[id] = 3L
            it[orderId] = 1L
            it[productId] = 3L
            it[quantity] = 3
            it[price] = java.math.BigDecimal("79.99")
        }
    }
}

