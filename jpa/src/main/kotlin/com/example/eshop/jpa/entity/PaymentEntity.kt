package com.example.eshop.jpa.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class PaymentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    var order: OrderEntity? = null,

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "payment_date", nullable = false)
    var paymentDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "method", nullable = false, length = 50)
    var method: String = "",

    @Column(name = "status", nullable = false, length = 50)
    var status: String = ""
) {
    // Helper property for backward compatibility
    val orderId: Long
        get() = order?.id ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String {
        return "PaymentEntity(id=$id, orderId=$orderId, amount=$amount, paymentDate=$paymentDate, method='$method', status='$status')"
    }
}
