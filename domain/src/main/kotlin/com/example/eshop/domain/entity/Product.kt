package com.example.eshop.domain.entity

import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.ProductId

data class Product(
    val id: ProductId,
    val name: String,
    val price: Money,
    val stock: Int,
    val category: String
) {
    init {
        require(name.isNotBlank()) { "Product name cannot be blank" }
        require(price.isPositive()) { "Product price must be positive" }
        require(stock >= 0) { "Product stock cannot be negative" }
        require(category.isNotBlank()) { "Product category cannot be blank" }
    }

    fun isAvailable(): Boolean = stock > 0
}

