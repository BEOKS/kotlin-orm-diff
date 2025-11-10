package com.example.eshop.jooq.util

data class PageRequest(
    val page: Int = 0,
    val size: Int = 20,
    val sortField: String? = null,
    val sortDirection: SortDirection = SortDirection.ASC
) {
    init {
        require(page >= 0) { "Page number must be non-negative" }
        require(size > 0) { "Page size must be positive" }
        require(size <= 1000) { "Page size must not exceed 1000" }
    }

    val offset: Int
        get() = page * size
}

enum class SortDirection {
    ASC, DESC
}
