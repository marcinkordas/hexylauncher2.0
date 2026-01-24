package com.hexgrid.launcher.domain

/**
 * Axial coordinate system for hexagonal grids.
 * q = column, r = row (axial coordinates)
 *
 * Neighbors in pointy-top hexagon:
 *   (+1, 0), (-1, 0), (0, +1), (0, -1), (+1, -1), (-1, +1)
 */
data class HexCoordinate(val q: Int, val r: Int) {
    
    // Convert to cube coordinates for distance calculation
    val s: Int get() = -q - r
    
    // Ring number (distance from center)
    val ring: Int get() = maxOf(
        kotlin.math.abs(q),
        kotlin.math.abs(r),
        kotlin.math.abs(s)
    )
    
    fun neighbors(): List<HexCoordinate> = listOf(
        HexCoordinate(q + 1, r),
        HexCoordinate(q - 1, r),
        HexCoordinate(q, r + 1),
        HexCoordinate(q, r - 1),
        HexCoordinate(q + 1, r - 1),
        HexCoordinate(q - 1, r + 1)
    )
    
    companion object {
        val ORIGIN = HexCoordinate(0, 0)
    }
}
