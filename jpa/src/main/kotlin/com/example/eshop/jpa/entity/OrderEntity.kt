package com.example.eshop.jpa.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "customer_id", nullable = false)
    var customerId: Long = 0,

    @Column(name = "order_date", nullable = false)
    var orderDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "status", nullable = false)
    var status: String = ""
)

