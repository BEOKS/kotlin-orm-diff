package com.example.eshop.querydsl.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "customer")
class CustomerEntity(
    @Id
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "email", nullable = false)
    var email: String = "",

    @Column(name = "address", nullable = false)
    var address: String = "",

    @Column(name = "registered_date", nullable = false)
    var registeredDate: LocalDate = LocalDate.now()
)

