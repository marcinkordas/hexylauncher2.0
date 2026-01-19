package com.hexy.launcher.domain

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Converts hex coordinates to screen pixels and vice versa.
 * Uses "pointy-top" orientation.
 */
class HexGridCalculator(
    private val hexRadius: Float  // Distance from center to vertex
) {
    // Hex dimensions
    private val hexWidth = hexRadius * 2f
    private val hexHeight = sqrt(3f) * hexRadius
    
    /**
     * Convert axial (q, r) to screen pixel (x, y).
     * Center hex (0,0) is at screen center.
     */
    fun hexToPixel(hex: HexCoordinate, centerX: Float, centerY: Float): PointF {
        val x = hexRadius * (3f / 2f * hex.q)
        val y = hexRadius * (sqrt(3f) / 2f * hex.q + sqrt(3f) * hex.r)
        return PointF(centerX + x, centerY + y)
    }
    
    /**
     * Convert screen pixel to nearest hex coordinate.
     */
    fun pixelToHex(px: Float, py: Float, centerX: Float, centerY: Float): HexCoordinate {
        val x = px - centerX
        val y = py - centerY
        
        val q = (2f / 3f * x) / hexRadius
        val r = (-1f / 3f * x + sqrt(3f) / 3f * y) / hexRadius
        
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
        
        val directions = listOf(
            HexCoordinate(1, 0), HexCoordinate(0, 1), HexCoordinate(-1, 1),
            HexCoordinate(-1, 0), HexCoordinate(0, -1), HexCoordinate(1, -1)
        )
        
        for (ring in 1..maxRings) {
            var hex = HexCoordinate(-ring, ring) // Start at top-left of ring
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
