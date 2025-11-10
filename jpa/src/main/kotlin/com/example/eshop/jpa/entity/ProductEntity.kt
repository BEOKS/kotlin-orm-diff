package com.example.eshop.jpa.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "product")
class ProductEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "stock", nullable = false)
    var stock: Int = 0,

    @Column(name = "category", nullable = false)
    var category: String = ""
)

