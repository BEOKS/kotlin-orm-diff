package com.example.eshop.springdatajpa.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class PaymentEntity(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "order_id", nullable = false)
    var orderId: Long,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @Column(name = "payment_date", nullable = false)
    var paymentDate: LocalDateTime,

    @Column(name = "method", nullable = false)
    var method: String,

    @Column(name = "status", nullable = false)
    var status: String
) {
    // JPA requires a no-arg constructor
    protected constructor() : this(0, 0, BigDecimal.ZERO, LocalDateTime.now(), "", "")
}

