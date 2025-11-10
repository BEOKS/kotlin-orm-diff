package com.example.eshop.domain.valueobject

@JvmInline
value class CustomerId(val value: Long) {
    init {
        require(value > 0) { "CustomerId must be positive" }
    }
}

