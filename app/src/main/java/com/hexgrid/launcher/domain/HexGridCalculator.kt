package com.hexgrid.launcher.domain

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Converts hex coordinates to screen pixels and vice versa.
 * Supports both pointy-top and flat-top orientations.
 */
class HexGridCalculator(
    private val hexRadius: Float,
    private val orientation: Orientation = Orientation.POINTY_TOP
) {
    
    enum class Orientation {
        POINTY_TOP,  // Vertex at top (current)
        FLAT_TOP     // Edge at top (30° rotation)
    }
    
    private val hexWidth = sqrt(3f) * hexRadius
    private val hexHeight = 2f * hexRadius
    
    /**
     * Convert axial (q, r) to screen pixel (x, y).
     */
    fun hexToPixel(hex: HexCoordinate, centerX: Float, centerY: Float): PointF {
        val x: Float
        val y: Float
        
        when (orientation) {
            Orientation.POINTY_TOP -> {
                x = hexRadius * sqrt(3f) * (hex.q + hex.r / 2f)
                y = hexRadius * 3f / 2f * hex.r
            }
            Orientation.FLAT_TOP -> {
                x = hexRadius * 3f / 2f * hex.q
                y = hexRadius * sqrt(3f) * (hex.r + hex.q / 2f)
            }
        }
        
        return PointF(centerX + x, centerY + y)
    }
    
    /**
     * Convert screen pixel to nearest hex coordinate.
     */
    fun pixelToHex(px: Float, py: Float, centerX: Float, centerY: Float): HexCoordinate {
        val x = px - centerX
        val y = py - centerY
        
        val q: Float
        val r: Float
        
        when (orientation) {
            Orientation.POINTY_TOP -> {
                q = (sqrt(3f) / 3f * x - 1f / 3f * y) / hexRadius
                r = (2f / 3f * y) / hexRadius
            }
            Orientation.FLAT_TOP -> {
                q = (2f / 3f * x) / hexRadius
                r = (-1f / 3f * x + sqrt(3f) / 3f * y) / hexRadius
            }
        }
        
        return axialRound(q, r)
    }
    
    /**
     * Generate hex coordinates in WINDMILL spiral order.
     * @param numBuckets Number of color buckets (6 or 10)
     */
    fun generateWindmillSpiral(maxRings: Int, numBuckets: Int = 6): List<Pair<HexCoordinate, Int>> {
        val result = mutableListOf<Pair<HexCoordinate, Int>>()
        
        // Ring 0: center, bucket -1 (special case)
        result.add(Pair(HexCoordinate.ORIGIN, -1))
        
        // Directions for counterclockwise traversal from RIGHT
        val directions = listOf(
            HexCoordinate(-1, 1),  // down-left
            HexCoordinate(-1, 0),  // left
            HexCoordinate(0, -1),  // up-left
            HexCoordinate(1, -1),  // up-right
            HexCoordinate(1, 0),   // right
            HexCoordinate(0, 1)    // down-right
        )
        
        for (ring in 1..maxRings) {
            var hex = HexCoordinate(ring, 0)
            var positionInRing = 0
            
            for (dir in 0 until 6) {
                for (step in 0 until ring) {
                    // Calculate bucket based on position in ring
                    // Each bucket gets equal share: (ring * numBuckets) / numBuckets = ring hexes per bucket
                    val bucket = (positionInRing * numBuckets) / (ring * 6)
                    
                    result.add(Pair(hex, bucket % numBuckets))
                    
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
     * Legacy method for compatibility.
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
