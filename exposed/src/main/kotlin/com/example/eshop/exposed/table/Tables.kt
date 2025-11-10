package com.example.eshop.exposed.table

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

object Customers : Table("customer") {
    val id = long("id")
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val address = varchar("address", 500)
    val registeredDate = date("registered_date")
    
    override val primaryKey = PrimaryKey(id)
}

object Products : Table("product") {
    val id = long("id")
    val name = varchar("name", 255)
    val price = decimal("price", 19, 2)
    val stock = integer("stock")
    val category = varchar("category", 100)
    
    override val primaryKey = PrimaryKey(id)
}

object Orders : Table("orders") {
    val id = long("id")
    val customerId = long("customer_id").references(Customers.id)
    val orderDate = datetime("order_date")
    val totalAmount = decimal("total_amount", 19, 2)
    val status = varchar("status", 50)
    
    override val primaryKey = PrimaryKey(id)
}

object OrderItems : Table("order_item") {
    val id = long("id")
    val orderId = long("order_id").references(Orders.id)
    val productId = long("product_id").references(Products.id)
    val quantity = integer("quantity")
    val price = decimal("price", 19, 2)
    
    override val primaryKey = PrimaryKey(id)
}

object Payments : Table("payment") {
    val id = long("id")
    val orderId = long("order_id").references(Orders.id)
    val amount = decimal("amount", 19, 2)
    val paymentDate = datetime("payment_date")
    val method = varchar("method", 50)
    val status = varchar("status", 50)
    
    override val primaryKey = PrimaryKey(id)
}

