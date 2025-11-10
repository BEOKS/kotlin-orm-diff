package com.example.eshop.ktorm.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.ktorm.table.OrderItems
import com.example.eshop.ktorm.table.Products
import org.ktorm.database.Database
import org.ktorm.dsl.*

class KtormProductRepository(private val database: Database) : ProductRepository {

    override fun save(product: Product): Product {
        database.insert(Products) {
            set(it.id, product.id.value)
            set(it.name, product.name)
            set(it.price, product.price.amount)
            set(it.stock, product.stock)
            set(it.category, product.category)
        }
        return product
    }

    override fun findById(id: ProductId): Product? {
        return database.from(Products)
            .select()
            .where { Products.id eq id.value }
            .map { row -> row.toProduct() }
            .singleOrNull()
    }

    override fun findAll(): List<Product> {
        return database.from(Products)
            .select()
            .map { row -> row.toProduct() }
    }

    override fun update(product: Product): Product {
        val updatedRows = database.update(Products) {
            set(it.name, product.name)
            set(it.price, product.price.amount)
            set(it.stock, product.stock)
            set(it.category, product.category)
            where { it.id eq product.id.value }
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Product with id ${product.id.value} not found")
        }
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val deleted = database.delete(Products) { it.id eq id.value }
        return deleted > 0
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        require(threshold >= 0) { "threshold must be non-negative" }

        return database.from(Products)
            .select()
            .where { Products.stock lessEq threshold }
            .orderBy(Products.category.asc(), Products.stock.asc())
            .map { row -> row.toProduct() }
            .groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        require(limit > 0) { "limit must be positive" }

        val totalSoldAlias = sum(OrderItems.quantity).aliased("total_sold")

        return database.from(Products)
            .innerJoin(OrderItems, on = Products.id eq OrderItems.productId)
            .select(
                Products.id,
                Products.name,
                Products.price,
                Products.stock,
                Products.category,
                totalSoldAlias
            )
            .groupBy(
                Products.id,
                Products.name,
                Products.price,
                Products.stock,
                Products.category
            )
            .orderBy(totalSoldAlias.desc())
            .limit(limit)
            .map { row ->
                Product(
                    id = ProductId(row[Products.id]!!),
                    name = row[Products.name]!!,
                    price = Money(row[Products.price]!!),
                    stock = row[Products.stock]!!,
                    category = row[Products.category]!!
                )
            }
    }

    private fun QueryRowSet.toProduct() = Product(
        id = ProductId(this[Products.id]!!),
        name = this[Products.name]!!,
        price = Money(this[Products.price]!!),
        stock = this[Products.stock]!!,
        category = this[Products.category]!!
    )
}
