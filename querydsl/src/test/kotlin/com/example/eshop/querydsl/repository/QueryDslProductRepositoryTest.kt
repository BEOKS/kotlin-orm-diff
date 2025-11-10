package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryDslProductRepositoryTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager
    private lateinit var repository: QueryDslProductRepository

    @BeforeAll
    fun setupAll() {
        emf = Persistence.createEntityManagerFactory("test-unit")
    }

    @BeforeEach
    fun setup() {
        em = emf.createEntityManager()
        repository = QueryDslProductRepository(em)
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
    fun `should save and find product by id with QueryDSL`() {
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
        em.flush()
        val found = repository.findById(ProductId(1L))

        // Then
        Assertions.assertNotNull(found)
        Assertions.assertEquals(product.name, found?.name)
        Assertions.assertEquals(product.category, found?.category)
        println("✓ [QueryDSL] Product saved and retrieved: ${found?.name} - $${found?.price}")
    }

    @Test
    fun `should find products with low stock by category using QueryDSL`() {
        // Given
        repository.save(Product(ProductId(1L), "Laptop", Money(999.99), 3, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(29.99), 5, "Electronics"))
        repository.save(Product(ProductId(3L), "Desk", Money(299.99), 2, "Furniture"))
        repository.save(Product(ProductId(4L), "Chair", Money(199.99), 50, "Furniture"))
        em.flush()

        // When
        val lowStockProducts = repository.findProductsLowStockByCategory(5)

        // Then
        Assertions.assertTrue(lowStockProducts.isNotEmpty())
        println("✓ [QueryDSL] Low stock products by category:")
        lowStockProducts.forEach { (category, products) ->
            println("  Category: $category")
            products.forEach { product ->
                println("    - ${product.name}: ${product.stock} units")
            }
        }
    }

    @Test
    fun `should find top selling products using QueryDSL`() {
        // Given
        setupProductsWithOrders()
        em.flush()

        // When
        val topProducts = repository.findTopSellingProducts(3)

        // Then
        Assertions.assertTrue(topProducts.isNotEmpty())
        println("✓ [QueryDSL] Top selling products:")
        topProducts.forEach { product ->
            println("  - ${product.name} (${product.category})")
        }
    }

    private fun setupProductsWithOrders() {
        repository.save(Product(ProductId(1L), "Laptop", Money(999.99), 50, "Electronics"))
        repository.save(Product(ProductId(2L), "Mouse", Money(29.99), 100, "Electronics"))
        repository.save(Product(ProductId(3L), "Keyboard", Money(79.99), 75, "Electronics"))

        QueryDslCustomerRepository(em).save(Customer(
            id = CustomerId(1L),
            name = "Test Customer",
            email = "test@example.com",
            address = "123 St",
            registeredDate = LocalDate.parse("2024-01-01")
        ))

        em.createNativeQuery("""
            INSERT INTO orders (id, customer_id, order_date, total_amount, status) 
            VALUES (1, 1, '2024-06-01 10:00:00', 2000.00, 'DELIVERED')
        """).executeUpdate()

        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (1, 1, 1, 10, 999.99)
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (2, 1, 2, 5, 29.99)
        """).executeUpdate()
        
        em.createNativeQuery("""
            INSERT INTO order_item (id, order_id, product_id, quantity, price) 
            VALUES (3, 1, 3, 3, 79.99)
        """).executeUpdate()
    }
}

