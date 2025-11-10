package com.example.eshop.domain.valueobject

@JvmInline
value class ProductId(val value: Long) {
    init {
        require(value > 0) { "ProductId must be positive" }
    }
}

