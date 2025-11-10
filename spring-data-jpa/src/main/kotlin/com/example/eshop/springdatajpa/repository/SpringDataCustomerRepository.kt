package com.example.eshop.springdatajpa.repository

import com.example.eshop.domain.entity.Customer
import com.example.eshop.domain.repository.CustomerRepository
import com.example.eshop.domain.valueobject.CustomerId
import com.example.eshop.domain.valueobject.Money
import com.example.eshop.springdatajpa.entity.CustomerEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
@Transactional(readOnly = true)
class SpringDataCustomerRepository(
    private val customerEntityRepository: CustomerEntityRepository
) : CustomerRepository {

    @Transactional
    override fun save(customer: Customer): Customer {
        // Check if entity exists
        val entity = if (customer.id.value != 0L && customerEntityRepository.existsById(customer.id.value)) {
            // Update existing entity
            customerEntityRepository.findById(customer.id.value).get().apply {
                name = customer.name
                email = customer.email
                address = customer.address
                registeredDate = customer.registeredDate
            }
        } else {
            // Create new entity
            CustomerEntity(
                id = customer.id.value,
                name = customer.name,
                email = customer.email,
                address = customer.address,
                registeredDate = customer.registeredDate
            )
        }
        customerEntityRepository.save(entity)
        return customer
    }

    override fun findById(id: CustomerId): Customer? {
        return customerEntityRepository.findById(id.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findAll(): List<Customer> {
        return customerEntityRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    override fun update(customer: Customer): Customer {
        val entity = customerEntityRepository.findById(customer.id.value)
            .orElseThrow { IllegalArgumentException("Customer not found: ${customer.id.value}") }

        entity.name = customer.name
        entity.email = customer.email
        entity.address = customer.address
        entity.registeredDate = customer.registeredDate
        customerEntityRepository.save(entity)
        return customer
    }

    @Transactional
    override fun delete(id: CustomerId): Boolean {
        return if (customerEntityRepository.existsById(id.value)) {
            customerEntityRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }

    override fun findCustomersWithHighValueOrders(minAmount: Money): List<Customer> {
        return customerEntityRepository.findCustomersWithHighValueOrders(minAmount.amount)
            .map { it.toDomain() }
    }

    override fun findCustomersWithOrdersInPeriod(startDate: String, endDate: String): List<Customer> {
        return customerEntityRepository.findCustomersWithOrdersInPeriod(
            LocalDate.parse(startDate),
            LocalDate.parse(endDate)
        ).map { it.toDomain() }
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

