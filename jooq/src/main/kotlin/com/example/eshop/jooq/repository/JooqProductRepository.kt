package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.generated.tables.references.PRODUCT
import com.example.eshop.jooq.generated.tables.references.ORDER_ITEM
import org.jooq.DSLContext
import org.jooq.impl.DSL

class JooqProductRepository(private val dsl: DSLContext) : ProductRepository {

    override fun save(product: Product): Product {
        dsl.insertInto(PRODUCT)
            .set(PRODUCT.ID, product.id.value)
            .set(PRODUCT.NAME, product.name)
            .set(PRODUCT.PRICE, product.price.amount)
            .set(PRODUCT.STOCK, product.stock)
            .set(PRODUCT.CATEGORY, product.category)
            .execute()
        return product
    }

    override fun findById(id: ProductId): Product? {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .where(PRODUCT.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Product(
                    id = ProductId(record.getValue(PRODUCT.ID)!!),
                    name = record.getValue(PRODUCT.NAME)!!,
                    price = Money(record.getValue(PRODUCT.PRICE)!!),
                    stock = record.getValue(PRODUCT.STOCK)!!,
                    category = record.getValue(PRODUCT.CATEGORY)!!
                )
            }
    }

    override fun findAll(): List<Product> {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(PRODUCT.ID)!!),
                    name = record.getValue(PRODUCT.NAME)!!,
                    price = Money(record.getValue(PRODUCT.PRICE)!!),
                    stock = record.getValue(PRODUCT.STOCK)!!,
                    category = record.getValue(PRODUCT.CATEGORY)!!
                )
            }
    }

    override fun update(product: Product): Product {
        dsl.update(PRODUCT)
            .set(PRODUCT.NAME, product.name)
            .set(PRODUCT.PRICE, product.price.amount)
            .set(PRODUCT.STOCK, product.stock)
            .set(PRODUCT.CATEGORY, product.category)
            .where(PRODUCT.ID.eq(product.id.value))
            .execute()
        return product
    }

    override fun delete(id: ProductId): Boolean {
        val deleted = dsl.deleteFrom(PRODUCT)
            .where(PRODUCT.ID.eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE, PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .where(PRODUCT.STOCK.le(threshold))
            .orderBy(PRODUCT.CATEGORY, PRODUCT.STOCK)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(PRODUCT.ID)!!),
                    name = record.getValue(PRODUCT.NAME)!!,
                    price = Money(record.getValue(PRODUCT.PRICE)!!),
                    stock = record.getValue(PRODUCT.STOCK)!!,
                    category = record.getValue(PRODUCT.CATEGORY)!!
                )
            }
            .groupBy { it.category }
    }

    override fun findTopSellingProducts(limit: Int): List<Product> {
        val p = PRODUCT.`as`("p")
        val oi = ORDER_ITEM.`as`("oi")
        val totalSold = DSL.sum(oi.QUANTITY).`as`("total_sold")

        return dsl.select(p.ID, p.NAME, p.PRICE, p.STOCK, p.CATEGORY, totalSold)
            .from(p)
            .join(oi).on(p.ID.eq(oi.PRODUCT_ID))
            .groupBy(p.ID, p.NAME, p.PRICE, p.STOCK, p.CATEGORY)
            .orderBy(totalSold.desc())
            .limit(limit)
            .fetch()
            .map { record ->
                Product(
                    id = ProductId(record.getValue(p.ID)!!),
                    name = record.getValue(p.NAME)!!,
                    price = Money(record.getValue(p.PRICE)!!),
                    stock = record.getValue(p.STOCK)!!,
                    category = record.getValue(p.CATEGORY)!!
                )
            }
    }

}
