package com.hexy.launcher.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

object ColorExtractor {
    
    // 6 reference colors for buckets (evenly spaced on hue wheel)
    private val BUCKET_HUES = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f) // R, Y, G, C, B, M
    
    /**
     * Extract dominant color from drawable.
     * Returns Pair(dominantColor, bucketIndex 0-5)
     */
    fun extractColor(drawable: Drawable): Pair<Int, Int> {
        val bitmap: Bitmap = drawable.toBitmap(48, 48)
        val palette = Palette.from(bitmap).generate()
        
        val dominantColor = palette.getDominantColor(Color.GRAY)
        val bucket = colorToBucket(dominantColor)
        
        return Pair(dominantColor, bucket)
    }
    
    /**
     * Map a color to one of 6 buckets based on hue.
     * Gray/dark colors go to bucket 0.
     */
    private fun colorToBucket(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        
        // If saturation is very low (grayscale), assign to bucket 0
        if (hsv[1] < 0.2f || hsv[2] < 0.2f) {
            return 0
        }
        
        val hue = hsv[0] // 0-360
        
        // Find closest bucket
        var minDist = Float.MAX_VALUE
        var bucket = 0
        for (i in BUCKET_HUES.indices) {
            val dist = hueDist(hue, BUCKET_HUES[i])
            if (dist < minDist) {
                minDist = dist
                bucket = i
            }
        }
        return bucket
    }
    
    private fun hueDist(h1: Float, h2: Float): Float {
        val diff = kotlin.math.abs(h1 - h2)
        return minOf(diff, 360f - diff)
    }
}
