package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.exposed.table.OrderItems
import com.example.eshop.exposed.table.Products
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ExposedProductRepository : ProductRepository {

    override fun save(product: Product): Product {
        Products.insert {
            it[id] = product.id.value
            it[name] = product.name
            it[price] = product.price.amount
            it[stock] = product.stock
            it[category] = product.category
        }
        return product
    }

    override fun findById(id: ProductId): Product? {
        return Products.selectAll()
            .where { Products.id eq id.value }
            .map { it.toProduct() }
            .singleOrNull()
    }

    override fun findAll(): List<Product> {
        return Products.selectAll()
            .map { it.toProduct() }
    }

    override fun update(product: Product): Product {
        Products.update({ Products.id eq product.id.value }) {
            it[name] = product.name
            it[price] = product.price.amount
            it[stock] = product.stock
            it[category] = product.category
        }
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val deleted = Products.deleteWhere { Products.id eq id.value }
        return deleted > 0
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        return Products.selectAll()
            .where { Products.stock lessEq threshold }
            .orderBy(Products.category to SortOrder.ASC, Products.stock to SortOrder.ASC)
            .map { it.toProduct() }
            .groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        val totalSoldAlias = OrderItems.quantity.sum()
        
        return Products
            .join(OrderItems, JoinType.INNER, Products.id, OrderItems.productId)
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
            .orderBy(totalSoldAlias to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                Product(
                    id = ProductId(row[Products.id]),
                    name = row[Products.name],
                    price = Money(row[Products.price]),
                    stock = row[Products.stock],
                    category = row[Products.category]
                )
            }
    }

    private fun ResultRow.toProduct() = Product(
        id = ProductId(this[Products.id]),
        name = this[Products.name],
        price = Money(this[Products.price]),
        stock = this[Products.stock],
        category = this[Products.category]
    )
}

