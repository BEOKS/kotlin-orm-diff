package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.exposed.table.Customers
import com.example.eshop.exposed.table.Orders
import com.example.eshop.exposed.table.Payments
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ExposedPaymentRepository : PaymentRepository {

    override fun save(payment: Payment): Payment {
        Payments.insert {
            it[id] = payment.id.value
            it[orderId] = payment.orderId.value
            it[amount] = payment.amount.amount
            it[paymentDate] = payment.paymentDate
            it[method] = payment.method.name
            it[status] = payment.status.name
        }
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return Payments.selectAll()
            .where { Payments.id eq id.value }
            .map { it.toPayment() }
            .singleOrNull()
    }

    override fun findAll(): List<Payment> {
        return Payments.selectAll()
            .map { it.toPayment() }
    }

    override fun update(payment: Payment): Payment {
        Payments.update({ Payments.id eq payment.id.value }) {
            it[orderId] = payment.orderId.value
            it[amount] = payment.amount.amount
            it[paymentDate] = payment.paymentDate
            it[method] = payment.method.name
            it[status] = payment.status.name
        }
        return payment
    }

    override fun delete(id: PaymentId): Boolean {
        val deleted = Payments.deleteWhere { Payments.id eq id.value }
        return deleted > 0
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        val totalPaymentsAlias = Payments.amount.sum()
        val avgPaymentAlias = Payments.amount.avg()
        val paymentCountAlias = Payments.id.count()
        val orderCountAlias = Orders.id.countDistinct()
        
        return Customers
            .join(Orders, JoinType.INNER, Customers.id, Orders.customerId)
            .join(Payments, JoinType.INNER, Orders.id, Payments.orderId)
            .select(
                Customers.id,
                Customers.name,
                totalPaymentsAlias,
                avgPaymentAlias,
                paymentCountAlias,
                orderCountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Customers.id, Customers.name)
            .map { row ->
                CustomerStatistics(
                    customerId = row[Customers.id],
                    customerName = row[Customers.name],
                    totalPayments = Money(row[totalPaymentsAlias] ?: java.math.BigDecimal.ZERO),
                    averagePayment = Money(row[avgPaymentAlias] ?: java.math.BigDecimal.ZERO),
                    paymentCount = row[paymentCountAlias],
                    completedOrderCount = row[orderCountAlias]
                )
            }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        val totalAmountAlias = Payments.amount.sum()
        val countAlias = Payments.id.count()
        val avgAmountAlias = Payments.amount.avg()
        
        return Payments
            .select(
                Payments.method,
                totalAmountAlias,
                countAlias,
                avgAmountAlias
            )
            .where { Payments.status eq PaymentStatus.COMPLETED.name }
            .groupBy(Payments.method)
            .associate { row ->
                val method = PaymentMethod.valueOf(row[Payments.method])
                method to PaymentMethodStats(
                    method = method,
                    totalAmount = Money(row[totalAmountAlias] ?: java.math.BigDecimal.ZERO),
                    count = row[countAlias],
                    averageAmount = Money(row[avgAmountAlias] ?: java.math.BigDecimal.ZERO)
                )
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return Payments.selectAll()
            .where { Payments.orderId eq orderId.value }
            .map { it.toPayment() }
            .singleOrNull()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        return Payments
            .join(Orders, JoinType.INNER, Payments.orderId, Orders.id)
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .selectAll()
            .where { Payments.status eq PaymentStatus.FAILED.name }
            .map { row ->
                PaymentWithDetails(
                    payment = Payment(
                        id = PaymentId(row[Payments.id]),
                        orderId = OrderId(row[Payments.orderId]),
                        amount = Money(row[Payments.amount]),
                        paymentDate = row[Payments.paymentDate],
                        method = PaymentMethod.valueOf(row[Payments.method]),
                        status = PaymentStatus.valueOf(row[Payments.status])
                    ),
                    orderDate = row[Orders.orderDate].toString(),
                    customerName = row[Customers.name],
                    customerEmail = row[Customers.email]
                )
            }
    }

    private fun ResultRow.toPayment() = Payment(
        id = PaymentId(this[Payments.id]),
        orderId = OrderId(this[Payments.orderId]),
        amount = Money(this[Payments.amount]),
        paymentDate = this[Payments.paymentDate],
        method = PaymentMethod.valueOf(this[Payments.method]),
        status = PaymentStatus.valueOf(this[Payments.status])
    )
}

