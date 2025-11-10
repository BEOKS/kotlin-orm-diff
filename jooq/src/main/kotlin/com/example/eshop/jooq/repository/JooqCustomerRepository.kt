package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import com.example.eshop.jooq.generated.tables.references.ORDERS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDate

class JooqCustomerRepository(private val dsl: DSLContext) : CustomerRepository {

    override fun save(customer: Customer): Customer {
        dsl.insertInto(CUSTOMER)
            .set(CUSTOMER.ID, customer.id.value)
            .set(CUSTOMER.NAME, customer.name)
            .set(CUSTOMER.EMAIL, customer.email)
            .set(CUSTOMER.ADDRESS, customer.address)
            .set(CUSTOMER.REGISTERED_DATE, customer.registeredDate)
            .execute()
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        return dsl.select(CUSTOMER.ID, CUSTOMER.NAME, CUSTOMER.EMAIL, CUSTOMER.ADDRESS, CUSTOMER.REGISTERED_DATE)
            .from(CUSTOMER)
            .where(CUSTOMER.ID.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Customer(
                    id = CustomerId(record.getValue(CUSTOMER.ID)!!),
                    name = record.getValue(CUSTOMER.NAME)!!,
                    email = record.getValue(CUSTOMER.EMAIL)!!,
                    address = record.getValue(CUSTOMER.ADDRESS)!!,
                    registeredDate = record.getValue(CUSTOMER.REGISTERED_DATE)!!
                )
            }
    }

    override fun findAll(): List<Customer> {
        return dsl.select(CUSTOMER.ID, CUSTOMER.NAME, CUSTOMER.EMAIL, CUSTOMER.ADDRESS, CUSTOMER.REGISTERED_DATE)
            .from(CUSTOMER)
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(CUSTOMER.ID)!!),
                    name = record.getValue(CUSTOMER.NAME)!!,
                    email = record.getValue(CUSTOMER.EMAIL)!!,
                    address = record.getValue(CUSTOMER.ADDRESS)!!,
                    registeredDate = record.getValue(CUSTOMER.REGISTERED_DATE)!!
                )
            }
    }

    override fun update(customer: Customer): Customer {
        dsl.update(CUSTOMER)
            .set(CUSTOMER.NAME, customer.name)
            .set(CUSTOMER.EMAIL, customer.email)
            .set(CUSTOMER.ADDRESS, customer.address)
            .set(CUSTOMER.REGISTERED_DATE, customer.registeredDate)
            .where(CUSTOMER.ID.eq(customer.id.value))
            .execute()
        return customer
    }

    override fun delete(id: CustomerId): Boolean {
        val deleted = dsl.deleteFrom(CUSTOMER)
            .where(CUSTOMER.ID.eq(id.value))
            .execute()
        return deleted > 0
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")

        return dsl.select(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .groupBy(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .having(DSL.sum(o.TOTAL_AMOUNT).ge(minAmount.amount))
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(c.ID)!!),
                    name = record.getValue(c.NAME)!!,
                    email = record.getValue(c.EMAIL)!!,
                    address = record.getValue(c.ADDRESS)!!,
                    registeredDate = record.getValue(c.REGISTERED_DATE)!!
                )
            }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")

        return dsl.selectDistinct(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .where(c.REGISTERED_DATE.between(LocalDate.parse(startDate), LocalDate.parse(endDate)))
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(c.ID)!!),
                    name = record.getValue(c.NAME)!!,
                    email = record.getValue(c.EMAIL)!!,
                    address = record.getValue(c.ADDRESS)!!,
                    registeredDate = record.getValue(c.REGISTERED_DATE)!!
                )
            }
    }

}
