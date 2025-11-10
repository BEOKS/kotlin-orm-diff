package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.springdatajpa.entity.OrderEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class SpringDataOrderRepository(
    private val orderEntityRepository: OrderEntityRepository,
    private val customerEntityRepository: CustomerEntityRepository,
    private val paymentEntityRepository: PaymentEntityRepository,
    private val orderItemEntityRepository: OrderItemEntityRepository
) : OrderRepository {

    @Transactional
    override fun save(order: Order): Order {
        // Check if entity exists
        val entity = if (order.id.value != 0L && orderEntityRepository.existsById(order.id.value)) {
            // Update existing entity
            orderEntityRepository.findById(order.id.value).get().apply {
                customerId = order.customerId.value
                orderDate = order.orderDate
                totalAmount = order.totalAmount.amount
                status = order.status.name
            }
        } else {
            // Create new entity
            OrderEntity(
                id = order.id.value,
                customerId = order.customerId.value,
                orderDate = order.orderDate,
                totalAmount = order.totalAmount.amount,
                status = order.status.name
            )
        }
        orderEntityRepository.save(entity)
        return order
    }

    override fun findById(id: OrderId): Order? {
        return orderEntityRepository.findById(id.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<Order> {
        return orderEntityRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun update(order: Order): Order {
        val entity = orderEntityRepository.findById(order.id.value)
            .orElseThrow { IllegalArgumentException("Order not found: ${order.id.value}") }

        entity.customerId = order.customerId.value
        entity.orderDate = order.orderDate
        entity.totalAmount = order.totalAmount.amount
        entity.status = order.status.name
        orderEntityRepository.save(entity)
        return order
    }

    @Transactional
    override fun delete(id: OrderId): Boolean {
        return if (orderEntityRepository.existsById(id.value)) {
            orderEntityRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        // N+1 문제 해결: 한 번의 쿼리로 모든 정보 조회
        return orderEntityRepository.findOrdersWithDetailsByCustomerId(customerId.value).map { row ->
            val order = (row[0] as OrderEntity).toDomain()
            val paymentStatus = row[1] as? String
            val itemCount = (row[2] as Number).toInt()
            val customerName = row[3] as String

            OrderWithDetails(
                order = order,
                paymentStatus = paymentStatus,
                itemCount = itemCount,
                customerName = customerName
            )
        }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        val unpaidOrders = orderEntityRepository.findUnpaidOrders()
        
        return unpaidOrders.mapNotNull { order ->
            customerEntityRepository.findById(order.customerId).map { customer ->
                OrderWithCustomer(
                    order = order.toDomain(),
                    customerName = customer.name,
                    customerEmail = customer.email
                )
            }.orElse(null)
        }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        return orderEntityRepository.countOrdersByStatus()
            .associate { row ->
                val status = OrderStatus.valueOf(row[0] as String)
                val count = (row[1] as Number).toLong()
                status to count
            }
    }

    private fun OrderEntity.toDomain(): Order {
        return Order(
            id = OrderId(id),
            customerId = CustomerId(customerId),
            orderDate = orderDate,
            totalAmount = Money(totalAmount),
            status = OrderStatus.valueOf(status)
        )
    }
}

