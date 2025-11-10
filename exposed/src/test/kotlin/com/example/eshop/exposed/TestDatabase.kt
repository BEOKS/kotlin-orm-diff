package com.example.eshop.exposed

import com.example.eshop.exposed.table.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction

object TestDatabase {
    private const val URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
    private const val DRIVER = "org.h2.Driver"
    private const val USER = "sa"
    private const val PASSWORD = ""

    fun connect(): Database {
        return Database.connect(
            url = URL,
            driver = DRIVER,
            user = USER,
            password = PASSWORD
        )
    }

    fun initSchema(db: Database) {
        transaction(db) {
            addLogger(org.jetbrains.exposed.sql.StdOutSqlLogger)
            SchemaUtils.create(Customers, Products, Orders, OrderItems, Payments)
        }
    }

    fun cleanDatabase(db: Database) {
        transaction(db) {
            SchemaUtils.drop(Payments, OrderItems, Orders, Products, Customers)
            SchemaUtils.create(Customers, Products, Orders, OrderItems, Payments)
        }
    }
}

