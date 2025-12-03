package utils

data class Page<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val pageSize: Int
) {
    val hasPrevious: Boolean get() = currentPage > 1
    val hasNext: Boolean get() = currentPage < totalPages
    val previousPage: Int get() = currentPage - 1
    val nextPage: Int get() = currentPage + 1

    companion object {
        /**
         * Paginate a list of items.
         * Usage: Page.paginate(allTasks, currentPage = 1, pageSize = 10)
         */
        fun <T> paginate(
            items: List<T>,
            currentPage: Int = 1,
            pageSize: Int = 10
        ): Page<T> {
            val totalItems = items.size
            val totalPages = if (totalItems == 0) 1 else (totalItems + pageSize - 1) / pageSize

            // Clamp to valid range [1..totalPages]
            val validPage = currentPage.coerceIn(1, totalPages)

            // Calculate slice bounds
            val startIndex = (validPage - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, totalItems)

            val pageItems = if (startIndex < totalItems) {
                items.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            return Page(
                items = pageItems,
                currentPage = validPage,
                totalPages = totalPages,
                totalItems = totalItems,
                pageSize = pageSize
            )
        }
    }
}
