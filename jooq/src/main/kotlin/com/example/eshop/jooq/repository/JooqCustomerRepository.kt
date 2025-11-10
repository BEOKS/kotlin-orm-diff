package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.jooq.schema.CustomerFields
import com.example.eshop.jooq.schema.OrderFields
import com.example.eshop.jooq.schema.Tables
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDate

class JooqCustomerRepository(private val dsl: DSLContext) : CustomerRepository {

    override fun save(customer: Customer): Customer {
        dsl.insertInto(DSL.table(Tables.CUSTOMER))
            .set(DSL.field(CustomerFields.ID), customer.id.value)
            .set(DSL.field(CustomerFields.NAME), customer.name)
            .set(DSL.field(CustomerFields.EMAIL), customer.email)
            .set(DSL.field(CustomerFields.ADDRESS), customer.address)
            .set(DSL.field(CustomerFields.REGISTERED_DATE), customer.registeredDate)
            .execute()
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        return dsl.select()
            .from(Tables.CUSTOMER)
            .where(DSL.field(CustomerFields.ID).eq(id.value))
            .fetchOne()
            ?.let { mapToCustomer(it) }
    }

    override fun findAll(): List<Customer> {
        return dsl.select()
            .from(Tables.CUSTOMER)
            .fetch()
            .map { mapToCustomer(it) }
    }

    override fun update(customer: Customer): Customer {
        dsl.update(DSL.table(Tables.CUSTOMER))
            .set(DSL.field(CustomerFields.NAME), customer.name)
            .set(DSL.field(CustomerFields.EMAIL), customer.email)
            .set(DSL.field(CustomerFields.ADDRESS), customer.address)
            .set(DSL.field(CustomerFields.REGISTERED_DATE), customer.registeredDate)
            .where(DSL.field(CustomerFields.ID).eq(customer.id.value))
            .execute()
        return customer
    }

    override fun delete(id: CustomerId): Boolean {
        val deleted = dsl.deleteFrom(DSL.table(Tables.CUSTOMER))
            .where(DSL.field(CustomerFields.ID).eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        val c = DSL.table(Tables.CUSTOMER).asTable("c")
        val o = DSL.table(Tables.ORDER).asTable("o")

        return dsl.select(
            c.field(CustomerFields.ID),
            c.field(CustomerFields.NAME),
            c.field(CustomerFields.EMAIL),
            c.field(CustomerFields.ADDRESS),
            c.field(CustomerFields.REGISTERED_DATE)
        )
            .from(c)
            .join(o).on(c.field(CustomerFields.ID).eq(o.field(OrderFields.CUSTOMER_ID)))
            .groupBy(
                c.field(CustomerFields.ID),
                c.field(CustomerFields.NAME),
                c.field(CustomerFields.EMAIL),
                c.field(CustomerFields.ADDRESS),
                c.field(CustomerFields.REGISTERED_DATE)
            )
            .having(DSL.sum(o.field(OrderFields.TOTAL_AMOUNT, Double::class.java)).ge(minAmount.amount.toDouble()))
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.get(c.field(CustomerFields.ID), Long::class.java)!!),
                    name = record.get(c.field(CustomerFields.NAME), String::class.java)!!,
                    email = record.get(c.field(CustomerFields.EMAIL), String::class.java)!!,
                    address = record.get(c.field(CustomerFields.ADDRESS), String::class.java)!!,
                    registeredDate = record.get(c.field(CustomerFields.REGISTERED_DATE), LocalDate::class.java)!!
                )
            }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val c = DSL.table(Tables.CUSTOMER).asTable("c")
        val o = DSL.table(Tables.ORDER).asTable("o")

        return dsl.selectDistinct(
            c.field(CustomerFields.ID),
            c.field(CustomerFields.NAME),
            c.field(CustomerFields.EMAIL),
            c.field(CustomerFields.ADDRESS),
            c.field(CustomerFields.REGISTERED_DATE)
        )
            .from(c)
            .join(o).on(c.field(CustomerFields.ID).eq(o.field(OrderFields.CUSTOMER_ID)))
            .where(
                c.field(CustomerFields.REGISTERED_DATE).between(LocalDate.parse(startDate), LocalDate.parse(endDate))
            )
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.get(c.field(CustomerFields.ID), Long::class.java)!!),
                    name = record.get(c.field(CustomerFields.NAME), String::class.java)!!,
                    email = record.get(c.field(CustomerFields.EMAIL), String::class.java)!!,
                    address = record.get(c.field(CustomerFields.ADDRESS), String::class.java)!!,
                    registeredDate = record.get(c.field(CustomerFields.REGISTERED_DATE), LocalDate::class.java)!!
                )
            }
    }

    private fun mapToCustomer(record: org.jooq.Record): Customer {
        return Customer(
            id = CustomerId(record.get(CustomerFields.ID, Long::class.java)!!),
            name = record.get(CustomerFields.NAME, String::class.java)!!,
            email = record.get(CustomerFields.EMAIL, String::class.java)!!,
            address = record.get(CustomerFields.ADDRESS, String::class.java)!!,
            registeredDate = record.get(CustomerFields.REGISTERED_DATE, LocalDate::class.java)!!
        )
    }
}

