package com.hexy.launcher.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.domain.HexCoordinate
import com.hexy.launcher.domain.HexGridCalculator
import kotlin.math.cos
import kotlin.math.sin

class HexagonalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private var hexRadius = 80f
    private var calculator = HexGridCalculator(hexRadius)
    private var apps: List<AppInfo> = emptyList()
    private var hexPositions: List<HexCoordinate> = emptyList()
    
    private var iconSizeMultiplier = 1.0f
    private var iconPadding = 4f
    
    private var offsetX = 0f
    private var offsetY = 0f
    
    private val hexPath = Path()
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var onAppClickListener: ((AppInfo) -> Unit)? = null
    private var onAppLongClickListener: ((AppInfo, Float, Float) -> Unit)? = null
    
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    init {
        loadSettings()
        // Pre-generate hex positions (enough for ~200 apps)
        hexPositions = calculator.generateSpiralCoordinates(10)
    }
    
    private fun loadSettings() {
        hexRadius = com.hexy.launcher.util.SettingsManager.getHexRadius(context)
        iconSizeMultiplier = com.hexy.launcher.util.SettingsManager.getIconSizeMultiplier(context)
        iconPadding = com.hexy.launcher.util.SettingsManager.getIconPadding(context)
        calculator = HexGridCalculator(hexRadius)
    }
    
    fun refreshSettings() {
        loadSettings()
        hexPositions = calculator.generateSpiralCoordinates(10)
        invalidate()
    }
    
    fun setApps(appList: List<AppInfo>) {
        apps = appList
        invalidate()
    }
    
    fun setOnAppClick(listener: (AppInfo) -> Unit) {
        onAppClickListener = listener
    }
    
    fun setOnAppLongClick(listener: (AppInfo, Float, Float) -> Unit) {
        onAppLongClickListener = listener
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY
        
        apps.forEachIndexed { index, app ->
            if (index >= hexPositions.size) return@forEachIndexed
            
            val hex = hexPositions[index]
            val pos = calculator.hexToPixel(hex, centerX, centerY)
            
            // Draw hexagon background
            drawHexagon(canvas, pos.x, pos.y, app.dominantColor)
            
            // Draw app icon (system icon as-is)
            drawIcon(canvas, pos.x, pos.y, app)
        }
    }
    
    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        hexPath.reset()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
            val x = cx + hexRadius * cos(angle)
            val y = cy + hexRadius * sin(angle)
            if (i == 0) hexPath.moveTo(x, y)
            else hexPath.lineTo(x, y)
        }
        hexPath.close()
        
        hexPaint.color = color
        hexPaint.alpha = 80
        hexPaint.style = Paint.Style.FILL
        canvas.drawPath(hexPath, hexPaint)
        
        hexPaint.color = Color.WHITE
        hexPaint.alpha = 100
        hexPaint.style = Paint.Style.STROKE
        hexPaint.strokeWidth = 2f
        canvas.drawPath(hexPath, hexPaint)
    }
    
    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, app: AppInfo) {
        // Use system icon as-is, no hexagonal clipping
        // Calculate icon size based on hex radius and user settings
        val baseIconSize = hexRadius * 1.3f
        val iconSize = (baseIconSize * iconSizeMultiplier - iconPadding * 2).toInt()
        val left = (cx - iconSize / 2).toInt()
        val top = (cy - iconSize / 2).toInt()
        
        app.icon.setBounds(left, top, left + iconSize, top + iconSize)
        app.icon.draw(canvas)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val app = findAppAt(e.x, e.y)
            app?.let { onAppClickListener?.invoke(it) }
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            val app = findAppAt(e.x, e.y)
            app?.let { onAppLongClickListener?.invoke(it, e.x, e.y) }
        }
        
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            offsetX -= dx
            offsetY -= dy
            invalidate()
            return true
        }
        
        override fun onDown(e: MotionEvent): Boolean = true
    }
    
    private fun findAppAt(x: Float, y: Float): AppInfo? {
        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY
        val hex = calculator.pixelToHex(x, y, centerX, centerY)
        
        val index = hexPositions.indexOf(hex)
        return if (index in apps.indices) apps[index] else null
    }
}
