package com.hexy.launcher.domain

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Converts hex coordinates to screen pixels and vice versa.
 * Uses "pointy-top" orientation (vertex at top).
 * 
 * Windmill spiral: starts from RIGHT and goes counterclockwise.
 */
class HexGridCalculator(
    private val hexRadius: Float
) {
    private val hexWidth = sqrt(3f) * hexRadius
    private val hexHeight = 2f * hexRadius
    
    /**
     * Convert axial (q, r) to screen pixel (x, y).
     * Pointy-top orientation.
     */
    fun hexToPixel(hex: HexCoordinate, centerX: Float, centerY: Float): PointF {
        val x = hexRadius * sqrt(3f) * (hex.q + hex.r / 2f)
        val y = hexRadius * 3f / 2f * hex.r
        return PointF(centerX + x, centerY + y)
    }
    
    /**
     * Convert screen pixel to nearest hex coordinate.
     */
    fun pixelToHex(px: Float, py: Float, centerX: Float, centerY: Float): HexCoordinate {
        val x = px - centerX
        val y = py - centerY
        
        val q = (sqrt(3f) / 3f * x - 1f / 3f * y) / hexRadius
        val r = (2f / 3f * y) / hexRadius
        
        return axialRound(q, r)
    }
    
    /**
     * Generate hex coordinates in WINDMILL spiral order.
     * - Ring 0: center (1 hex)
     * - Ring n: 6n hexes, starting from RIGHT and going counterclockwise
     * 
     * Returns list of (HexCoordinate, bucketIndex) pairs.
     * Each ring is divided into 6 equal sectors (1/6 per bucket).
     */
    fun generateWindmillSpiral(maxRings: Int): List<Pair<HexCoordinate, Int>> {
        val result = mutableListOf<Pair<HexCoordinate, Int>>()
        
        // Ring 0: center, bucket 0 (special case - center belongs to most-used app, not a color)
        result.add(Pair(HexCoordinate.ORIGIN, -1)) // -1 indicates center (no bucket)
        
        // Directions for counterclockwise traversal from RIGHT in pointy-top
        // Starting at (ring, 0) which is the rightmost hex
        // Directions: down-left, left, up-left, up-right, right, down-right
        val directions = listOf(
            HexCoordinate(-1, 1),  // down-left
            HexCoordinate(-1, 0),  // left
            HexCoordinate(0, -1),  // up-left
            HexCoordinate(1, -1),  // up-right
            HexCoordinate(1, 0),   // right
            HexCoordinate(0, 1)    // down-right
        )
        
        for (ring in 1..maxRings) {
            // Start at rightmost hex of this ring: (ring, 0)
            var hex = HexCoordinate(ring, 0)
            
            // Each ring has 6 * ring hexes
            // Each bucket gets (ring) hexes per ring
            var positionInRing = 0
            
            for (dir in 0 until 6) {
                for (step in 0 until ring) {
                    // Calculate bucket: position / (ring) gives bucket index
                    // This ensures each bucket gets exactly 'ring' hexes per ring
                    val bucket = positionInRing / ring
                    
                    result.add(Pair(hex, bucket % 6))
                    
                    hex = HexCoordinate(
                        hex.q + directions[dir].q,
                        hex.r + directions[dir].r
                    )
                    positionInRing++
                }
            }
        }
        
        return result
    }
    
    /**
     * Legacy method for compatibility - returns just coordinates without bucket info.
     */
    fun generateSpiralCoordinates(maxRings: Int): List<HexCoordinate> {
        return generateWindmillSpiral(maxRings).map { it.first }
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
