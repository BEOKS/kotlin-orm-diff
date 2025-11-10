package com.example.eshop.jpa.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "product")
class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false, length = 255)
    var name: String = "",

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(name = "stock", nullable = false)
    var stock: Int = 0,

    @Column(name = "category", nullable = false, length = 100)
    var category: String = "",

    @Version
    @Column(name = "version")
    var version: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProductEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String {
        return "ProductEntity(id=$id, name='$name', price=$price, stock=$stock, category='$category')"
    }
}
