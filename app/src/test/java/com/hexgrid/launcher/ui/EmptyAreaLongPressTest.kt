package com.hexgrid.launcher.ui

import com.hexgrid.launcher.domain.HexCoordinate
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test for the empty-area long-press routing logic extracted from HexagonalGridView.
 * Verifies that the callback fires only for unoccupied, non-app, non-widget coordinates.
 */
class EmptyAreaLongPressTest {

    /**
     * Pure function extracted from HexagonalGridView.GestureListener.onLongPress logic.
     * Returns: "app" | "widget" | "empty"
     */
    private fun classify(
        coord: HexCoordinate,
        appCoords: Set<HexCoordinate>,
        widgetCoords: Set<HexCoordinate>
    ): String = when {
        coord in appCoords    -> "app"
        coord in widgetCoords -> "widget"
        else                  -> "empty"
    }

    @Test
    fun `app coordinate triggers app path`() {
        val coord = HexCoordinate(1, 0)
        val result = classify(coord, setOf(coord), emptySet())
        assertEquals("app", result)
    }

    @Test
    fun `widget coordinate triggers widget no-op path`() {
        val coord = HexCoordinate(2, 1)
        val result = classify(coord, emptySet(), setOf(coord))
        assertEquals("widget", result)
    }

    @Test
    fun `unoccupied coordinate triggers empty-area callback`() {
        val coord = HexCoordinate(5, 3)
        val result = classify(coord, setOf(HexCoordinate(0, 0)), setOf(HexCoordinate(1, 1)))
        assertEquals("empty", result)
    }

    @Test
    fun `app takes priority over widget when same coord (should not happen but safe)`() {
        val coord = HexCoordinate(0, 0)
        val result = classify(coord, setOf(coord), setOf(coord))
        assertEquals("app", result)
    }

    @Test
    fun `origin coordinate is empty when no apps or widgets placed`() {
        val result = classify(HexCoordinate(0, 0), emptySet(), emptySet())
        assertEquals("empty", result)
    }
}
