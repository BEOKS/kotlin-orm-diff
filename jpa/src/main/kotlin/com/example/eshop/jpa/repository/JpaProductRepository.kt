package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jpa.entity.ProductEntity
import jakarta.persistence.EntityManager

class JpaProductRepository(private val em: EntityManager) : ProductRepository {

    override fun save(product: Product): Product {
        val entity = ProductEntity(
            id = product.id.value,
            name = product.name,
            price = product.price.amount,
            stock = product.stock,
            category = product.category
        )
        em.persist(entity)
        return product
    }

    override fun findById(id: ProductId): Product? {
        return em.find(ProductEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<Product> {
        return em.createQuery("SELECT p FROM ProductEntity p", ProductEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(product: Product): Product {
        val entity = em.find(ProductEntity::class.java, product.id.value)
            ?: throw IllegalArgumentException("Product not found: ${product.id.value}")
        
        entity.name = product.name
        entity.price = product.price.amount
        entity.stock = product.stock
        entity.category = product.category
        em.merge(entity)
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val entity = em.find(ProductEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        val products = em.createQuery("""
            SELECT p FROM ProductEntity p
            WHERE p.stock <= :threshold
            ORDER BY p.category, p.stock
        """, ProductEntity::class.java)
            .setParameter("threshold", threshold)
            .resultList
            .map { it.toDomain() }
        
        return products.groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        val query = em.createQuery("""
            SELECT p FROM ProductEntity p
            JOIN OrderItemEntity oi ON p.id = oi.productId
            GROUP BY p.id, p.name, p.price, p.stock, p.category
            ORDER BY SUM(oi.quantity) DESC
        """, ProductEntity::class.java)
        
        query.maxResults = limit
        return query.resultList.map { it.toDomain() }
    }

    private fun ProductEntity.toDomain(): Product {
        return Product(
            id = ProductId(id),
            name = name,
            price = Money(price),
            stock = stock,
            category = category
        )
    }
}

