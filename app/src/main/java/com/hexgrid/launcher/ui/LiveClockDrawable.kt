package com.hexgrid.launcher.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.util.Calendar

/**
 * A drawable that wraps an original app icon and overlays live clock hands.
 * Updates every minute to show current time.
 */
class LiveClockDrawable(
    private val originalIcon: Drawable,
    private val iconSize: Int = 96
) : Drawable(), Runnable {

    private val handler = Handler(Looper.getMainLooper())
    private var isScheduled = false
    
    // Clock hand paints
    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
    }
    
    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
    }
    
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(1f, 0f, 0.5f, Color.BLACK)
    }
    
    init {
        // Set intrinsic dimensions
        originalIcon.setBounds(0, 0, iconSize, iconSize)
    }
    
    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        
        // Draw original icon as background
        originalIcon.bounds = bounds
        originalIcon.draw(canvas)
        
        // Get current time
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        
        // Calculate hand angles (12 o'clock is up = -90 degrees)
        val hourAngle = Math.toRadians(((hours % 12) * 30.0 + minutes * 0.5) - 90)
        val minuteAngle = Math.toRadians((minutes * 6.0) - 90)
        
        // Hour hand (shorter, thicker)
        val hourHandLength = radius * 0.35f
        val hourEndX = centerX + (hourHandLength * Math.cos(hourAngle)).toFloat()
        val hourEndY = centerY + (hourHandLength * Math.sin(hourAngle)).toFloat()
        canvas.drawLine(centerX, centerY, hourEndX, hourEndY, hourHandPaint)
        
        // Minute hand (longer, thinner)
        val minuteHandLength = radius * 0.5f
        val minuteEndX = centerX + (minuteHandLength * Math.cos(minuteAngle)).toFloat()
        val minuteEndY = centerY + (minuteHandLength * Math.sin(minuteAngle)).toFloat()
        canvas.drawLine(centerX, centerY, minuteEndX, minuteEndY, minuteHandPaint)
        
        // Center dot
        canvas.drawCircle(centerX, centerY, 3f, centerDotPaint)
    }
    
    override fun setAlpha(alpha: Int) {
        originalIcon.alpha = alpha
        hourHandPaint.alpha = alpha
        minuteHandPaint.alpha = alpha
        centerDotPaint.alpha = alpha
    }
    
    override fun setColorFilter(colorFilter: ColorFilter?) {
        originalIcon.colorFilter = colorFilter
    }
    
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    
    override fun getIntrinsicWidth(): Int = iconSize
    
    override fun getIntrinsicHeight(): Int = iconSize
    
    /**
     * Start the minute update timer
     */
    fun startUpdates() {
        if (!isScheduled) {
            isScheduled = true
            scheduleNextUpdate()
        }
    }
    
    /**
     * Stop the minute update timer
     */
    fun stopUpdates() {
        isScheduled = false
        handler.removeCallbacks(this)
    }
    
    private fun scheduleNextUpdate() {
        if (!isScheduled) return
        
        // Calculate delay until next minute
        val calendar = Calendar.getInstance()
        val seconds = calendar.get(Calendar.SECOND)
        val delayMs = (60 - seconds) * 1000L
        
        handler.postDelayed(this, delayMs)
    }
    
    override fun run() {
        // Trigger redraw
        invalidateSelf()
        // Schedule next update
        scheduleNextUpdate()
    }
    
    companion object {
        /**
         * Check if a package name is likely a clock app
         */
        fun isClockApp(packageName: String): Boolean {
            val lowerName = packageName.lowercase()
            return lowerName.contains("clock") ||
                   lowerName.contains("deskclock") ||
                   lowerName.contains("alarm") ||
                   lowerName == "com.google.android.deskclock" ||
                   lowerName == "com.samsung.android.app.aodservice" ||
                   lowerName == "com.sec.android.app.clockpackage"
        }
    }
}
