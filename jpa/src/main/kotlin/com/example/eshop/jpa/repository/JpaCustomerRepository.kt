package com.example.eshop.jpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.jpa.entity.CustomerEntity
import jakarta.persistence.EntityManager

class JpaCustomerRepository(private val em: EntityManager) : CustomerRepository {

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
        return em.find(CustomerEntity::class.java, id.value)?.toDomain()
    }

    override fun findAll(): List<Customer> {
        return em.createQuery("SELECT c FROM CustomerEntity c", CustomerEntity::class.java)
            .resultList
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
        val query = em.createQuery("""
            SELECT c FROM CustomerEntity c
            JOIN OrderEntity o ON c.id = o.customerId
            GROUP BY c.id, c.name, c.email, c.address, c.registeredDate
            HAVING SUM(o.totalAmount) >= :minAmount
        """, CustomerEntity::class.java)
        
        query.setParameter("minAmount", minAmount.amount)
        return query.resultList.map { it.toDomain() }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        val query = em.createQuery("""
            SELECT DISTINCT c FROM CustomerEntity c
            JOIN OrderEntity o ON c.id = o.customerId
            WHERE c.registeredDate BETWEEN :startDate AND :endDate
        """, CustomerEntity::class.java)
        
        query.setParameter("startDate", java.time.LocalDate.parse(startDate))
        query.setParameter("endDate", java.time.LocalDate.parse(endDate))
        return query.resultList.map { it.toDomain() }
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

