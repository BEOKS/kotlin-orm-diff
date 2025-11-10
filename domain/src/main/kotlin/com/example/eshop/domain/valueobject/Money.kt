package com.example.eshop.domain.valueobject

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(val amount: BigDecimal) {
    
    init {
        require(amount.scale() <= 2) { "Money amount cannot have more than 2 decimal places" }
    }

    constructor(amount: Double) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))
    constructor(amount: Long) : this(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))

    operator fun plus(other: Money): Money = Money(amount.add(other.amount))
    operator fun minus(other: Money): Money = Money(amount.subtract(other.amount))
    operator fun times(multiplier: Int): Money = Money(amount.multiply(BigDecimal.valueOf(multiplier.toLong())))
    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    fun isZero(): Boolean = amount == BigDecimal.ZERO

    override fun toString(): String = amount.toString()

    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
    }
}

