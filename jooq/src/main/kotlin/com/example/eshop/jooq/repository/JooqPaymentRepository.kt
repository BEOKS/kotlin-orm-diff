package com.example.eshop.jooq.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jooq.generated.tables.references.PAYMENT
import com.example.eshop.jooq.generated.tables.references.ORDERS
import com.example.eshop.jooq.generated.tables.references.CUSTOMER
import com.example.eshop.jooq.util.MoneyConstants
import mu.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

class JooqPaymentRepository(private val dsl: DSLContext) : PaymentRepository {

    // Private mapper functions
    private fun mapToPayment(record: Record): Payment? {
        return try {
            Payment(
                id = PaymentId(record.getValue(PAYMENT.ID) ?: return null),
                orderId = OrderId(record.getValue(PAYMENT.ORDER_ID) ?: return null),
                amount = Money(record.getValue(PAYMENT.AMOUNT) ?: return null),
                paymentDate = record.getValue(PAYMENT.PAYMENT_DATE) ?: return null,
                method = record.getValue(PAYMENT.METHOD)?.let {
                    PaymentMethod.valueOf(it)
                } ?: return null,
                status = record.getValue(PAYMENT.STATUS)?.let {
                    PaymentStatus.valueOf(it)
                } ?: return null
            )
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "Failed to map record to Payment: Invalid enum value" }
            null
        }
    }

    override fun save(payment: Payment): Payment {
        logger.debug { "Saving payment: ${payment.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                // Check for duplicate
                val exists = ctx.fetchExists(
                    ctx.selectFrom(PAYMENT).where(PAYMENT.ID.eq(payment.id.value))
                )

                if (exists) {
                    logger.warn { "Payment with id ${payment.id.value} already exists" }
                    throw IllegalStateException("Payment with id ${payment.id.value} already exists")
                }

                ctx.insertInto(PAYMENT)
                    .set(PAYMENT.ID, payment.id.value)
                    .set(PAYMENT.ORDER_ID, payment.orderId.value)
                    .set(PAYMENT.AMOUNT, payment.amount.amount)
                    .set(PAYMENT.PAYMENT_DATE, payment.paymentDate)
                    .set(PAYMENT.METHOD, payment.method.name)
                    .set(PAYMENT.STATUS, payment.status.name)
                    .execute()

                logger.info { "Successfully saved payment: ${payment.id.value}" }
                payment
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save payment: ${payment.id.value}" }
            throw e
        }
    }

    override fun findById(id: PaymentId): Payment? {
        logger.debug { "Finding payment by id: ${id.value}" }

        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT,
            PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .where(PAYMENT.ID.eq(id.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found payment: ${id.value}" }
                mapToPayment(it)
            }
            ?: run {
                logger.debug { "Payment not found: ${id.value}" }
                null
            }
    }

    override fun findAll(): List<Payment> {
        logger.debug { "Finding all payments" }

        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT,
            PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .fetch()
            .mapNotNull { mapToPayment(it) }
            .also { logger.debug { "Found ${it.size} payments" } }
    }

    override fun update(payment: Payment): Payment {
        logger.debug { "Updating payment: ${payment.id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val rowsAffected = ctx.update(PAYMENT)
                    .set(PAYMENT.ORDER_ID, payment.orderId.value)
                    .set(PAYMENT.AMOUNT, payment.amount.amount)
                    .set(PAYMENT.PAYMENT_DATE, payment.paymentDate)
                    .set(PAYMENT.METHOD, payment.method.name)
                    .set(PAYMENT.STATUS, payment.status.name)
                    .where(PAYMENT.ID.eq(payment.id.value))
                    .execute()

                if (rowsAffected == 0) {
                    logger.warn { "Payment with id ${payment.id.value} not found for update" }
                    throw IllegalStateException("Payment with id ${payment.id.value} not found")
                }

                logger.info { "Successfully updated payment: ${payment.id.value}" }
                payment
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to update payment: ${payment.id.value}" }
            throw e
        }
    }

    override fun delete(id: PaymentId): Boolean {
        logger.debug { "Deleting payment: ${id.value}" }

        return try {
            dsl.transactionResult { configuration ->
                val ctx = DSL.using(configuration)

                val deleted = ctx.deleteFrom(PAYMENT)
                    .where(PAYMENT.ID.eq(id.value))
                    .execute()

                val result = deleted > 0
                if (result) {
                    logger.info { "Successfully deleted payment: ${id.value}" }
                } else {
                    logger.warn { "Payment not found for deletion: ${id.value}" }
                }
                result
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete payment: ${id.value}" }
            throw e
        }
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        logger.debug { "Calculating customer order statistics" }

        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")
        val p = PAYMENT.`as`("p")

        val totalPay = DSL.sum(p.AMOUNT).`as`("total_payments")
        val avgPay = DSL.avg(p.AMOUNT).`as`("avg_payment")
        val payCnt = DSL.count(p.ID).`as`("payment_count")
        val ordCnt = DSL.countDistinct(o.ID).`as`("order_count")

        return dsl.select(c.ID, c.NAME, totalPay, avgPay, payCnt, ordCnt)
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .join(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.ID, c.NAME)
            .fetch()
            .mapNotNull { record ->
                try {
                    CustomerStatistics(
                        customerId = record.getValue(c.ID) ?: return@mapNotNull null,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null,
                        totalPayments = Money(
                            (record.get("total_payments", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        averagePayment = Money(
                            (record.get("avg_payment", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                        completedOrderCount = record.get("order_count", Long::class.java) ?: 0L
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map customer statistics record" }
                    null
                }
            }
            .also { logger.debug { "Calculated statistics for ${it.size} customers" } }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        logger.debug { "Calculating payment method statistics" }

        val totalAmt = DSL.sum(PAYMENT.AMOUNT).`as`("total_amount")
        val cnt = DSL.count().`as`("count")
        val avgAmt = DSL.avg(PAYMENT.AMOUNT).`as`("avg_amount")

        return dsl.select(PAYMENT.METHOD, totalAmt, cnt, avgAmt)
            .from(PAYMENT)
            .where(PAYMENT.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(PAYMENT.METHOD)
            .fetch()
            .mapNotNull { record ->
                try {
                    val methodStr = record.getValue(PAYMENT.METHOD) ?: return@mapNotNull null
                    val payMethod = PaymentMethod.valueOf(methodStr)
                    payMethod to PaymentMethodStats(
                        method = payMethod,
                        totalAmount = Money(
                            (record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        count = record.get("count", Long::class.java) ?: 0L,
                        averageAmount = Money(
                            (record.get("avg_amount", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    logger.error(e) { "Invalid payment method in statistics" }
                    null
                }
            }
            .toMap()
            .also { logger.debug { "Calculated statistics for ${it.size} payment methods" } }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        logger.debug { "Finding payment by order id: ${orderId.value}" }

        return dsl.select(PAYMENT.ID, PAYMENT.ORDER_ID, PAYMENT.AMOUNT,
            PAYMENT.PAYMENT_DATE, PAYMENT.METHOD, PAYMENT.STATUS)
            .from(PAYMENT)
            .where(PAYMENT.ORDER_ID.eq(orderId.value))
            .fetchOne()
            ?.let {
                logger.debug { "Found payment for order: ${orderId.value}" }
                mapToPayment(it)
            }
            ?: run {
                logger.debug { "Payment not found for order: ${orderId.value}" }
                null
            }
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        logger.debug { "Finding failed payments with details" }

        val p = PAYMENT.`as`("p")
        val o = ORDERS.`as`("o")
        val c = CUSTOMER.`as`("c")

        return dsl.select(p.ID, p.ORDER_ID, p.AMOUNT, p.PAYMENT_DATE, p.METHOD, p.STATUS,
            o.ORDER_DATE, c.NAME, c.EMAIL)
            .from(p)
            .join(o).on(p.ORDER_ID.eq(o.ID))
            .join(c).on(o.CUSTOMER_ID.eq(c.ID))
            .where(p.STATUS.eq(PaymentStatus.FAILED.name))
            .fetch()
            .mapNotNull { record ->
                try {
                    val payment = Payment(
                        id = PaymentId(record.getValue(p.ID) ?: return@mapNotNull null),
                        orderId = OrderId(record.getValue(p.ORDER_ID) ?: return@mapNotNull null),
                        amount = Money(record.getValue(p.AMOUNT) ?: return@mapNotNull null),
                        paymentDate = record.getValue(p.PAYMENT_DATE) ?: return@mapNotNull null,
                        method = record.getValue(p.METHOD)?.let {
                            PaymentMethod.valueOf(it)
                        } ?: return@mapNotNull null,
                        status = record.getValue(p.STATUS)?.let {
                            PaymentStatus.valueOf(it)
                        } ?: return@mapNotNull null
                    )

                    PaymentWithDetails(
                        payment = payment,
                        orderDate = record.getValue(o.ORDER_DATE)?.toString() ?: return@mapNotNull null,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null,
                        customerEmail = record.getValue(c.EMAIL) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map failed payment with details" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} failed payments" } }
    }

    /**
     * 프리미엄 고객을 조회합니다.
     *
     * SQL의 CASE WHEN을 사용하여 단일 쿼리로 고객 타입을 결정합니다.
     * 이전 UNION 방식보다 효율적이며, 한 번의 집계로 모든 정보를 계산합니다.
     *
     * @param minTotalAmount 고액 결제자 기준 최소 총 결제 금액
     * @param minPaymentCount 빈번한 결제자 기준 최소 결제 횟수
     * @return 프리미엄 고객 정보 목록
     */
    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        logger.debug { "Finding premium customers (minAmount: ${minTotalAmount.amount}, minCount: $minPaymentCount)" }

        val c = CUSTOMER.`as`("c")
        val o = ORDERS.`as`("o")
        val p = PAYMENT.`as`("p")

        val totalAmount = DSL.sum(p.AMOUNT)
        val paymentCount = DSL.count(p.ID)

        // CASE WHEN을 사용하여 SQL에서 직접 타입 결정
        val customerType = DSL.case_()
            .`when`(
                totalAmount.ge(minTotalAmount.amount)
                    .and(paymentCount.ge(minPaymentCount.toInt())),
                DSL.inline("BOTH")
            )
            .`when`(
                totalAmount.ge(minTotalAmount.amount),
                DSL.inline("HIGH_VALUE")
            )
            .`when`(
                paymentCount.ge(minPaymentCount.toInt()),
                DSL.inline("FREQUENT_PAYER")
            )
            .`as`("customer_type")

        return dsl
            .select(
                c.ID, c.NAME, c.EMAIL,
                totalAmount.`as`("total_amount"),
                paymentCount.`as`("payment_count"),
                customerType
            )
            .from(c)
            .join(o).on(c.ID.eq(o.CUSTOMER_ID))
            .join(p).on(o.ID.eq(p.ORDER_ID))
            .where(p.STATUS.eq(PaymentStatus.COMPLETED.name))
            .groupBy(c.ID, c.NAME, c.EMAIL)
            .having(
                totalAmount.ge(minTotalAmount.amount)
                    .or(paymentCount.ge(minPaymentCount.toInt()))
            )
            .fetch()
            .mapNotNull { record ->
                try {
                    val typeStr = record.get("customer_type", String::class.java) ?: return@mapNotNull null

                    PremiumCustomerInfo(
                        customerId = record.getValue(c.ID) ?: return@mapNotNull null,
                        customerName = record.getValue(c.NAME) ?: return@mapNotNull null,
                        customerEmail = record.getValue(c.EMAIL) ?: return@mapNotNull null,
                        totalPaymentAmount = Money(
                            (record.get("total_amount", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                        customerType = PremiumCustomerType.valueOf(typeStr)
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map premium customer info" }
                    null
                }
            }
            .also { logger.info { "Found ${it.size} premium customers" } }
    }

    override fun findMonthlyPaymentTrends(): List<MonthlyPaymentTrend> {
        logger.debug { "Finding monthly payment trends" }

        // jOOQ는 Native Query를 resultQuery() 메서드로 실행할 수 있습니다.
        // https://www.jooq.org/doc/latest/manual/sql-building/plain-sql/

        val sql = """
            WITH monthly_stats AS (
                SELECT
                    strftime('%Y-%m', payment_date) as year_month,
                    SUM(amount) as total_amount,
                    COUNT(*) as payment_count,
                    AVG(amount) as average_amount
                FROM payment
                WHERE status = 'COMPLETED'
                GROUP BY strftime('%Y-%m', payment_date)
            )
            SELECT
                year_month,
                total_amount,
                payment_count,
                average_amount,
                LAG(total_amount) OVER (ORDER BY year_month) as previous_month_amount
            FROM monthly_stats
            ORDER BY year_month
        """.trimIndent()

        return dsl.resultQuery(sql)
            .fetch()
            .mapNotNull { record ->
                try {
                    val totalAmount = record.get("total_amount", BigDecimal::class.java)
                        ?: return@mapNotNull null
                    val previousMonthAmount = record.get("previous_month_amount", BigDecimal::class.java)

                    val growthRate = if (previousMonthAmount != null && previousMonthAmount > BigDecimal.ZERO) {
                        ((totalAmount - previousMonthAmount) / previousMonthAmount * BigDecimal(100)).toDouble()
                    } else null

                    MonthlyPaymentTrend(
                        yearMonth = record.get("year_month", String::class.java) ?: return@mapNotNull null,
                        totalAmount = Money(totalAmount.setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)),
                        paymentCount = record.get("payment_count", Long::class.java) ?: 0L,
                        averageAmount = Money(
                            (record.get("average_amount", BigDecimal::class.java) ?: BigDecimal.ZERO)
                                .setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE)
                        ),
                        previousMonthAmount = previousMonthAmount?.let {
                            Money(it.setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING_MODE))
                        },
                        growthRate = growthRate
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map monthly payment trend" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} monthly trends" } }
    }

    override fun findRecentPaymentsByAllCustomers(limit: Int): List<RecentPaymentInfo> {
        require(limit > 0) { "limit must be positive" }
        logger.debug { "Finding recent $limit payments by all customers" }

        val sql = """
            WITH ranked_payments AS (
                SELECT
                    p.id as payment_id,
                    p.order_id,
                    p.amount,
                    p.payment_date,
                    p.method,
                    p.status,
                    c.id as customer_id,
                    c.name as customer_name,
                    o.order_date,
                    ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY p.payment_date DESC) as row_num
                FROM payment p
                INNER JOIN orders o ON p.order_id = o.id
                INNER JOIN customer c ON o.customer_id = c.id
                WHERE p.status = 'COMPLETED'
            )
            SELECT *
            FROM ranked_payments
            WHERE row_num <= ?
            ORDER BY customer_id, row_num
        """.trimIndent()

        return dsl.resultQuery(sql, limit)
            .fetch()
            .mapNotNull { record ->
                try {
                    val payment = Payment(
                        id = PaymentId(record.get("payment_id", Long::class.java) ?: return@mapNotNull null),
                        orderId = OrderId(record.get("order_id", Long::class.java) ?: return@mapNotNull null),
                        amount = Money(record.get("amount", BigDecimal::class.java) ?: return@mapNotNull null),
                        paymentDate = record.get("payment_date", java.time.LocalDateTime::class.java) ?: return@mapNotNull null,
                        method = PaymentMethod.valueOf(
                            record.get("method", String::class.java) ?: return@mapNotNull null
                        ),
                        status = PaymentStatus.valueOf(
                            record.get("status", String::class.java) ?: return@mapNotNull null
                        )
                    )

                    RecentPaymentInfo(
                        customerId = record.get("customer_id", Long::class.java) ?: return@mapNotNull null,
                        customerName = record.get("customer_name", String::class.java) ?: return@mapNotNull null,
                        payment = payment,
                        orderDate = record.get("order_date", String::class.java) ?: return@mapNotNull null,
                        rowNumber = record.get("row_num", Int::class.java) ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to map recent payment info" }
                    null
                }
            }
            .also { logger.debug { "Found ${it.size} recent payment records" } }
    }
}
