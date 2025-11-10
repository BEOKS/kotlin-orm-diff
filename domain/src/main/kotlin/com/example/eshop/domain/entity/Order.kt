package com.example.eshop.domain.entity

import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderStatus
import java.time.LocalDateTime

data class Order(
    val id: OrderId,
    val customerId: CustomerId,
    val orderDate: LocalDateTime,
    val totalAmount: Money,
    val status: OrderStatus
) {
    init {
        require(totalAmount.isPositive() || totalAmount.isZero()) { "Order total amount cannot be negative" }
    }

    fun isCompleted(): Boolean = status == OrderStatus.DELIVERED
    fun isCancelled(): Boolean = status == OrderStatus.CANCELLED
    fun isPending(): Boolean = status == OrderStatus.PENDING
}

