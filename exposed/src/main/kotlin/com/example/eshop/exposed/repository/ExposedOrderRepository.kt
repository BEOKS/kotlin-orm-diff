package com.example.eshop.exposed.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.exposed.table.Customers
import com.example.eshop.exposed.table.OrderItems
import com.example.eshop.exposed.table.Orders
import com.example.eshop.exposed.table.Payments
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ExposedOrderRepository : OrderRepository {

    override fun save(order: Order): Order {
        Orders.insert {
            it[id] = order.id.value
            it[customerId] = order.customerId.value
            it[orderDate] = order.orderDate
            it[totalAmount] = order.totalAmount.amount
            it[status] = order.status.name
        }
        return order
    }

    override fun findById(id: OrderId): Order? {
        return Orders.selectAll()
            .where { Orders.id eq id.value }
            .map { it.toOrder() }
            .singleOrNull()
    }

    override fun findAll(): List<Order> {
        return Orders.selectAll()
            .map { it.toOrder() }
    }

    override fun update(order: Order): Order {
        Orders.update({ Orders.id eq order.id.value }) {
            it[customerId] = order.customerId.value
            it[orderDate] = order.orderDate
            it[totalAmount] = order.totalAmount.amount
            it[status] = order.status.name
        }
        return order
    }

    override fun delete(id: OrderId): Boolean {
        val deleted = Orders.deleteWhere { Orders.id eq id.value }
        return deleted > 0
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        val itemCountAlias = OrderItems.id.count()
        
        return Orders
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .join(Payments, JoinType.LEFT, Orders.id, Payments.orderId)
            .join(OrderItems, JoinType.LEFT, Orders.id, OrderItems.orderId)
            .select(
                Orders.id,
                Orders.customerId,
                Orders.orderDate,
                Orders.totalAmount,
                Orders.status,
                Customers.name,
                Payments.status,
                itemCountAlias
            )
            .where { Orders.customerId eq customerId.value }
            .groupBy(
                Orders.id,
                Orders.customerId,
                Orders.orderDate,
                Orders.totalAmount,
                Orders.status,
                Customers.name,
                Payments.status
            )
            .map { row ->
                OrderWithDetails(
                    order = Order(
                        id = OrderId(row[Orders.id]),
                        customerId = CustomerId(row[Orders.customerId]),
                        orderDate = row[Orders.orderDate],
                        totalAmount = Money(row[Orders.totalAmount]),
                        status = OrderStatus.valueOf(row[Orders.status])
                    ),
                    paymentStatus = row.getOrNull(Payments.status),
                    itemCount = row[itemCountAlias].toInt(),
                    customerName = row[Customers.name]
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        return Orders
            .join(Customers, JoinType.INNER, Orders.customerId, Customers.id)
            .join(Payments, JoinType.LEFT, Orders.id, Payments.orderId)
            .selectAll()
            .where {
                Payments.id.isNull() or (Payments.status eq PaymentStatus.PENDING.name)
            }
            .map { row ->
                OrderWithCustomer(
                    order = Order(
                        id = OrderId(row[Orders.id]),
                        customerId = CustomerId(row[Orders.customerId]),
                        orderDate = row[Orders.orderDate],
                        totalAmount = Money(row[Orders.totalAmount]),
                        status = OrderStatus.valueOf(row[Orders.status])
                    ),
                    customerName = row[Customers.name],
                    customerEmail = row[Customers.email]
                )
            }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        return Orders
            .select(Orders.status, Orders.id.count())
            .groupBy(Orders.status)
            .associate { row ->
                OrderStatus.valueOf(row[Orders.status]) to row[Orders.id.count()]
            }
    }

    private fun ResultRow.toOrder() = Order(
        id = OrderId(this[Orders.id]),
        customerId = CustomerId(this[Orders.customerId]),
        orderDate = this[Orders.orderDate],
        totalAmount = Money(this[Orders.totalAmount]),
        status = OrderStatus.valueOf(this[Orders.status])
    )
}

