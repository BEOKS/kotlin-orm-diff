package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Payment
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.springdatajpa.entity.PaymentEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
@Transactional(readOnly = true)
class SpringDataPaymentRepository(
    private val paymentEntityRepository: PaymentEntityRepository
) : PaymentRepository {

    @Transactional
    override fun save(payment: Payment): Payment {
        // Check if entity exists
        val entity = if (payment.id.value != 0L && paymentEntityRepository.existsById(payment.id.value)) {
            // Update existing entity
            paymentEntityRepository.findById(payment.id.value).get().apply {
                orderId = payment.orderId.value
                amount = payment.amount.amount
                paymentDate = payment.paymentDate
                method = payment.method.name
                status = payment.status.name
            }
        } else {
            // Create new entity
            PaymentEntity(
                id = payment.id.value,
                orderId = payment.orderId.value,
                amount = payment.amount.amount,
                paymentDate = payment.paymentDate,
                method = payment.method.name,
                status = payment.status.name
            )
        }
        paymentEntityRepository.save(entity)
        return payment
    }

    override fun findById(id: PaymentId): Payment? {
        return paymentEntityRepository.findById(id.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<Payment> {
        return paymentEntityRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun update(payment: Payment): Payment {
        val entity = paymentEntityRepository.findById(payment.id.value)
            .orElseThrow { IllegalArgumentException("Payment not found: ${payment.id.value}") }

        entity.orderId = payment.orderId.value
        entity.amount = payment.amount.amount
        entity.paymentDate = payment.paymentDate
        entity.method = payment.method.name
        entity.status = payment.status.name
        paymentEntityRepository.save(entity)
        return payment
    }

    @Transactional
    override fun delete(id: PaymentId): Boolean {
        return if (paymentEntityRepository.existsById(id.value)) {
            paymentEntityRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }

    override fun calculateCustomerOrderStatistics(): List<CustomerStatistics> {
        return paymentEntityRepository.calculateCustomerOrderStatistics().map { row ->
            CustomerStatistics(
                customerId = (row[0] as Number).toLong(),
                customerName = row[1] as String,
                totalPayments = Money(BigDecimal.valueOf((row[2] as Number).toDouble())),
                averagePayment = Money(BigDecimal.valueOf((row[3] as Number).toDouble())),
                paymentCount = (row[4] as Number).toLong(),
                completedOrderCount = (row[5] as Number).toLong()
            )
        }
    }

    override fun calculatePaymentMethodStatistics(): Map<PaymentMethod, PaymentMethodStats> {
        return paymentEntityRepository.calculatePaymentMethodStatistics()
            .associate { row ->
                val method = PaymentMethod.valueOf(row[0] as String)
                val stats = PaymentMethodStats(
                    method = method,
                    totalAmount = Money(BigDecimal.valueOf((row[1] as Number).toDouble())),
                    count = (row[2] as Number).toLong(),
                    averageAmount = Money(BigDecimal.valueOf((row[3] as Number).toDouble()))
                )
                method to stats
            }
    }

    override fun findByOrderId(orderId: OrderId): Payment? {
        return paymentEntityRepository.findByOrderId(orderId.value)?.toDomain()
    }

    override fun findFailedPaymentsWithDetails(): List<PaymentWithDetails> {
        return paymentEntityRepository.findFailedPaymentsWithDetails().map { row ->
            val payment = (row[0] as PaymentEntity).toDomain()
            PaymentWithDetails(
                payment = payment,
                orderDate = (row[1] as LocalDateTime).toString(),
                customerName = row[2] as String,
                customerEmail = row[3] as String
            )
        }
    }

    override fun findPremiumCustomersWithUnion(
        minTotalAmount: Money,
        minPaymentCount: Long
    ): List<PremiumCustomerInfo> {
        return paymentEntityRepository.findPremiumCustomersWithUnion(
            minTotalAmount.amount,
            minPaymentCount
        ).map { row ->
            PremiumCustomerInfo(
                customerId = (row[0] as Number).toLong(),
                customerName = row[1] as String,
                customerEmail = row[2] as String,
                totalPaymentAmount = Money(row[3] as BigDecimal),
                paymentCount = (row[4] as Number).toLong(),
                customerType = PremiumCustomerType.valueOf(row[5] as String)
            )
        }
    }

    override fun findMonthlyPaymentTrends(): List<MonthlyPaymentTrend> {
        return paymentEntityRepository.findMonthlyPaymentTrends().map { row ->
            val totalAmount = BigDecimal.valueOf((row[1] as Number).toDouble())
            val previousMonthAmount = row[4]?.let {
                BigDecimal.valueOf((it as Number).toDouble())
            }

            val growthRate = if (previousMonthAmount != null && previousMonthAmount > BigDecimal.ZERO) {
                ((totalAmount - previousMonthAmount) / previousMonthAmount * BigDecimal(100)).toDouble()
            } else null

            MonthlyPaymentTrend(
                yearMonth = row[0] as String,
                totalAmount = Money(totalAmount),
                paymentCount = (row[2] as Number).toLong(),
                averageAmount = Money(BigDecimal.valueOf((row[3] as Number).toDouble())),
                previousMonthAmount = previousMonthAmount?.let { Money(it) },
                growthRate = growthRate
            )
        }
    }

    override fun findRecentPaymentsByAllCustomers(limit: Int): List<RecentPaymentInfo> {
        require(limit > 0) { "limit must be positive" }

        return paymentEntityRepository.findRecentPaymentsByAllCustomers(limit).map { row ->
            val payment = Payment(
                id = PaymentId((row[0] as Number).toLong()),
                orderId = OrderId((row[1] as Number).toLong()),
                amount = Money(row[2] as BigDecimal),
                paymentDate = row[3] as LocalDateTime,
                method = PaymentMethod.valueOf(row[4] as String),
                status = PaymentStatus.valueOf(row[5] as String)
            )

            RecentPaymentInfo(
                customerId = (row[6] as Number).toLong(),
                customerName = row[7] as String,
                payment = payment,
                orderDate = row[8] as String,
                rowNumber = (row[9] as Number).toInt()
            )
        }
    }

    private fun PaymentEntity.toDomain(): Payment {
        return Payment(
            id = PaymentId(id),
            orderId = OrderId(orderId),
            amount = Money(amount),
            paymentDate = paymentDate,
            method = PaymentMethod.valueOf(method),
            status = PaymentStatus.valueOf(status)
        )
    }
}

