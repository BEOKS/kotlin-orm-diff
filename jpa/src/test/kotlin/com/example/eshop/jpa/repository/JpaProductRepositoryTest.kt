package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jpa.repository.JpaCustomerRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaProductRepositoryTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repository: JpaProductRepository

    @BeforeAll
    fun setupAll() {
        emf = Persistence.createEntityManagerFactory("test-unit")
    }

    @BeforeEach
    fun setup() {
        em = emf.createEntityManager()
        repository = JpaProductRepository(em)
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
    fun `should save and find product by id`() {
        // Given
        val product = Product(
            id = ProductId(1),
            name = "Laptop",
            price = Money(999.99),
            stock = 50,
            category = "Electronics"
        )

        // When
        val saved = repository.save(product)
        em.flush()
        val found = repository.findById(saved.id)

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertTrue(saved.id.value > 0)
        Assertions.assertEquals(saved.name, found?.name)
        Assertions.assertEquals(saved.category, found?.category)
        println("Product saved and retrieved: ${found?.name} - $${found?.price} (ID: ${saved.id.value})")
    }

    @Test
    fun `should find products with low stock by category`() {
        // Given
        repository.save(Product(ProductId(1), "Laptop", Money(999.99), 3, "Electronics"))
        repository.save(Product(ProductId(1), "Mouse", Money(29.99), 5, "Electronics"))
        repository.save(Product(ProductId(1), "Desk", Money(299.99), 2, "Furniture"))
        repository.save(Product(ProductId(1), "Chair", Money(199.99), 50, "Furniture"))
        em.flush()

        // When
        val lowStockProducts = repository.findProductsLowStockByCategory(5)

        // Then
        Assertions.assertTrue(lowStockProducts.isNotEmpty())
        println("Low stock products by category:")
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
        em.flush()

        // When
        val topProducts = repository.findTopSellingProducts(3)

        // Then
        Assertions.assertTrue(topProducts.isNotEmpty())
        println("Top selling products:")
        topProducts.forEach { product ->
            println("  - ${product.name} (${product.category})")
        }
    }

    private fun setupProductsWithOrders() {
        val p1 = repository.save(Product(ProductId(1), "Laptop", Money(999.99), 50, "Electronics"))
        val p2 = repository.save(Product(ProductId(1), "Mouse", Money(29.99), 100, "Electronics"))
        val p3 = repository.save(Product(ProductId(1), "Keyboard", Money(79.99), 75, "Electronics"))

        val c1 = JpaCustomerRepository(em).save(Customer(
            id = CustomerId(1),
            name = "Test Customer",
            email = "test@example.com",
            address = "123 St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status, version)
            VALUES (1, ${c1.id.value}, '2024-06-01 10:00:00', 2000.00, 'DELIVERED', 0)
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price)
            VALUES (1, 1, ${p1.id.value}, 10, 999.99)
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price)
            VALUES (2, 1, ${p2.id.value}, 5, 29.99)
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price)
            VALUES (3, 1, ${p3.id.value}, 3, 79.99)
        """).executeUpdate()
    }
}
