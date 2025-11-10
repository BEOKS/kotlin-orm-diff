package com.example.eshop.querydsl.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class PaymentEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "payment_date", nullable = false)
    var paymentDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "method", nullable = false)
    var method: String = "",

    @Column(name = "status", nullable = false)
    var status: String = ""
)

