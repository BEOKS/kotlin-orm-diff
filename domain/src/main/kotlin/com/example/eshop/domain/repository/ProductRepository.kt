package com.example.eshop.domain.repository

import com.example.eshop.domain.entity.Product
import com.example.eshop.domain.valueobject.ProductId

interface ProductRepository {
    // CRUD operations
    fun save(product: Product): Product
    fun findById(id: ProductId): Product?
    fun findAll(): List<Product>
    fun update(product: Product): Product
    fun delete(id: ProductId): Boolean

    // Complex queries
    /**
     * 카테고리별 재고 부족 상품 조회
     * 재고가 threshold 이하인 상품을 카테고리별로 조회
     */
    fun findProductsLowStockByCategory(threshold: Int): Map<String, List<Product>>

    /**
     * 가장 많이 주문된 상품 Top N 조회
     * OrderItem과 조인하여 주문 횟수 기준으로 정렬
     */
    fun findTopSellingProducts(limit: Int): List<Product>
}

