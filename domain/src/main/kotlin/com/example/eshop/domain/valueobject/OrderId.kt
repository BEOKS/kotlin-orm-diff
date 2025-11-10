package com.example.eshop.domain.valueobject

@JvmInline
value class OrderId(val value: Long) {
    init {
        require(value > 0) { "OrderId must be positive" }
    }
}

