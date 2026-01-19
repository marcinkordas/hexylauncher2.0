package com.hexy.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
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

    fun scrollToOrigin() {
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }
    
    // Pre-computed hex path for performance
    private val hexPath = Path()
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    
    // Animation state
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null
    
    private var onAppClickListener: ((AppInfo) -> Unit)? = null
    private var onAppLongClickListener: ((AppInfo, Float, Float) -> Unit)? = null
    
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    init {
        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
        loadSettings()
        // Pre-generate hex positions (enough for ~200 apps)
        hexPositions = calculator.generateSpiralCoordinates(10)
        precomputeHexPath()
    }
    
    private fun precomputeHexPath() {
        hexPath.reset()
        // Create a unit hexagon path centered at origin
        // Using pointy-top orientation: first vertex at top
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
            val x = hexRadius * cos(angle)
            val y = hexRadius * sin(angle)
            if (i == 0) hexPath.moveTo(x, y)
            else hexPath.lineTo(x, y)
        }
        hexPath.close()
    }
    
    private fun loadSettings() {
        hexRadius = com.hexy.launcher.util.SettingsManager.getHexRadius(context)
        iconSizeMultiplier = com.hexy.launcher.util.SettingsManager.getIconSizeMultiplier(context)
        iconPadding = com.hexy.launcher.util.SettingsManager.getIconPadding(context)
        calculator = HexGridCalculator(hexRadius)
        precomputeHexPath()
    }
    
    fun refreshSettings() {
        loadSettings()
        hexPositions = calculator.generateSpiralCoordinates(10)
        invalidate()
    }
    
    fun setApps(appList: List<AppInfo>) {
        apps = appList
        // Start radial expansion animation
        startRadialAnimation()
    }
    
    private fun startRadialAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                animationProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
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
            
            // Apply radial animation scaling based on ring (only if valid)
            val ring = hex.ring
            val ringProgress = ((animationProgress * 10) - ring).coerceIn(0f, 1f)
            
            if (ringProgress > 0f) {
                canvas.save()
                canvas.translate(pos.x, pos.y)
                canvas.scale(ringProgress, ringProgress)
                
                // Draw hexagon background
                drawHexagonAtOrigin(canvas, app.dominantColor)
                
                // Draw app icon
                drawIconAtOrigin(canvas, app)
                
                canvas.restore()
            }
        }
    }
    
    private fun drawHexagonAtOrigin(canvas: Canvas, color: Int) {
        // Fill
        hexPaint.color = color
        hexPaint.alpha = 80
        hexPaint.style = Paint.Style.FILL
        canvas.drawPath(hexPath, hexPaint)
        
        // Stroke
        hexPaint.color = Color.WHITE
        hexPaint.alpha = 100
        hexPaint.style = Paint.Style.STROKE
        hexPaint.strokeWidth = 2f
        canvas.drawPath(hexPath, hexPaint)
    }
    
    private fun drawIconAtOrigin(canvas: Canvas, app: AppInfo) {
        // Calculate icon size based on hex radius and user settings
        val baseIconSize = hexRadius * 1.3f
        val iconSize = (baseIconSize * iconSizeMultiplier - iconPadding * 2).toInt()
        val halfSize = iconSize / 2
        
        app.icon.setBounds(-halfSize, -halfSize, halfSize, halfSize)
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
