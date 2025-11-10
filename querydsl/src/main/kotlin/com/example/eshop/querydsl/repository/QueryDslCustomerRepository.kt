package com.example.eshop.querydsl.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.querydsl.entity.CustomerEntity
import com.example.eshop.querydsl.entity.QCustomerEntity
import com.example.eshop.querydsl.entity.QOrderEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class QueryDslCustomerRepository(private val em: EntityManager) : CustomerRepository {

    private val queryFactory = JPAQueryFactory(em)

    override fun save(customer: Customer): Customer {
        val entity = CustomerEntity(
            id = customer.id.value,
            name = customer.name,
            email = customer.email,
            address = customer.address,
            registeredDate = customer.registeredDate
        )
        em.persist(entity)
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        val qCustomer = QCustomerEntity.customerEntity
        
        return queryFactory
            .selectFrom(qCustomer)
            .where(qCustomer.id.eq(id.value))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(): List<Customer> {
        val qCustomer = QCustomerEntity.customerEntity
        
        return queryFactory
            .selectFrom(qCustomer)
            .fetch()
            .map { it.toDomain() }
    }

    override fun update(customer: Customer): Customer {
        val entity = em.find(CustomerEntity::class.java, customer.id.value)
            ?: throw IllegalArgumentException("Customer not found: ${customer.id.value}")
        
        entity.name = customer.name
        entity.email = customer.email
        entity.address = customer.address
        entity.registeredDate = customer.registeredDate
        em.merge(entity)
        return customer
    }

    override fun delete(id: CustomerId): Boolean {
        val entity = em.find(CustomerEntity::class.java, id.value) ?: return false
        em.remove(entity)
        return true
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        val qCustomer = QCustomerEntity.customerEntity
        val qOrder = QOrderEntity.orderEntity
        
        return queryFactory
            .selectFrom(qCustomer)
            .join(qOrder).on(qCustomer.id.eq(qOrder.customerId))
            .groupBy(qCustomer.id, qCustomer.name, qCustomer.email, qCustomer.address, qCustomer.registeredDate)
            .having(qOrder.totalAmount.sum().goe(minAmount.amount))
            .fetch()
            .map { it.toDomain() }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val qCustomer = QCustomerEntity.customerEntity
        val qOrder = QOrderEntity.orderEntity
        
        val start = java.time.LocalDate.parse(startDate)
        val end = java.time.LocalDate.parse(endDate)
        
        return queryFactory
            .selectDistinct(qCustomer)
            .from(qCustomer)
            .join(qOrder).on(qCustomer.id.eq(qOrder.customerId))
            .where(qCustomer.registeredDate.between(start, end))
            .fetch()
            .map { it.toDomain() }
    }

    private fun CustomerEntity.toDomain(): Customer {
        return Customer(
            id = CustomerId(id),
            name = name,
            email = email,
            address = address,
            registeredDate = registeredDate
        )
    }
}

