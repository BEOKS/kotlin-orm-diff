package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.repository.ProductRepository
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId
import com.example.eshop.jooq.generated.tables.references.PRODUCT
import com.example.eshop.jooq.generated.tables.references.ORDER_ITEM
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

private val logger = KotlinLogging.logger {}

class JooqProductRepository(private val dsl: DSLContext) : ProductRepository {

    // Private mapper function
    private fun mapToProduct(record: Record): Product? {
        return try {
            Product(
                id = ProductId(record.getValue(PRODUCT.ID) ?: return null),
                name = record.getValue(PRODUCT.NAME) ?: return null,
                price = Money(record.getValue(PRODUCT.PRICE) ?: return null),
                stock = record.getValue(PRODUCT.STOCK) ?: return null,
                category = record.getValue(PRODUCT.CATEGORY) ?: return null
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to map record to Product" }
            null
        }
    }

    override fun save(product: Product): Product {
        logger.debug { "Saving product: ${product.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                // Check for duplicate
                val exists = ctx.fetchExists(
                    ctx.selectFrom(PRODUCT).where(PRODUCT.ID.eq(product.id.value))
                )

                if (exists) {
                    logger.warn { "Product with id ${product.id.value} already exists" }
                    throw IllegalStateException("Product with id ${product.id.value} already exists")
                }

                ctx.insertInto(PRODUCT)
                    .set(PRODUCT.ID, product.id.value)
                    .set(PRODUCT.NAME, product.name)
                    .set(PRODUCT.PRICE, product.price.amount)
                    .set(PRODUCT.STOCK, product.stock)
                    .set(PRODUCT.CATEGORY, product.category)
                    .execute()

                logger.info { "Successfully saved product: ${product.id.value}" }
                product
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save product: ${product.id.value}" }
            throw e
        }
    }

    override fun findById(id: ProductId): Product? {
        logger.debug { "Finding product by id: ${id.value}" }

        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE,
            PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .where(PRODUCT.ID.eq(id.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found product: ${id.value}" }
                mapToProduct(it)
            }
            ?: run {
                logger.debug { "Product not found: ${id.value}" }
                null
            }
    }

    override fun findAll(): List<Product> {
        logger.debug { "Finding all products" }

        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE,
            PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .fetch()
            .mapNotNull { mapToProduct(it) }
            .also { logger.debug { "Found ${it.size} products" } }
    }

    override fun update(product: Product): Product {
        logger.debug { "Updating product: ${product.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val rowsAffected = ctx.update(PRODUCT)
                    .set(PRODUCT.NAME, product.name)
                    .set(PRODUCT.PRICE, product.price.amount)
                    .set(PRODUCT.STOCK, product.stock)
                    .set(PRODUCT.CATEGORY, product.category)
                    .where(PRODUCT.ID.eq(product.id.value))
                    .execute()

                if (rowsAffected == 0) {
                    logger.warn { "Product with id ${product.id.value} not found for update" }
                    throw IllegalStateException("Product with id ${product.id.value} not found")
                }

                logger.info { "Successfully updated product: ${product.id.value}" }
                product
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update product: ${product.id.value}" }
            throw e
        }
    }

    override fun delete(id: ProductId): Boolean {
        logger.debug { "Deleting product: ${id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val deleted = ctx.deleteFrom(PRODUCT)
                    .where(PRODUCT.ID.eq(id.value))
                    .execute()

                val result = deleted > 0
                if (result) {
                    logger.info { "Successfully deleted product: ${id.value}" }
                } else {
                    logger.warn { "Product not found for deletion: ${id.value}" }
                }
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete product: ${id.value}" }
            throw e
        }
    }

    /**
     * 재고가 적은 상품을 카테고리별로 조회합니다.
     *
     * @param threshold 재고 임계값
     * @return 카테고리별로 그룹화된 재고 부족 상품 맵
     * @throws IllegalArgumentException threshold가 음수인 경우
     */
    override fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>> {
        require(threshold >= 0) { "Threshold must be non-negative, but was: $threshold" }

        logger.debug { "Finding products with low stock (threshold: $threshold)" }

        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.PRICE,
            PRODUCT.STOCK, PRODUCT.CATEGORY)
            .from(PRODUCT)
            .where(PRODUCT.STOCK.le(threshold))
            .orderBy(PRODUCT.CATEGORY.asc(), PRODUCT.STOCK.asc())
            .fetch()
            .mapNotNull { mapToProduct(it) }
            .groupBy { it.category }
            .also { logger.debug { "Found ${it.values.sumOf { list -> list.size }} low stock products in ${it.size} categories" } }
    }

    /**
     * 가장 많이 팔린 상품을 조회합니다.
     *
     * 주의: 주문 이력이 없는 상품은 결과에 포함되지 않습니다.
     *
     * @param limit 조회할 최대 상품 수
     * @return 판매량이 많은 순서로 정렬된 상품 목록
     * @throws IllegalArgumentException limit이 양수가 아닌 경우
     */
    override fun findTopSellingProducts(limit: Int): List<Product> {
        require(limit > 0) { "Limit must be positive, but was: $limit" }

        logger.debug { "Finding top $limit selling products" }

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
            .mapNotNull { record ->
                try {
                    Product(
                        id = ProductId(record.getValue(p.ID) ?: return@mapNotNull null),
                        name = record.getValue(p.NAME) ?: return@mapNotNull null,
                        price = Money(record.getValue(p.PRICE) ?: return@mapNotNull null),
                        stock = record.getValue(p.STOCK) ?: return@mapNotNull null,
                        category = record.getValue(p.CATEGORY) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map top selling product" }
                    null
                }
            }
            .also { logger.info { "Found ${it.size} top selling products" } }
    }
}
