package com.hexgrid.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class HexGridCalculatorOccupiedTest {

    private val calc = HexGridCalculator(96f)

    @Test
    fun `generateSpiralCoordinates without occupied returns full spiral`() {
        val coords = calc.generateSpiralCoordinates(3)
        // ring 0=1, ring1=6, ring2=12, ring3=18 → total 37
        assertEquals(37, coords.size)
    }

    @Test
    fun `generateSpiralCoordinates excludes occupied cells`() {
        val occupied = setOf(HexCoordinate(1, 0), HexCoordinate(-1, 1))
        val filtered = calc.generateSpiralCoordinates(3, occupied)
        assertFalse(filtered.contains(HexCoordinate(1, 0)))
        assertFalse(filtered.contains(HexCoordinate(-1, 1)))
        assertEquals(35, filtered.size) // 37 - 2
    }

    @Test
    fun `generateSpiralCoordinates with empty occupied returns same as without`() {
        val a = calc.generateSpiralCoordinates(5)
        val b = calc.generateSpiralCoordinates(5, emptySet())
        assertEquals(a, b)
    }
}
