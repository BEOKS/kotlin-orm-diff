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
import java.math.BigDecimal
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
        val cId = DSL.field(CustomerFields.ID, Long::class.java)
        val cName = DSL.field(CustomerFields.NAME, String::class.java)
        val cEmail = DSL.field(CustomerFields.EMAIL, String::class.java)
        val cAddress = DSL.field(CustomerFields.ADDRESS, String::class.java)
        val cRegDate = DSL.field(CustomerFields.REGISTERED_DATE, LocalDate::class.java)
        
        return dsl.select(cId, cName, cEmail, cAddress, cRegDate)
            .from(Tables.CUSTOMER)
            .where(cId.eq(id.value))
            .fetchOne()
            ?.let { record ->
                Customer(
                    id = CustomerId(record.getValue(cId)),
                    name = record.getValue(cName),
                    email = record.getValue(cEmail),
                    address = record.getValue(cAddress),
                    registeredDate = record.getValue(cRegDate)
                )
            }
    }

    override fun findAll(): List<Customer> {
        val cId = DSL.field(CustomerFields.ID, Long::class.java)
        val cName = DSL.field(CustomerFields.NAME, String::class.java)
        val cEmail = DSL.field(CustomerFields.EMAIL, String::class.java)
        val cAddress = DSL.field(CustomerFields.ADDRESS, String::class.java)
        val cRegDate = DSL.field(CustomerFields.REGISTERED_DATE, LocalDate::class.java)
        
        return dsl.select(cId, cName, cEmail, cAddress, cRegDate)
            .from(Tables.CUSTOMER)
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(cId)),
                    name = record.getValue(cName),
                    email = record.getValue(cEmail),
                    address = record.getValue(cAddress),
                    registeredDate = record.getValue(cRegDate)
                )
            }
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
        val cId = DSL.field("c.${CustomerFields.ID}", Long::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val cEmail = DSL.field("c.${CustomerFields.EMAIL}", String::class.java)
        val cAddress = DSL.field("c.${CustomerFields.ADDRESS}", String::class.java)
        val cRegDate = DSL.field("c.${CustomerFields.REGISTERED_DATE}", LocalDate::class.java)
        val oTotalAmount = DSL.field("o.${OrderFields.TOTAL_AMOUNT}", BigDecimal::class.java)

        return dsl.select(cId, cName, cEmail, cAddress, cRegDate)
            .from("${Tables.CUSTOMER} c")
            .join("${Tables.ORDER} o")
            .on("c.${CustomerFields.ID} = o.${OrderFields.CUSTOMER_ID}")
            .groupBy(cId, cName, cEmail, cAddress, cRegDate)
            .having(DSL.sum(oTotalAmount).ge(minAmount.amount))
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(cId)),
                    name = record.getValue(cName),
                    email = record.getValue(cEmail),
                    address = record.getValue(cAddress),
                    registeredDate = record.getValue(cRegDate)
                )
            }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val cId = DSL.field("c.${CustomerFields.ID}", Long::class.java)
        val cName = DSL.field("c.${CustomerFields.NAME}", String::class.java)
        val cEmail = DSL.field("c.${CustomerFields.EMAIL}", String::class.java)
        val cAddress = DSL.field("c.${CustomerFields.ADDRESS}", String::class.java)
        val cRegDate = DSL.field("c.${CustomerFields.REGISTERED_DATE}", LocalDate::class.java)

        return dsl.selectDistinct(cId, cName, cEmail, cAddress, cRegDate)
            .from("${Tables.CUSTOMER} c")
            .join("${Tables.ORDER} o")
            .on("c.${CustomerFields.ID} = o.${OrderFields.CUSTOMER_ID}")
            .where(cRegDate.between(LocalDate.parse(startDate), LocalDate.parse(endDate)))
            .fetch()
            .map { record ->
                Customer(
                    id = CustomerId(record.getValue(cId)),
                    name = record.getValue(cName),
                    email = record.getValue(cEmail),
                    address = record.getValue(cAddress),
                    registeredDate = record.getValue(cRegDate)
                )
            }
    }

}
