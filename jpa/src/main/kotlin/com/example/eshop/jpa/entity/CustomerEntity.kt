package com.example.eshop.jpa.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "customer")
class CustomerEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false, length = 100)
    var name: String = "",

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String = "",

    @Column(name = "address", nullable = false, length = 500)
    var address: String = "",

    @Column(name = "registered_date", nullable = false)
    var registeredDate: LocalDate = LocalDate.now()
) {
    @OneToMany(mappedBy = "customer", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var orders: MutableList<OrderEntity> = mutableListOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CustomerEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String {
        return "CustomerEntity(id=$id, name='$name', email='$email')"
    }
}
