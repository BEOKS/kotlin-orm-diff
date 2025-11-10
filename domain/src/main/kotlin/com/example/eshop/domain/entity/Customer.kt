package com.example.eshop.domain.entity

import com.example.eshop.domain.valueobject.CustomerId
import java.time.LocalDate

data class Customer(
    val id: CustomerId,
    val name: String,
    val email: String,
    val address: String,
    val registeredDate: LocalDate
) {
    init {
        require(name.isNotBlank()) { "Customer name cannot be blank" }
        require(email.contains("@")) { "Invalid email format" }
        require(address.isNotBlank()) { "Customer address cannot be blank" }
    }
}

