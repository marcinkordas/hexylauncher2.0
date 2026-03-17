package com.hexgrid.launcher.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetStoreTest {

    private fun entry(id: Int, awId: Int = id * 10) = WidgetEntry(
        widgetId = id, appWidgetId = awId,
        centerHexQ = 1, centerHexR = -1,
        widthPx = 400, heightPx = 200
    )

    @Test
    fun `round-trip empty list`() {
        val json = WidgetStore.entriesToJson(emptyList())
        val result = WidgetStore.parseEntries(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `round-trip preserves all fields`() {
        val original = listOf(entry(1), entry(2))
        val json = WidgetStore.entriesToJson(original)
        val parsed = WidgetStore.parseEntries(json)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].widgetId)
        assertEquals(10, parsed[0].appWidgetId)
        assertEquals(1, parsed[0].centerHexQ)
        assertEquals(-1, parsed[0].centerHexR)
        assertEquals(400, parsed[0].widthPx)
        assertEquals(200, parsed[0].heightPx)
        assertEquals(2, parsed[1].widgetId)
    }

    @Test
    fun `parseEntries returns empty on malformed JSON`() {
        val result = WidgetStore.parseEntries("not json")
        assertTrue(result.isEmpty())
    }
}
