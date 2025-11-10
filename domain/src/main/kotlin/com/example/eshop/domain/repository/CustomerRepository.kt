package com.example.eshop.domain.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money

interface CustomerRepository {
    // CRUD operations
    fun save(customer: Customer): Customer
    fun findById(id: CustomerId): Customer?
    fun findAll(): List<Customer>
    fun update(customer: Customer): Customer
    fun delete(id: CustomerId): Boolean

    // Complex queries
    /**
     * 고액 주문 고객 조회
     * 총 주문 금액이 minAmount 이상인 고객 목록 조회 (조인, 그룹핑, having)
     */
    fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer>

    /**
     * 특정 기간 동안 가입한 고객 중 주문 이력이 있는 고객 조회
     */
    fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer>
}

