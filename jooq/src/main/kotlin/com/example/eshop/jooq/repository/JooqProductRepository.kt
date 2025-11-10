package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.schema.OrderItemFields
import com.example.eshop.jooq.schema.ProductFields
import com.example.eshop.jooq.schema.Tables
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.math.BigDecimal

class JooqProductRepository(private val dsl: DSLContext) : ProductRepository {

    override fun save(product: Product): Product {
        dsl.insertInto(DSL.table(Tables.PRODUCT))
            .set(DSL.field(ProductFields.ID), product.id.value)
            .set(DSL.field(ProductFields.NAME), product.name)
            .set(DSL.field(ProductFields.PRICE), product.price.amount)
            .set(DSL.field(ProductFields.STOCK), product.stock)
            .set(DSL.field(ProductFields.CATEGORY), product.category)
            .execute()
        return product
    }

    override fun findById(id: ProductId): Product? {
        val pId = DSL.field(ProductFields.ID, Long::class.java)
        val pName = DSL.field(ProductFields.NAME, String::class.java)
        val pPrice = DSL.field(ProductFields.PRICE, BigDecimal::class.java)
        val pStock = DSL.field(ProductFields.STOCK, Int::class.java)
        val pCategory = DSL.field(ProductFields.CATEGORY, String::class.java)
        
        return dsl.select(pId, pName, pPrice, pStock, pCategory)
            .from(Tables.PRODUCT)
            .where(pId.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Product(
                    id = ProductId(record.getValue(pId)),
                    name = record.getValue(pName),
                    price = Money(record.getValue(pPrice)),
                    stock = record.getValue(pStock),
                    category = record.getValue(pCategory)
                )
            }
    }

    override fun findAll(): List<Product> {
        val pId = DSL.field(ProductFields.ID, Long::class.java)
        val pName = DSL.field(ProductFields.NAME, String::class.java)
        val pPrice = DSL.field(ProductFields.PRICE, BigDecimal::class.java)
        val pStock = DSL.field(ProductFields.STOCK, Int::class.java)
        val pCategory = DSL.field(ProductFields.CATEGORY, String::class.java)
        
        return dsl.select(pId, pName, pPrice, pStock, pCategory)
            .from(Tables.PRODUCT)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(pId)),
                    name = record.getValue(pName),
                    price = Money(record.getValue(pPrice)),
                    stock = record.getValue(pStock),
                    category = record.getValue(pCategory)
                )
            }
    }

    override fun update(product: Product): Product {
        dsl.update(DSL.table(Tables.PRODUCT))
            .set(DSL.field(ProductFields.NAME), product.name)
            .set(DSL.field(ProductFields.PRICE), product.price.amount)
            .set(DSL.field(ProductFields.STOCK), product.stock)
            .set(DSL.field(ProductFields.CATEGORY), product.category)
            .where(DSL.field(ProductFields.ID).eq(product.id.value))
            .execute()
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val deleted = dsl.deleteFrom(DSL.table(Tables.PRODUCT))
            .where(DSL.field(ProductFields.ID).eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        val pId = DSL.field(ProductFields.ID, Long::class.java)
        val pName = DSL.field(ProductFields.NAME, String::class.java)
        val pPrice = DSL.field(ProductFields.PRICE, BigDecimal::class.java)
        val pStock = DSL.field(ProductFields.STOCK, Int::class.java)
        val pCategory = DSL.field(ProductFields.CATEGORY, String::class.java)
        
        return dsl.select(pId, pName, pPrice, pStock, pCategory)
            .from(Tables.PRODUCT)
            .where(pStock.le(threshold))
            .orderBy(pCategory, pStock)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(pId)),
                    name = record.getValue(pName),
                    price = Money(record.getValue(pPrice)),
                    stock = record.getValue(pStock),
                    category = record.getValue(pCategory)
                )
            }
            .groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        val pId = DSL.field("p.${ProductFields.ID}", Long::class.java)
        val pName = DSL.field("p.${ProductFields.NAME}", String::class.java)
        val pPrice = DSL.field("p.${ProductFields.PRICE}", BigDecimal::class.java)
        val pStock = DSL.field("p.${ProductFields.STOCK}", Int::class.java)
        val pCategory = DSL.field("p.${ProductFields.CATEGORY}", String::class.java)
        val oiQuantity = DSL.field("oi.${OrderItemFields.QUANTITY}", Int::class.java)
        val totalSold = DSL.sum(oiQuantity).`as`("total_sold")

        return dsl.select(pId, pName, pPrice, pStock, pCategory, totalSold)
            .from("${Tables.PRODUCT} p")
            .join("${Tables.ORDER_ITEM} oi")
            .on("p.${ProductFields.ID} = oi.${OrderItemFields.PRODUCT_ID}")
            .groupBy(pId, pName, pPrice, pStock, pCategory)
            .orderBy(totalSold.desc())
            .limit(limit)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(pId)),
                    name = record.getValue(pName),
                    price = Money(record.getValue(pPrice)),
                    stock = record.getValue(pStock),
                    category = record.getValue(pCategory)
                )
            }
    }

}
