package com.hexy.launcher.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Extracts dominant color from app icons with smart bucketing.
 * 
 * Buckets:
 * 0-7: Chromatic colors (Red, Orange, Yellow, Green, Turquoise, Blue, Navy, Violet)
 * 8: White/Gray
 * 9: Black
 * 10: Google apps (special bucket for com.google.* packages)
 */
object ColorExtractor {
    
    private val BUCKET_NAMES = arrayOf(
        "Red", "Orange", "Yellow", "Green", "Turquoise", 
        "Blue", "Navy", "Violet", "White", "Black", "Google"
    )
    
    val BUCKET_COLORS = intArrayOf(
        0xFFFF5252.toInt(), // Red
        0xFFFF9800.toInt(), // Orange
        0xFFFFEB3B.toInt(), // Yellow
        0xFF4CAF50.toInt(), // Green
        0xFF009688.toInt(), // Turquoise
        0xFF2196F3.toInt(), // Blue
        0xFF3F51B5.toInt(), // Navy
        0xFF9C27B0.toInt(), // Violet
        0xFFF5F5F5.toInt(), // White
        0xFF212121.toInt(), // Black
        0xFF4285F4.toInt()  // Google Blue
    )
    
    // Google package prefixes
    private val GOOGLE_PACKAGES = setOf(
        "com.google.",
        "com.android.vending", // Play Store
        "com.android.chrome"
    )
    
    /**
     * Check if package is a Google app.
     */
    fun isGooglePackage(packageName: String): Boolean {
        return GOOGLE_PACKAGES.any { packageName.startsWith(it) }
    }
    
    /**
     * Extract dominant color from drawable.
     * Returns Pair(dominantColor, bucketIndex 0-10)
     * 
     * @param packageName Optional - if Google package, returns bucket 10
     */
    fun extractColor(drawable: Drawable, packageName: String? = null): Pair<Int, Int> {
        // Check for Google apps first
        if (packageName != null && isGooglePackage(packageName)) {
            val bitmap: Bitmap = drawable.toBitmap(48, 48)
            val palette = Palette.from(bitmap).maximumColorCount(8).generate()
            val dominantColor = palette.getDominantColor(Color.GRAY)
            return Pair(dominantColor, 10) // Google bucket
        }
        
        val bitmap: Bitmap = drawable.toBitmap(48, 48)
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        
        val swatches = palette.swatches.sortedByDescending { it.population }
        
        var bestColor = palette.getDominantColor(Color.GRAY)
        var bestSaturation = 0f
        
        for (swatch in swatches) {
            val hsv = FloatArray(3)
            Color.colorToHSV(swatch.rgb, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            
            val isAchromatic = saturation < 0.2f || value < 0.15f || (saturation < 0.15f && value > 0.85f)
            
            if (!isAchromatic && saturation > bestSaturation) {
                bestSaturation = saturation
                bestColor = swatch.rgb
            }
        }
        
        if (bestSaturation < 0.1f) {
            bestColor = palette.getDominantColor(Color.GRAY)
        }
        
        val bucket = colorToBucket(bestColor)
        return Pair(bestColor, bucket)
    }
    
    private fun colorToBucket(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        
        if (value < 0.2f) return 9 // Black
        if (saturation < 0.15f && value > 0.8f) return 8 // White
        if (saturation < 0.2f) return if (value > 0.5f) 8 else 9
        
        return when {
            hue >= 345 || hue < 15 -> 0  // Red
            hue >= 15 && hue < 45 -> 1   // Orange
            hue >= 45 && hue < 70 -> 2   // Yellow
            hue >= 70 && hue < 150 -> 3  // Green
            hue >= 150 && hue < 185 -> 4 // Turquoise
            hue >= 185 && hue < 230 -> 5 // Blue
            hue >= 230 && hue < 260 -> 6 // Navy
            else -> 7                     // Violet/Pink
        }
    }
    
    fun getBucketName(bucket: Int): String {
        return if (bucket in 0..10) BUCKET_NAMES[bucket] else "Unknown"
    }
}
