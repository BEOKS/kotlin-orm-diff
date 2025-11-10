package com.example.eshop.jooq

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager

object TestDatabase {
    private const val URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
    private const val USER = "sa"
    private const val PASSWORD = ""

    fun createConnection(): Connection {
        return DriverManager.getConnection(URL, USER, PASSWORD)
    }

    fun createDSLContext(connection: Connection): DSLContext {
        return DSL.using(connection, SQLDialect.H2)
    }

    fun initSchema(connection: Connection) {
        val schema = TestDatabase::class.java.getResource("/schema.sql")?.readText()
            ?: throw IllegalStateException("Schema file not found")
        
        connection.createStatement().use { statement ->
            schema.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { sql ->
                    statement.execute(sql)
                }
        }
    }

    fun cleanDatabase(dsl: DSLContext) {
        dsl.execute("SET REFERENTIAL_INTEGRITY FALSE")
        dsl.execute("TRUNCATE TABLE payment")
        dsl.execute("TRUNCATE TABLE order_item")
        dsl.execute("TRUNCATE TABLE orders")
        dsl.execute("TRUNCATE TABLE product")
        dsl.execute("TRUNCATE TABLE customer")
        dsl.execute("SET REFERENTIAL_INTEGRITY TRUE")
    }
}

