package com.example.eshop.domain.entity

import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.PaymentId
import com.example.eshop.domain.valueobject.PaymentMethod
import com.example.eshop.domain.valueobject.PaymentStatus
import java.time.LocalDateTime

data class Payment(
    val id: PaymentId,
    val orderId: OrderId,
    val amount: Money,
    val paymentDate: LocalDateTime,
    val method: PaymentMethod,
    val status: PaymentStatus
) {
    init {
        require(amount.isPositive()) { "Payment amount must be positive" }
    }

    fun isCompleted(): Boolean = status == PaymentStatus.COMPLETED
    fun isFailed(): Boolean = status == PaymentStatus.FAILED
    fun isPending(): Boolean = status == PaymentStatus.PENDING
}

