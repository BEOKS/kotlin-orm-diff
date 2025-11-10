package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.exposed.table.Customers
import com.example.eshop.exposed.table.Orders
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate

class ExposedCustomerRepository : CustomerRepository {

    override fun save(customer: Customer): Customer {
        Customers.insert {
            it[id] = customer.id.value
            it[name] = customer.name
            it[email] = customer.email
            it[address] = customer.address
            it[registeredDate] = customer.registeredDate
        }
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        return Customers.selectAll()
            .where { Customers.id eq id.value }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override fun findAll(): List<Customer> {
        return Customers.selectAll()
            .map { it.toCustomer() }
    }

    override fun update(customer: Customer): Customer {
        Customers.update({ Customers.id eq customer.id.value }) {
            it[name] = customer.name
            it[email] = customer.email
            it[address] = customer.address
            it[registeredDate] = customer.registeredDate
        }
        return customer
    }

    override fun delete(id: CustomerId): Boolean {
        val deleted = Customers.deleteWhere { Customers.id eq id.value }
        return deleted > 0
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        return Customers
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

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        
        return Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .selectAll()
            .where {
                (Customers.registeredDate greaterEq start) and (Customers.registeredDate lessEq end)
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

