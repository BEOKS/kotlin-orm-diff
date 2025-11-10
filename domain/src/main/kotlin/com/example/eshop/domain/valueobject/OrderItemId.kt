package com.example.eshop.domain.valueobject

@JvmInline
value class OrderItemId(val value: Long) {
    init {
        require(value > 0) { "OrderItemId must be positive" }
    }
}

