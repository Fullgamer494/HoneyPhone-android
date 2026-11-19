package dev.honeyphone.app.core.network

import java.io.IOException

internal data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)

internal fun <T> fetchAllCursorPages(fetchPage: (String?) -> CursorPage<T>): List<T> {
    val items = mutableListOf<T>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null

    do {
        val page = fetchPage(cursor)
        items += page.items
        cursor = page.nextCursor?.takeIf(String::isNotBlank)
        if (cursor != null && !seenCursors.add(cursor)) {
            throw IOException("La API devolvió un cursor de paginación repetido.")
        }
    } while (cursor != null)

    return items
}
