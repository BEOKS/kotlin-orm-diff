package com.example.eshop.springdatajpa.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "product")
class ProductEntity(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    var price: BigDecimal,

    @Column(name = "stock", nullable = false)
    var stock: Int,

    @Column(name = "category", nullable = false)
    var category: String
) {
    // JPA requires a no-arg constructor
    protected constructor() : this(0, "", BigDecimal.ZERO, 0, "")
}

