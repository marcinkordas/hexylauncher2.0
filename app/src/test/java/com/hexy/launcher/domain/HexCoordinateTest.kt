package com.hexy.launcher.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for HexCoordinate math operations
 */
class HexCoordinateTest {

    @Test
    fun `origin has ring 0`() {
        val origin = HexCoordinate.ORIGIN
        assertEquals(0, origin.ring)
    }

    @Test
    fun `adjacent hex has ring 1`() {
        val adjacent = HexCoordinate(1, 0)
        assertEquals(1, adjacent.ring)
    }

    @Test
    fun `diagonal hex has correct ring`() {
        val diagonal = HexCoordinate(2, -1)
        assertEquals(2, diagonal.ring)
    }

    @Test
    fun `neighbors returns 6 neighbors`() {
        val hex = HexCoordinate(0, 0)
        val neighbors = hex.neighbors()
        assertEquals(6, neighbors.size)
    }

    @Test
    fun `neighbors are unique`() {
        val hex = HexCoordinate(1, 1)
        val neighbors = hex.neighbors()
        assertEquals(neighbors.distinct().size, neighbors.size)
    }

    @Test
    fun `s coordinate is calculated correctly`() {
        val hex = HexCoordinate(3, -1)
        assertEquals(-2, hex.s) // s = -q - r = -3 - (-1) = -2
    }

    @Test
    fun `ring 3 hex is at distance 3`() {
        val hex = HexCoordinate(3, 0)
        assertEquals(3, hex.ring)
    }

    @Test
    fun `negative coordinates work correctly`() {
        val hex = HexCoordinate(-2, -1)
        assertEquals(3, hex.ring) // max(|-2|, |-1|, |3|) = 3
    }
}
