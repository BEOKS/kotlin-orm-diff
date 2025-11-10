package com.example.eshop.ktorm

import com.example.eshop.ktorm.table.*
import org.ktorm.database.Database
import org.ktorm.dsl.delete
import org.ktorm.logging.ConsoleLogger
import org.ktorm.logging.LogLevel

object TestDatabase {
    private const val URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
    private const val DRIVER = "org.h2.Driver"
    private const val USER = "sa"
    private const val PASSWORD = ""

    fun connect(): Database {
        return Database.connect(
            url = URL,
            driver = DRIVER,
            user = USER,
            password = PASSWORD,
            logger = ConsoleLogger(threshold = LogLevel.INFO)
        )
    }

    fun initSchema(db: Database) {
        db.useConnection { conn ->
            conn.createStatement().use { stmt ->
                // Create tables in order (respecting foreign key dependencies)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customer (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL,
                        address VARCHAR(500) NOT NULL,
                        registered_date DATE NOT NULL
                    )
                """)

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        price DECIMAL(19, 2) NOT NULL,
                        stock INT NOT NULL,
                        category VARCHAR(100) NOT NULL
                    )
                """)

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id BIGINT PRIMARY KEY,
                        customer_id BIGINT NOT NULL,
                        order_date DATETIME NOT NULL,
                        total_amount DECIMAL(19, 2) NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        FOREIGN KEY (customer_id) REFERENCES customer(id)
                    )
                """)

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS order_item (
                        id BIGINT PRIMARY KEY,
                        order_id BIGINT NOT NULL,
                        product_id BIGINT NOT NULL,
                        quantity INT NOT NULL,
                        price DECIMAL(19, 2) NOT NULL,
                        FOREIGN KEY (order_id) REFERENCES orders(id),
                        FOREIGN KEY (product_id) REFERENCES product(id)
                    )
                """)

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS payment (
                        id BIGINT PRIMARY KEY,
                        order_id BIGINT NOT NULL,
                        amount DECIMAL(19, 2) NOT NULL,
                        payment_date DATETIME NOT NULL,
                        method VARCHAR(50) NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        FOREIGN KEY (order_id) REFERENCES orders(id)
                    )
                """)
            }
        }
    }

    fun cleanDatabase(db: Database) {
        // Delete in reverse order to respect foreign key constraints
        db.useConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM payment")
                stmt.execute("DELETE FROM order_item")
                stmt.execute("DELETE FROM orders")
                stmt.execute("DELETE FROM product")
                stmt.execute("DELETE FROM customer")
            }
        }
    }
}
