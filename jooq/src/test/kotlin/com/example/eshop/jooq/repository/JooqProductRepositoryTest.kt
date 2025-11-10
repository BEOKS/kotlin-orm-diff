package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.TestDatabase
import org.jooq.DSLContext
import org.junit.jupiter.api.*
import java.sql.Connection

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqProductRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var dsl: DSLContext
    private lateinit var repository: JooqProductRepository

    @BeforeAll
    fun setupAll() {
        connection = TestDatabase.createConnection()
        dsl = TestDatabase.createDSLContext(connection)
        TestDatabase.initSchema(connection)
        repository = JooqProductRepository(dsl)
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
    fun `should save and find product by id`() {
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

    @Test
    fun `should find products with low stock by category`() {
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

    @Test
    fun `should find top selling products`() {
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

    private fun setupProductsWithOrders() {
        // Products
        repository.save(Product(ProductId(1L), "Laptop", Money(999.99), 50, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(29.99), 100, "Electronics"))
        repository.save(Product(ProductId(3L), "Keyboard", Money(79.99), 75, "Electronics"))

        // Customer
        dsl.execute("""
            INSERT INTO customer (id, name, email, address, registered_date) 
            VALUES (1, 'Test Customer', 'test@example.com', '123 St', '2024-01-01')
        """)

        // Orders
        dsl.execute("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 2000.00, 'DELIVERED')
        """)

        // Order Items
        dsl.execute("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (1, 1, 1, 10, 999.99)
        """)
        dsl.execute("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (2, 1, 2, 5, 29.99)
        """)
        dsl.execute("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (3, 1, 3, 3, 79.99)
        """)
    }
}

