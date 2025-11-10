package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.springdatajpa.entity.ProductEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class SpringDataProductRepository(
    private val productEntityRepository: ProductEntityRepository
) : ProductRepository {

    @Transactional
    override fun save(product: Product): Product {
        // Check if entity exists
        val entity = if (product.id.value != 0L && productEntityRepository.existsById(product.id.value)) {
            // Update existing entity
            productEntityRepository.findById(product.id.value).get().apply {
                name = product.name
                price = product.price.amount
                stock = product.stock
                category = product.category
            }
        } else {
            // Create new entity
            ProductEntity(
                id = product.id.value,
                name = product.name,
                price = product.price.amount,
                stock = product.stock,
                category = product.category
            )
        }
        productEntityRepository.save(entity)
        return product
    }

    override fun findById(id: ProductId): Product? {
        return productEntityRepository.findById(id.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<Product> {
        return productEntityRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun update(product: Product): Product {
        val entity = productEntityRepository.findById(product.id.value)
            .orElseThrow { IllegalArgumentException("Product not found: ${product.id.value}") }

        entity.name = product.name
        entity.price = product.price.amount
        entity.stock = product.stock
        entity.category = product.category
        productEntityRepository.save(entity)
        return product
    }

    @Transactional
    override fun delete(id: ProductId): Boolean {
        return if (productEntityRepository.existsById(id.value)) {
            productEntityRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        val products = productEntityRepository.findByStockLessThanEqual(threshold)
            .map { it.toDomain() }
        
        return products.groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        return productEntityRepository.findTopSellingProducts(PageRequest.of(0, limit))
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

