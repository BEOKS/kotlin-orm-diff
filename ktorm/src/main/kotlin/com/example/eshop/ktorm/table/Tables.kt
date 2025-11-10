package com.example.eshop.ktorm.table

import org.ktorm.schema.*
import java.time.LocalDate
import java.time.LocalDateTime

object Customers : Table<Nothing>("customer") {
    val id = long("id").primaryKey()
    val name = varchar("name")
    val email = varchar("email")
    val address = varchar("address")
    val registeredDate = date("registered_date")
}

object Products : Table<Nothing>("product") {
    val id = long("id").primaryKey()
    val name = varchar("name")
    val price = decimal("price")
    val stock = int("stock")
    val category = varchar("category")
}

object Orders : Table<Nothing>("orders") {
    val id = long("id").primaryKey()
    val customerId = long("customer_id")
    val orderDate = datetime("order_date")
    val totalAmount = decimal("total_amount")
    val status = varchar("status")
}

object OrderItems : Table<Nothing>("order_item") {
    val id = long("id").primaryKey()
    val orderId = long("order_id")
    val productId = long("product_id")
    val quantity = int("quantity")
    val price = decimal("price")
}

object Payments : Table<Nothing>("payment") {
    val id = long("id").primaryKey()
    val orderId = long("order_id")
    val amount = decimal("amount")
    val paymentDate = datetime("payment_date")
    val method = varchar("method")
    val status = varchar("status")
}
