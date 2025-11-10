package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Order
import com.example.eshop.domain.repository.*
import com.example.eshop.domain.valueobject.*
import com.example.eshop.querydsl.entity.*
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class QueryDslOrderRepository(private val em: EntityManager) : OrderRepository {

    private val queryFactory = JPAQueryFactory(em)
    private val qOrder = QOrderEntity.orderEntity
    private val qCustomer = QCustomerEntity.customerEntity
    private val qPayment = QPaymentEntity.paymentEntity
    private val qOrderItem = QOrderItemEntity.orderItemEntity
    private val qProduct = QProductEntity.productEntity

    override fun save(order: Order): Order {
        val entity = OrderEntity(
            id = order.id.value,
            customerId = order.customerId.value,
            orderDate = order.orderDate,
            totalAmount = order.totalAmount.amount,
            status = order.status.name
        )
        em.persist(entity)
        return order
    }

    override fun findById(id: OrderId): Order? {
        return queryFactory
            .selectFrom(qOrder)
            .where(qOrder.id.eq(id.value))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(): List<Order> {
        return queryFactory
            .selectFrom(qOrder)
            .fetch()
            .map { it.toDomain() }
    }

    override fun update(order: Order): Order {
        val entity = em.find(OrderEntity::class.java, order.id.value)
            ?: throw IllegalArgumentException("Order not found: ${order.id.value}")

        entity.customerId = order.customerId.value
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
        return queryFactory
            .select(qOrder, qPayment.status, qOrderItem.id.count().intValue(), qCustomer.name)
            .from(qOrder)
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .leftJoin(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .leftJoin(qOrderItem).on(qOrder.id.eq(qOrderItem.orderId))
            .where(qOrder.customerId.eq(customerId.value))
            .groupBy(qOrder.id, qOrder.customerId, qOrder.orderDate, qOrder.totalAmount,
                qOrder.status, qPayment.status, qCustomer.name)
            .fetch()
            .map { tuple ->
                val orderEntity = tuple.get(qOrder)!!
                OrderWithDetails(
                    order = orderEntity.toDomain(),
                    paymentStatus = tuple.get(qPayment.status),
                    itemCount = tuple.get(qOrderItem.id.count().intValue())!!,
                    customerName = tuple.get(qCustomer.name)!!
                )
            }
    }

    override fun findUnpaidOrdersWithDetails(): List<OrderWithCustomer> {
        return queryFactory
            .select(qOrder, qCustomer.name, qCustomer.email)
            .from(qOrder)
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .leftJoin(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(
                qPayment.id.isNull
                    .or(qPayment.status.eq(PaymentStatus.PENDING.name))
            )
            .fetch()
            .map { tuple ->
                OrderWithCustomer(
                    order = tuple.get(qOrder)!!.toDomain(),
                    customerName = tuple.get(qCustomer.name)!!,
                    customerEmail = tuple.get(qCustomer.email)!!
                )
            }
    }

    override fun countOrdersByStatus(): Map<OrderStatus, Long> {
        return queryFactory
            .select(qOrder.status, qOrder.count())
            .from(qOrder)
            .groupBy(qOrder.status)
            .fetch()
            .associate { tuple ->
                OrderStatus.valueOf(tuple.get(qOrder.status)!!) to tuple.get(qOrder.count())!!
            }
    }

    override fun searchOrders(criteria: OrderSearchCriteria): List<OrderSearchResult> {
        // 동적 WHERE 조건 생성
        val whereClause = buildWhereClause(criteria)

        // 정렬 조건 결정
        val orderBy = buildOrderBy(criteria)

        // GROUP_CONCAT을 위한 표현식
        val productNamesExpr = Expressions.stringTemplate(
            "group_concat({0})",
            qProduct.name
        )

        return queryFactory
            .select(
                qOrder,
                qCustomer.name,
                qCustomer.email,
                qOrderItem.id.count().intValue(),
                productNamesExpr,
                qPayment.status
            )
            .from(qOrder)
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .leftJoin(qOrderItem).on(qOrder.id.eq(qOrderItem.orderId))
            .leftJoin(qProduct).on(qOrderItem.productId.eq(qProduct.id))
            .leftJoin(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(whereClause)
            .groupBy(qOrder.id, qOrder.customerId, qOrder.orderDate, qOrder.totalAmount,
                qOrder.status, qCustomer.name, qCustomer.email, qPayment.status)
            .orderBy(orderBy)
            .offset(criteria.offset.toLong())
            .limit(criteria.limit.toLong())
            .fetch()
            .map { tuple ->
                val orderEntity = tuple.get(qOrder)!!
                val productNamesStr = tuple.get(productNamesExpr)
                val productNames = productNamesStr?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: emptyList()

                OrderSearchResult(
                    order = orderEntity.toDomain(),
                    customerName = tuple.get(qCustomer.name)!!,
                    customerEmail = tuple.get(qCustomer.email)!!,
                    itemCount = tuple.get(qOrderItem.id.count().intValue())!!,
                    productNames = productNames,
                    paymentStatus = tuple.get(qPayment.status)
                )
            }
    }

    override fun countOrders(criteria: OrderSearchCriteria): Long {
        // 동적 WHERE 조건 생성 (searchOrders와 동일)
        val whereClause = buildWhereClause(criteria)

        // DISTINCT order ID 카운트
        return queryFactory
            .selectDistinct(qOrder.id)
            .from(qOrder)
            .join(qCustomer).on(qOrder.customerId.eq(qCustomer.id))
            .leftJoin(qOrderItem).on(qOrder.id.eq(qOrderItem.orderId))
            .leftJoin(qProduct).on(qOrderItem.productId.eq(qProduct.id))
            .leftJoin(qPayment).on(qOrder.id.eq(qPayment.orderId))
            .where(whereClause)
            .fetch()
            .size
            .toLong()
    }

    /**
     * 검색 조건에 따라 동적으로 WHERE 절 생성
     */
    private fun buildWhereClause(criteria: OrderSearchCriteria): BooleanBuilder {
        val builder = BooleanBuilder()

        // Order 테이블 조건
        criteria.orderDateFrom?.let {
            builder.and(qOrder.orderDate.goe(it))
        }
        criteria.orderDateTo?.let {
            builder.and(qOrder.orderDate.loe(it))
        }
        criteria.minTotalAmount?.let {
            builder.and(qOrder.totalAmount.goe(it.amount))
        }
        criteria.maxTotalAmount?.let {
            builder.and(qOrder.totalAmount.loe(it.amount))
        }
        criteria.orderStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            builder.and(qOrder.status.`in`(statuses.map { it.name }))
        }

        // Customer 테이블 조건
        criteria.customerName?.takeIf { it.isNotBlank() }?.let {
            builder.and(qCustomer.name.containsIgnoreCase(it))
        }
        criteria.customerEmail?.takeIf { it.isNotBlank() }?.let {
            builder.and(qCustomer.email.containsIgnoreCase(it))
        }
        criteria.customerAddress?.takeIf { it.isNotBlank() }?.let {
            builder.and(qCustomer.address.containsIgnoreCase(it))
        }
        criteria.customerRegisteredDateFrom?.let {
            builder.and(qCustomer.registeredDate.goe(it))
        }
        criteria.customerRegisteredDateTo?.let {
            builder.and(qCustomer.registeredDate.loe(it))
        }

        // Product 테이블 조건
        criteria.productName?.takeIf { it.isNotBlank() }?.let {
            builder.and(qProduct.name.containsIgnoreCase(it))
        }
        criteria.productCategory?.takeIf { it.isNotBlank() }?.let {
            builder.and(qProduct.category.eq(it))
        }
        criteria.minProductPrice?.let {
            builder.and(qProduct.price.goe(it.amount))
        }
        criteria.maxProductPrice?.let {
            builder.and(qProduct.price.loe(it.amount))
        }

        // Payment 테이블 조건
        criteria.paymentMethods?.takeIf { it.isNotEmpty() }?.let { methods ->
            builder.and(qPayment.method.`in`(methods.map { it.name }))
        }
        criteria.paymentStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            builder.and(qPayment.status.`in`(statuses.map { it.name }))
        }
        criteria.paymentDateFrom?.let {
            builder.and(qPayment.paymentDate.goe(it))
        }
        criteria.paymentDateTo?.let {
            builder.and(qPayment.paymentDate.loe(it))
        }

        return builder
    }

    /**
     * 정렬 조건 생성
     */
    private fun buildOrderBy(criteria: OrderSearchCriteria): OrderSpecifier<*> {
        val sortField = when (criteria.sortBy) {
            OrderSortField.ORDER_DATE -> qOrder.orderDate
            OrderSortField.TOTAL_AMOUNT -> qOrder.totalAmount
            OrderSortField.CUSTOMER_NAME -> qCustomer.name
            OrderSortField.ORDER_STATUS -> qOrder.status
        }

        return if (criteria.sortDirection == SortDirection.ASC) {
            sortField.asc()
        } else {
            sortField.desc()
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
