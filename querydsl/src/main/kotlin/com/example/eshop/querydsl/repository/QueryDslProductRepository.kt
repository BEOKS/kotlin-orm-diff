package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.querydsl.entity.ProductEntity
import com.example.eshop.querydsl.entity.QOrderItemEntity
import com.example.eshop.querydsl.entity.QProductEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class QueryDslProductRepository(private val em: EntityManager) : ProductRepository {

    private val queryFactory = JPAQueryFactory(em)
    private val qProduct = QProductEntity.productEntity
    private val qOrderItem = QOrderItemEntity.orderItemEntity

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
        return queryFactory
            .selectFrom(qProduct)
            .where(qProduct.id.eq(id.value))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(): List<Product> {
        return queryFactory
            .selectFrom(qProduct)
            .fetch()
            .map { it.toDomain() }
    }

    override fun update(product: Product): Product {
        val entity = em.find(ProductEntity::class.java, product.id.value)
            ?: throw IllegalArgumentException("Product not found: ${product.id.value}")

        entity.name = product.name
        entity.price = product.price.amount
        entity.stock = product.stock
        entity.category = product.category
        // Dirty Checking will automatically generate UPDATE query
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val entity = em.find(ProductEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        val products = queryFactory
            .selectFrom(qProduct)
            .where(qProduct.stock.loe(threshold))
            .orderBy(qProduct.category.asc(), qProduct.stock.asc())
            .fetch()
            .map { it.toDomain() }
        
        return products.groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        return queryFactory
            .selectFrom(qProduct)
            .join(qOrderItem).on(qProduct.id.eq(qOrderItem.productId))
            .groupBy(qProduct.id, qProduct.name, qProduct.price, qProduct.stock, qProduct.category)
            .orderBy(qOrderItem.quantity.sum().desc())
            .limit(limit.toLong())
            .fetch()
            .map { it.toDomain() }
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

