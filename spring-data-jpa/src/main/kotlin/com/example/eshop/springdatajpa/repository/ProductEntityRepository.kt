package com.example.eshop.springdatajpa.repository

import com.example.eshop.springdatajpa.entity.ProductEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProductEntityRepository : JpaRepository<ProductEntity, Long> {

    /**
     * 재고가 threshold 이하인 상품 조회
     */
    fun findByStockLessThanEqual(threshold: Int): List<ProductEntity>

    /**
     * 가장 많이 주문된 상품 Top N 조회
     * OrderItem과 조인하여 주문 횟수 기준으로 정렬
     */
    @Query("""
        SELECT p FROM ProductEntity p
        JOIN OrderItemEntity oi ON p.id = oi.productId
        GROUP BY p.id, p.name, p.price, p.stock, p.category
        ORDER BY SUM(oi.quantity) DESC
    """)
    fun findTopSellingProducts(pageable: Pageable): List<ProductEntity>
}

