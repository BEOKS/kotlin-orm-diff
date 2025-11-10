package com.example.eshop.domain.valueobject

@JvmInline
value class PaymentId(val value: Long) {
    init {
        require(value > 0) { "PaymentId must be positive" }
    }
}

