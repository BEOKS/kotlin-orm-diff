package com.example.eshop.springdatajpa.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "customer")
class CustomerEntity(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "email", nullable = false)
    var email: String,

    @Column(name = "address", nullable = false)
    var address: String,

    @Column(name = "registered_date", nullable = false)
    var registeredDate: LocalDate
) {
    // JPA requires a no-arg constructor
    protected constructor() : this(0, "", "", "", LocalDate.now())
}

