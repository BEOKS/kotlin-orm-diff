package com.example.eshop.springdatajpa.repository

import com.example.eshop.springdatajpa.entity.CustomerEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface CustomerEntityRepository : JpaRepository<CustomerEntity, Long> {
    
    /**
     * 고액 주문 고객 조회
     * 총 주문 금액이 minAmount 이상인 고객 목록 조회 (조인, 그룹핑, having)
     */
    @Query("""
        SELECT c FROM CustomerEntity c
        JOIN OrderEntity o ON c.id = o.customerId
        GROUP BY c.id, c.name, c.email, c.address, c.registeredDate
        HAVING SUM(o.totalAmount) >= :minAmount
    """)
    fun findCustomersWithHighValueOrders(@Param("minAmount") minAmount: BigDecimal): List<CustomerEntity>
    
    /**
     * 특정 기간 동안 가입한 고객 중 주문 이력이 있는 고객 조회
     */
    @Query("""
        SELECT DISTINCT c FROM CustomerEntity c
        JOIN OrderEntity o ON c.id = o.customerId
        WHERE c.registeredDate BETWEEN :startDate AND :endDate
    """)
    fun findCustomersWithOrdersInPeriod(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<CustomerEntity>
}

