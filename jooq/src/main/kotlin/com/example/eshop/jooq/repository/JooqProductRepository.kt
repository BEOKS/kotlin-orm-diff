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
        return dsl.select()
            .from(Tables.PRODUCT)
            .where(DSL.field(ProductFields.ID).eq(id.value))
            .fetchOne()
            ?.let { mapToProduct(it) }
    }

    override fun findAll(): List<Product> {
        return dsl.select()
            .from(Tables.PRODUCT)
            .fetch()
            .map { mapToProduct(it) }
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
        return dsl.select()
            .from(Tables.PRODUCT)
            .where(DSL.field(ProductFields.STOCK, Int::class.java).le(threshold))
            .orderBy(DSL.field(ProductFields.CATEGORY), DSL.field(ProductFields.STOCK))
            .fetch()
            .map { mapToProduct(it) }
            .groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        val p = DSL.table(Tables.PRODUCT).asTable("p")
        val oi = DSL.table(Tables.ORDER_ITEM).asTable("oi")

        return dsl.select(
            p.field(ProductFields.ID),
            p.field(ProductFields.NAME),
            p.field(ProductFields.PRICE),
            p.field(ProductFields.STOCK),
            p.field(ProductFields.CATEGORY),
            DSL.sum(oi.field(OrderItemFields.QUANTITY, Int::class.java)).`as`("total_sold")
        )
            .from(p)
            .join(oi).on(p.field(ProductFields.ID).eq(oi.field(OrderItemFields.PRODUCT_ID)))
            .groupBy(
                p.field(ProductFields.ID),
                p.field(ProductFields.NAME),
                p.field(ProductFields.PRICE),
                p.field(ProductFields.STOCK),
                p.field(ProductFields.CATEGORY)
            )
            .orderBy(DSL.field("total_sold").desc())
            .limit(limit)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.get(p.field(ProductFields.ID), Long::class.java)!!),
                    name = record.get(p.field(ProductFields.NAME), String::class.java)!!,
                    price = Money(record.get(p.field(ProductFields.PRICE), BigDecimal::class.java)!!),
                    stock = record.get(p.field(ProductFields.STOCK), Int::class.java)!!,
                    category = record.get(p.field(ProductFields.CATEGORY), String::class.java)!!
                )
            }
    }

    private fun mapToProduct(record: org.jooq.Record): Product {
        return Product(
            id = ProductId(record.get(ProductFields.ID, Long::class.java)!!),
            name = record.get(ProductFields.NAME, String::class.java)!!,
            price = Money(record.get(ProductFields.PRICE, BigDecimal::class.java)!!),
            stock = record.get(ProductFields.STOCK, Int::class.java)!!,
            category = record.get(ProductFields.CATEGORY, String::class.java)!!
        )
    }
}

