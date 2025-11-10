package com.example.eshop.ktorm.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.OrderRepository
import com.example.eshop.domain.repository.OrderWithCustomer
import com.example.eshop.domain.repository.OrderWithDetails
import com.example.eshop.domain.valueobject.*
import com.example.eshop.ktorm.table.Customers
import com.example.eshop.ktorm.table.OrderItems
import com.example.eshop.ktorm.table.Orders
import com.example.eshop.ktorm.table.Payments
import com.example.eshop.ktorm.util.toEnumOrThrow
import org.ktorm.database.Database
import org.ktorm.dsl.*

class KtormOrderRepository(private val database: Database) : OrderRepository {

    override fun save(order: Order): Order {
        database.insert(Orders) {
            set(it.id, order.id.value)
            set(it.customerId, order.customerId.value)
            set(it.orderDate, order.orderDate)
            set(it.totalAmount, order.totalAmount.amount)
            set(it.status, order.status.name)
        }
        return order
    }

    override fun findById(id: OrderId): Order? {
        return database.from(Orders)
            .select()
            .where { Orders.id eq id.value }
            .map { row -> row.toOrder() }
            .singleOrNull()
    }

    override fun findAll(): List<Order> {
        return database.from(Orders)
            .select()
            .map { row -> row.toOrder() }
    }

    override fun update(order: Order): Order {
        val updatedRows = database.update(Orders) {
            set(it.customerId, order.customerId.value)
            set(it.orderDate, order.orderDate)
            set(it.totalAmount, order.totalAmount.amount)
            set(it.status, order.status.name)
            where { it.id eq order.id.value }
        }
        if (updatedRows == 0) {
            throw IllegalStateException("Order with id ${order.id.value} not found")
        }
        return order
    }

    override fun delete(id: OrderId): Boolean {
        val deleted = database.delete(Orders) { it.id eq id.value }
        return deleted > 0
    }

    override fun findOrdersWithPaymentAndItems(customerId: CustomerId): List<OrderWithDetails> {
        val itemCountAlias = count(OrderItems.id).aliased("item_count")

        return database.from(Orders)
            .innerJoin(Customers, on = Orders.customerId eq Customers.id)
            .leftJoin(Payments, on = Orders.id eq Payments.orderId)
            .leftJoin(OrderItems, on = Orders.id eq OrderItems.orderId)
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
                        id = OrderId(row[Orders.id]!!),
                        customerId = CustomerId(row[Orders.customerId]!!),
                        orderDate = row[Orders.orderDate]!!,
                        totalAmount = Money(row[Orders.totalAmount]!!),
                        status = row[Orders.status]!!.toEnumOrThrow<OrderStatus>()
                    ),
                    paymentStatus = row[Payments.status],
                    itemCount = (row[itemCountAlias] as? Number)?.toInt() ?: 0,
                    customerName = row[Customers.name]!!
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        return database.from(Orders)
            .innerJoin(Customers, on = Orders.customerId eq Customers.id)
            .leftJoin(Payments, on = Orders.id eq Payments.orderId)
            .select()
            .where {
                Payments.id.isNull() or (Payments.status eq PaymentStatus.PENDING.name)
            }
            .map { row ->
                OrderWithCustomer(
                    order = Order(
                        id = OrderId(row[Orders.id]!!),
                        customerId = CustomerId(row[Orders.customerId]!!),
                        orderDate = row[Orders.orderDate]!!,
                        totalAmount = Money(row[Orders.totalAmount]!!),
                        status = row[Orders.status]!!.toEnumOrThrow<OrderStatus>()
                    ),
                    customerName = row[Customers.name]!!,
                    customerEmail = row[Customers.email]!!
                )
            }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        val countAlias = count(Orders.id).aliased("count")

        return database.from(Orders)
            .select(Orders.status, countAlias)
            .groupBy(Orders.status)
            .map { row ->
                Pair(
                    row[Orders.status]!!.toEnumOrThrow<OrderStatus>(),
                    (row[countAlias] as? Number)?.toLong() ?: 0L
                )
            }
            .toMap()
    }

    private fun QueryRowSet.toOrder() = Order(
        id = OrderId(this[Orders.id]!!),
        customerId = CustomerId(this[Orders.customerId]!!),
        orderDate = this[Orders.orderDate]!!,
        totalAmount = Money(this[Orders.totalAmount]!!),
        status = this[Orders.status]!!.toEnumOrThrow<OrderStatus>()
    )
}
