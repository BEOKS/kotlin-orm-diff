package com.example.eshop.springdatajpa.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "order_item")
class OrderItemEntity(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "product_id", nullable = false)
    var productId: Long,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    var price: BigDecimal
) {
    // JPA requires a no-arg constructor
    protected constructor() : this(0, 0, 0, 0, BigDecimal.ZERO)
}

