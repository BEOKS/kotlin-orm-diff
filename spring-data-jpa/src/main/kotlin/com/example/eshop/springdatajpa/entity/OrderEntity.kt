package com.example.eshop.springdatajpa.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "customer_id", nullable = false)
    var customerId: Long,

    @Column(name = "order_date", nullable = false)
    var orderDate: LocalDateTime,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal,

    @Column(name = "status", nullable = false)
    var status: String
) {
    // JPA requires a no-arg constructor
    protected constructor() : this(0, 0, LocalDateTime.now(), BigDecimal.ZERO, "")
}

