package dev.honeyphone.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CursorPagingTest {
    @Test
    fun fetchesEveryPageUntilTheServerReturnsNoCursor() {
        val requestedCursors = mutableListOf<String?>()

        val items = fetchAllCursorPages { cursor ->
            requestedCursors += cursor
            when (cursor) {
                null -> CursorPage(listOf(1, 2), "page+2/=")
                "page+2/=" -> CursorPage(listOf(3), "page-3")
                "page-3" -> CursorPage(listOf(4), null)
                else -> error("Unexpected cursor: $cursor")
            }
        }

        assertEquals(listOf(1, 2, 3, 4), items)
        assertEquals(listOf(null, "page+2/=", "page-3"), requestedCursors)
    }

    @Test
    fun rejectsRepeatedCursorInsteadOfLoopingForever() {
        assertThrows(IOException::class.java) {
            fetchAllCursorPages { CursorPage(listOf(1), "same") }
        }
    }

    @Test
    fun treatsBlankCursorAsEndOfHistory() {
        var requestCount = 0

        val items = fetchAllCursorPages {
            requestCount++
            CursorPage(listOf(requestCount), "  ")
        }

        assertEquals(listOf(1), items)
        assertEquals(1, requestCount)
    }
}
