package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.jpa.entity.CustomerEntity
import com.example.eshop.jpa.entity.OrderEntity
import jakarta.persistence.EntityManager

class JpaOrderRepository(private val em: EntityManager) : OrderRepository {

    override fun save(order: Order): Order {
        // Find the customer entity to establish relationship
        val customer = em.find(CustomerEntity::class.java, order.customerId.value)
            ?: throw IllegalArgumentException("Customer not found: ${order.customerId.value}")

        val entity = OrderEntity(
            id = 0, // Auto-generated
            customer = customer,
            orderDate = order.orderDate,
            totalAmount = order.totalAmount.amount,
            status = order.status.name
        )
        em.persist(entity)
        em.flush() // Ensure ID is generated

        // Return new Order with generated ID
        return Order(
            id = OrderId(entity.id),
            customerId = order.customerId,
            orderDate = order.orderDate,
            totalAmount = order.totalAmount,
            status = order.status
        )
    }

    override fun findById(id: OrderId): Order? {
        return em.find(OrderEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<Order> {
        return em.createQuery("SELECT o FROM OrderEntity o ORDER BY o.id", OrderEntity::class.java)
            .resultList
            .map { it.toDomain() }
    }

    override fun update(order: Order): Order {
        val entity = em.find(OrderEntity::class.java, order.id.value)
            ?: throw IllegalArgumentException("Order not found: ${order.id.value}")

        // Find the customer entity if it changed
        val customer = em.find(CustomerEntity::class.java, order.customerId.value)
            ?: throw IllegalArgumentException("Customer not found: ${order.customerId.value}")

        // Dirty checking - no need for merge()
        entity.customer = customer
        entity.orderDate = order.orderDate
        entity.totalAmount = order.totalAmount.amount
        entity.status = order.status.name

        return order
    }

    override fun delete(id: OrderId): Boolean {
        val entity = em.find(OrderEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        val query = em.createQuery("""
            SELECT
                o.id, o.customer.id, o.orderDate, o.totalAmount, o.status,
                o.customer.name,
                o.payment.status,
                SIZE(o.orderItems)
            FROM OrderEntity o
            WHERE o.customer.id = :customerId
        """)

        query.setParameter("customerId", customerId.value)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            OrderWithDetails(
                order = Order(
                    id = OrderId(row[0] as Long),
                    customerId = CustomerId(row[1] as Long),
                    orderDate = row[2] as java.time.LocalDateTime,
                    totalAmount = Money(row[3] as java.math.BigDecimal),
                    status = OrderStatus.valueOf(row[4] as String)
                ),
                paymentStatus = row[6] as String?,
                itemCount = (row[7] as Int),
                customerName = row[5] as String
            )
        }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        val query = em.createQuery("""
            SELECT o, o.customer.name, o.customer.email
            FROM OrderEntity o
            LEFT JOIN o.payment p
            WHERE p.id IS NULL OR p.status = 'PENDING'
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).map { row ->
            val orderEntity = row[0] as OrderEntity
            OrderWithCustomer(
                order = orderEntity.toDomain(),
                customerName = row[1] as String,
                customerEmail = row[2] as String
            )
        }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        val query = em.createQuery("""
            SELECT o.status, COUNT(o)
            FROM OrderEntity o
            GROUP BY o.status
        """)

        @Suppress("UNCHECKED_CAST")
        return (query.resultList as List<Array<Any>>).associate { row ->
            OrderStatus.valueOf(row[0] as String) to (row[1] as Long)
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
