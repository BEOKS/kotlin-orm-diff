package com.example.eshop.querydsl.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "order_item")
class OrderItemEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0,

    @Column(name = "product_id", nullable = false)
    var productId: Long = 0,

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0,

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO
)

