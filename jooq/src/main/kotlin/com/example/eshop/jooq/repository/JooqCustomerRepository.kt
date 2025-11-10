package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import com.example.eshop.jooq.generated.tables.references.ORDERS
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val logger = KotlinLogging.logger {}

class JooqCustomerRepository(private val dsl: DSLContext) : CustomerRepository {

    // Private mapper function
    private fun mapToCustomer(record: Record): Customer? {
        return try {
            Customer(
                id = CustomerId(record.getValue(CUSTOMER.ID) ?: return null),
                name = record.getValue(CUSTOMER.NAME) ?: return null,
                email = record.getValue(CUSTOMER.EMAIL) ?: return null,
                address = record.getValue(CUSTOMER.ADDRESS) ?: return null,
                registeredDate = record.getValue(CUSTOMER.REGISTERED_DATE) ?: return null
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to map record to Customer" }
            null
        }
    }

    override fun save(customer: Customer): Customer {
        logger.debug { "Saving customer: ${customer.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                // Check for duplicate
                val exists = ctx.fetchExists(
                    ctx.selectFrom(CUSTOMER).where(CUSTOMER.ID.eq(customer.id.value))
                )

                if (exists) {
                    logger.warn { "Customer with id ${customer.id.value} already exists" }
                    throw IllegalStateException("Customer with id ${customer.id.value} already exists")
                }

                ctx.insertInto(CUSTOMER)
                    .set(CUSTOMER.ID, customer.id.value)
                    .set(CUSTOMER.NAME, customer.name)
                    .set(CUSTOMER.EMAIL, customer.email)
                    .set(CUSTOMER.ADDRESS, customer.address)
                    .set(CUSTOMER.REGISTERED_DATE, customer.registeredDate)
                    .execute()

                logger.info { "Successfully saved customer: ${customer.id.value}" }
                customer
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save customer: ${customer.id.value}" }
            throw e
        }
    }

    override fun findById(id: CustomerId): Customer? {
        logger.debug { "Finding customer by id: ${id.value}" }

        return dsl.select(CUSTOMER.ID, CUSTOMER.NAME, CUSTOMER.EMAIL,
            CUSTOMER.ADDRESS, CUSTOMER.REGISTERED_DATE)
            .from(CUSTOMER)
            .where(CUSTOMER.ID.eq(id.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found customer: ${id.value}" }
                mapToCustomer(it)
            }
            ?: run {
                logger.debug { "Customer not found: ${id.value}" }
                null
            }
    }

    override fun findAll(): List<Customer> {
        logger.debug { "Finding all customers" }

        return dsl.select(CUSTOMER.ID, CUSTOMER.NAME, CUSTOMER.EMAIL,
            CUSTOMER.ADDRESS, CUSTOMER.REGISTERED_DATE)
            .from(CUSTOMER)
            .fetch()
            .mapNotNull { mapToCustomer(it) }
            .also { logger.debug { "Found ${it.size} customers" } }
    }

    override fun update(customer: Customer): Customer {
        logger.debug { "Updating customer: ${customer.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val rowsAffected = ctx.update(CUSTOMER)
                    .set(CUSTOMER.NAME, customer.name)
                    .set(CUSTOMER.EMAIL, customer.email)
                    .set(CUSTOMER.ADDRESS, customer.address)
                    .set(CUSTOMER.REGISTERED_DATE, customer.registeredDate)
                    .where(CUSTOMER.ID.eq(customer.id.value))
                    .execute()

                if (rowsAffected == 0) {
                    logger.warn { "Customer with id ${customer.id.value} not found for update" }
                    throw IllegalStateException("Customer with id ${customer.id.value} not found")
                }

                logger.info { "Successfully updated customer: ${customer.id.value}" }
                customer
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update customer: ${customer.id.value}" }
            throw e
        }
    }

    override fun delete(id: CustomerId): Boolean {
        logger.debug { "Deleting customer: ${id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val deleted = ctx.deleteFrom(CUSTOMER)
                    .where(CUSTOMER.ID.eq(id.value))
                    .execute()

                val result = deleted > 0
                if (result) {
                    logger.info { "Successfully deleted customer: ${id.value}" }
                } else {
                    logger.warn { "Customer not found for deletion: ${id.value}" }
                }
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete customer: ${id.value}" }
            throw e
        }
    }

    /**
     * 고액 주문 고객을 조회합니다.
     *
     * @param minAmount 최소 총 주문 금액
     * @return 조건을 만족하는 고객 목록
     */
    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        logger.debug { "Finding customers with high value orders (minAmount: ${minAmount.amount})" }

        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")

        return dsl.select(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .groupBy(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .having(DSL.sum(o.TOTAL_AMOUNT).ge(minAmount.amount))
            .fetch()
            .mapNotNull { mapToCustomer(it) }
            .also { logger.debug { "Found ${it.size} customers with high value orders" } }
    }

    /**
     * 특정 기간에 주문한 고객을 조회합니다.
     *
     * 주문 날짜(ORDER_DATE)를 기준으로 필터링합니다.
     *
     * @param startDate 시작 날짜 (yyyy-MM-dd 형식)
     * @param endDate 종료 날짜 (yyyy-MM-dd 형식)
     * @return 기간 내에 주문한 고객 목록
     * @throws IllegalArgumentException 날짜 형식이 잘못되었거나 시작일이 종료일보다 늦은 경우
     */
    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        logger.debug { "Finding customers with orders in period: $startDate to $endDate" }

        // Input validation
        val start = try {
            LocalDate.parse(startDate)
        } catch (e: DateTimeParseException) {
            logger.error(e) { "Invalid start date format: $startDate" }
            throw IllegalArgumentException("Invalid start date format: $startDate", e)
        }

        val end = try {
            LocalDate.parse(endDate)
        } catch (e: DateTimeParseException) {
            logger.error(e) { "Invalid end date format: $endDate" }
            throw IllegalArgumentException("Invalid end date format: $endDate", e)
        }

        if (start.isAfter(end)) {
            logger.error { "Start date $start is after end date $end" }
            throw IllegalArgumentException("Start date must be before or equal to end date")
        }

        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")

        return dsl.selectDistinct(c.ID, c.NAME, c.EMAIL, c.ADDRESS, c.REGISTERED_DATE)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .where(
                o.ORDER_DATE.between(start.atStartOfDay(), end.atTime(23, 59, 59))
            )
            .fetch()
            .mapNotNull { mapToCustomer(it) }
            .also { logger.debug { "Found ${it.size} customers with orders in period" } }
    }
}
