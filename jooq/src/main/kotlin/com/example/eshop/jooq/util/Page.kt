package com.example.eshop.jooq.util

data class Page<T>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        fun <T> of(
            content: List<T>,
            pageRequest: PageRequest,
            totalElements: Long
        ): Page<T> {
            val totalPages = if (pageRequest.size == 0) 0
                else ((totalElements + pageRequest.size - 1) / pageRequest.size).toInt()

            return Page(
                content = content,
                pageNumber = pageRequest.page,
                pageSize = pageRequest.size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = pageRequest.page < totalPages - 1,
                hasPrevious = pageRequest.page > 0
            )
        }
    }
}
