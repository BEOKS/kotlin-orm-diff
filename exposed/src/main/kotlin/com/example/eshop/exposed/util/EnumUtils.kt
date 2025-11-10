package com.example.eshop.exposed.util

/**
 * Safely converts a string to an enum value.
 * Throws IllegalStateException with a descriptive message if the value is invalid.
 */
inline fun <reified T : Enum<T>> String.toEnumOrThrow(): T {
    return enumValues<T>().find { it.name == this }
        ?: throw IllegalStateException("Invalid ${T::class.simpleName}: $this")
}
