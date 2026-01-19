package com.hexy.launcher.domain

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Converts hex coordinates to screen pixels and vice versa.
 * Uses "pointy-top" orientation (vertex at top).
 * 
 * In a honeycomb grid:
 * - Adjacent hexes in same column are staggered horizontally
 * - This creates the characteristic interlocking pattern
 */
class HexGridCalculator(
    private val hexRadius: Float  // Distance from center to vertex
) {
    // For pointy-top hexagons:
    // - Width (flat edge to flat edge) = sqrt(3) * hexRadius
    // - Height (vertex to vertex) = 2 * hexRadius
    private val hexWidth = sqrt(3f) * hexRadius
    private val hexHeight = 2f * hexRadius
    
    /**
     * Convert axial (q, r) to screen pixel (x, y).
     * Uses POINTY-TOP orientation formula for proper honeycomb tessellation.
     * 
     * Key insight: In pointy-top, alternating rows are offset by hexWidth/2
     */
    fun hexToPixel(hex: HexCoordinate, centerX: Float, centerY: Float): PointF {
        // Pointy-top axial to pixel:
        // x = size * sqrt(3) * (q + r/2)
        // y = size * 3/2 * r
        val x = hexRadius * sqrt(3f) * (hex.q + hex.r / 2f)
        val y = hexRadius * 3f / 2f * hex.r
        return PointF(centerX + x, centerY + y)
    }
    
    /**
     * Convert screen pixel to nearest hex coordinate.
     * Uses pointy-top formula.
     */
    fun pixelToHex(px: Float, py: Float, centerX: Float, centerY: Float): HexCoordinate {
        val x = px - centerX
        val y = py - centerY
        
        // Inverse of pointy-top formula
        val q = (sqrt(3f) / 3f * x - 1f / 3f * y) / hexRadius
        val r = (2f / 3f * y) / hexRadius
        
        return axialRound(q, r)
    }
    
    /**
     * Generate hex coordinates in spiral order from center.
     * Ring 0 = center (1 hex)
     * Ring 1 = 6 hexes around center
     * Ring N = 6*N hexes
     */
    fun generateSpiralCoordinates(maxRings: Int): List<HexCoordinate> {
        val result = mutableListOf<HexCoordinate>()
        result.add(HexCoordinate.ORIGIN)
        
        // Directions for traversing each edge of a ring (pointy-top)
        val directions = listOf(
            HexCoordinate(1, 0), HexCoordinate(0, 1), HexCoordinate(-1, 1),
            HexCoordinate(-1, 0), HexCoordinate(0, -1), HexCoordinate(1, -1)
        )
        
        for (ring in 1..maxRings) {
            // Start position for this ring (move to starting corner)
            var hex = HexCoordinate(0, -ring)
            
            for (dir in 0 until 6) {
                for (step in 0 until ring) {
                    result.add(hex)
                    hex = HexCoordinate(
                        hex.q + directions[dir].q,
                        hex.r + directions[dir].r
                    )
                }
            }
        }
        return result
    }
    
    private fun axialRound(q: Float, r: Float): HexCoordinate {
        val s = -q - r
        var rq = kotlin.math.round(q).toInt()
        var rr = kotlin.math.round(r).toInt()
        var rs = kotlin.math.round(s).toInt()
        
        val qDiff = kotlin.math.abs(rq - q)
        val rDiff = kotlin.math.abs(rr - r)
        val sDiff = kotlin.math.abs(rs - s)
        
        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs
        } else if (rDiff > sDiff) {
            rr = -rq - rs
        }
        
        return HexCoordinate(rq, rr)
    }
}
