package com.example.eshop.domain.entity

import com.example.eshop.domain.valueobject.Money
import com.example.eshop.domain.valueobject.OrderId
import com.example.eshop.domain.valueobject.OrderItemId
import com.example.eshop.domain.valueobject.ProductId

data class OrderItem(
    val id: OrderItemId,
    val orderId: OrderId,
    val productId: ProductId,
    val quantity: Int,
    val price: Money
) {
    init {
        require(quantity > 0) { "Order item quantity must be positive" }
        require(price.isPositive()) { "Order item price must be positive" }
    }

    fun calculateSubtotal(): Money = price * quantity
}

