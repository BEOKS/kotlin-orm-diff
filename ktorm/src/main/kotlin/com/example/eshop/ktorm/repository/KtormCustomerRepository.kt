package com.example.eshop.ktorm.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.ktorm.table.Customers
import com.example.eshop.ktorm.table.Orders
import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

class KtormCustomerRepository(private val database: Database) : CustomerRepository {

    override fun save(customer: Customer): Customer {
        database.insert(Customers) {
            set(it.id, customer.id.value)
            set(it.name, customer.name)
            set(it.email, customer.email)
            set(it.address, customer.address)
            set(it.registeredDate, customer.registeredDate)
        }
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        return database.from(Customers)
            .select()
            .where { Customers.id eq id.value }
            .map { row -> row.toCustomer() }
            .singleOrNull()
    }

    override fun findAll(): List<Customer> {
        return database.from(Customers)
            .select()
            .map { row -> row.toCustomer() }
    }

    override fun update(customer: Customer): Customer {
        val updatedRows = database.update(Customers) {
            set(it.name, customer.name)
            set(it.email, customer.email)
            set(it.address, customer.address)
            set(it.registeredDate, customer.registeredDate)
            where { it.id eq customer.id.value }
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Customer with id ${customer.id.value} not found")
        }
        return customer
    }

    override fun delete(id: CustomerId): Boolean {
        val deleted = database.delete(Customers) { it.id eq id.value }
        return deleted > 0
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        val totalAmountAlias = sum(Orders.totalAmount).aliased("total_amount")

        return database.from(Customers)
            .innerJoin(Orders, on = Customers.id eq Orders.customerId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate,
                totalAmountAlias
            )
            .groupBy(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate
            )
            .map { row ->
                val total = (row[totalAmountAlias] as? Number)?.toDouble() ?: 0.0
                Pair(row, total)
            }
            .filter { (_, total) -> total >= minAmount.amount.toDouble() }
            .map { (row, _) ->
                Customer(
                    id = CustomerId(row[Customers.id]!!),
                    name = row[Customers.name]!!,
                    email = row[Customers.email]!!,
                    address = row[Customers.address]!!,
                    registeredDate = row[Customers.registeredDate]!!
                )
            }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
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

        return database.from(Customers)
            .innerJoin(Orders, on = Customers.id eq Orders.customerId)
            .select(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate
            )
            .where { (Orders.orderDate greaterEq startDateTime) and (Orders.orderDate less endDateTime) }
            .groupBy(
                Customers.id,
                Customers.name,
                Customers.email,
                Customers.address,
                Customers.registeredDate
            )
            .map { row -> row.toCustomer() }
    }

    private fun QueryRowSet.toCustomer() = Customer(
        id = CustomerId(this[Customers.id]!!),
        name = this[Customers.name]!!,
        email = this[Customers.email]!!,
        address = this[Customers.address]!!,
        registeredDate = this[Customers.registeredDate]!!
    )
}
