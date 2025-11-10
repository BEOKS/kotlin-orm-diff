package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.exposed.table.Customers
import com.example.eshop.exposed.table.Orders
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeParseException

class ExposedCustomerRepository : CustomerRepository {

    override fun save(customer: Customer): Customer = transaction {
        Customers.insert {
            it[id] = customer.id.value
            it[name] = customer.name
            it[email] = customer.email
            it[address] = customer.address
            it[registeredDate] = customer.registeredDate
        }
        customer
    }

    override fun findById(id: CustomerId): Customer? = transaction {
        Customers.selectAll()
            .where { Customers.id eq id.value }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override fun findAll(): List<Customer> = transaction {
        Customers.selectAll()
            .map { it.toCustomer() }
    }

    override fun update(customer: Customer): Customer = transaction {
        val updatedRows = Customers.update({ Customers.id eq customer.id.value }) {
            it[name] = customer.name
            it[email] = customer.email
            it[address] = customer.address
            it[registeredDate] = customer.registeredDate
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Customer with id ${customer.id.value} not found")
        }
        customer
    }

    override fun delete(id: CustomerId): Boolean = transaction {
        val deleted = Customers.deleteWhere { Customers.id eq id.value }
        deleted > 0
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> = transaction {
        Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate
            )
            .groupBy(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate
            )
            .having { Orders.totalAmount.sum() greaterEq minAmount.amount }
            .map { row ->
                Customer(
                    id = CustomerId(row[Customers.id]),
                    name = row[Customers.name],
                    email = row[Customers.email],
                    address = row[Customers.address],
                    registeredDate = row[Customers.registeredDate]
                )
            }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> = transaction {
        require(startDate.isNotBlank()) { "startDate cannot be blank" }
        require(endDate.isNotBlank()) { "endDate cannot be blank" }

        val start = try {
            LocalDate.parse(startDate)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid startDate format: $startDate", e)
        }

        val end = try {
            LocalDate.parse(endDate)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid endDate format: $endDate", e)
        }

        require(end >= start) { "endDate must be >= startDate" }

        // Convert LocalDate to LocalDateTime for comparison
        val startDateTime = start.atStartOfDay()
        val endDateTime = end.plusDays(1).atStartOfDay()

        Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .selectAll()
            .where {
                (Orders.orderDate greaterEq startDateTime) and (Orders.orderDate less endDateTime)
            }
            .withDistinct()
            .map { it.toCustomer() }
    }

    private fun ResultRow.toCustomer() = Customer(
        id = CustomerId(this[Customers.id]),
        name = this[Customers.name],
        email = this[Customers.email],
        address = this[Customers.address],
        registeredDate = this[Customers.registeredDate]
    )
}

