package com.hexy.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.OverScroller
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.domain.AppSorter
import com.hexy.launcher.domain.HexCoordinate
import com.hexy.launcher.domain.HexGridCalculator
import kotlin.math.cos
import kotlin.math.sin

class HexagonalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    
    private var hexRadius = 96f
    private var calculator = HexGridCalculator(hexRadius)
    private var apps: List<AppInfo> = emptyList()
    private var hexPositions: List<HexCoordinate> = emptyList()
    
    private var iconSizeMultiplier = 1.0f
    private var iconPadding = 12f
    private var showOutline = true
    private var showLabels = true
    
    private var offsetX = 0f
    private var offsetY = 0f
    
    private var minOffsetX = 0f
    private var maxOffsetX = 0f
    private var minOffsetY = 0f
    private var maxOffsetY = 0f

    fun scrollToOrigin() {
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }
    
    private val hexPath = Path()
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 70
        style = Paint.Style.FILL
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        alpha = 0
        style = Paint.Style.FILL
    }
    
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null
    
    private val scroller = OverScroller(context).apply {
        setFriction(0.1f)
    }
    
    private var tappedIndex = -1
    private var tapBrightness = 0f
    private var tapAnimator: ValueAnimator? = null
    
    private var onAppClickListener: ((AppInfo) -> Unit)? = null
    private var onAppLongClickListener: ((AppInfo, Float, Float) -> Unit)? = null
    
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        loadSettings()
        hexPositions = calculator.generateSpiralCoordinates(15)
        precomputeHexPath()
    }
    
    private fun precomputeHexPath() {
        hexPath.reset()
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
        showOutline = com.hexy.launcher.util.SettingsManager.getShowOutline(context)
        showLabels = com.hexy.launcher.util.SettingsManager.getShowLabels(context)
        calculator = HexGridCalculator(hexRadius)
        textPaint.textSize = hexRadius * 0.16f // Even smaller font
        precomputeHexPath()
    }
    
    fun refreshSettings() {
        loadSettings()
        hexPositions = calculator.generateSpiralCoordinates(15)
        updateScrollBounds()
        invalidate()
    }
    
    fun setApps(appList: List<AppInfo>) {
        apps = appList
        updateScrollBounds()
        startRadialAnimation()
    }
    
    private fun updateScrollBounds() {
        if (apps.isEmpty() || hexPositions.isEmpty()) return
        
        val lastIndex = minOf(apps.size - 1, hexPositions.size - 1)
        var minX = 0f
        var maxX = 0f
        var minY = 0f
        var maxY = 0f
        
        for (i in 0..lastIndex) {
            val hex = hexPositions[i]
            val pos = calculator.hexToPixel(hex, 0f, 0f)
            minX = minOf(minX, pos.x)
            maxX = maxOf(maxX, pos.x)
            minY = minOf(minY, pos.y)
            maxY = maxOf(maxY, pos.y)
        }
        
        minOffsetX = -(maxX + hexRadius * 2)
        maxOffsetX = -minX + hexRadius * 2
        minOffsetY = -(maxY + hexRadius * 2)
        maxOffsetY = -minY + hexRadius * 2
    }
    
    private fun startRadialAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = OvershootInterpolator(1.0f)
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
    
    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat().coerceIn(minOffsetX, maxOffsetX)
            offsetY = scroller.currY.toFloat().coerceIn(minOffsetY, maxOffsetY)
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY
        
        apps.forEachIndexed { index, app ->
            if (index >= hexPositions.size) return@forEachIndexed
            
            val hex = hexPositions[index]
            val pos = calculator.hexToPixel(hex, centerX, centerY)
            
            // Cull off-screen
            if (pos.x < -hexRadius * 2 || pos.x > width + hexRadius * 2 ||
                pos.y < -hexRadius * 2 || pos.y > height + hexRadius * 2) {
                return@forEachIndexed
            }
            
            val ring = hex.ring
            val ringProgress = ((animationProgress * 12) - ring).coerceIn(0f, 1f)
            val isPlaceholder = AppSorter.isPlaceholder(app)
            
            if (ringProgress > 0f) {
                canvas.save()
                canvas.translate(pos.x, pos.y)
                canvas.scale(ringProgress, ringProgress)
                
                if (isPlaceholder) {
                    // Draw empty placeholder hex
                    canvas.drawPath(hexPath, emptyPaint)
                } else {
                    val brightness = if (index == tappedIndex) tapBrightness else 0f
                    
                    // Ring 0+1 (first 7): white only, no color background
                    if (index < 7) {
                        drawInnerHexagon(canvas, brightness)
                    } else {
                        drawHexagonAtOrigin(canvas, app.dominantColor, brightness)
                    }
                    
                    // Icon offset for label
                    val iconOffset = if (showLabels) -hexRadius * 0.04f else 0f
                    drawIconAtOrigin(canvas, app, iconOffset)
                }
                
                canvas.restore()
                
                // Label
                if (showLabels && ringProgress >= 1f && !isPlaceholder) {
                    drawLabelAt(canvas, pos.x, pos.y + hexRadius * 0.72f, app.label)
                }
            }
        }
    }
    
    private fun drawInnerHexagon(canvas: Canvas, brightness: Float) {
        // Semi-transparent white for inner ring
        whitePaint.alpha = (70 + brightness * 50).toInt()
        canvas.drawPath(hexPath, whitePaint)
        
        if (showOutline) {
            hexPaint.color = Color.WHITE
            hexPaint.alpha = (80 + brightness * 40).toInt()
            hexPaint.style = Paint.Style.STROKE
            hexPaint.strokeWidth = 1.5f
            canvas.drawPath(hexPath, hexPaint)
        }
    }
    
    private fun drawHexagonAtOrigin(canvas: Canvas, color: Int, brightness: Float) {
        hexPaint.color = color
        hexPaint.alpha = (50 + brightness * 80).toInt()
        hexPaint.style = Paint.Style.FILL
        canvas.drawPath(hexPath, hexPaint)
        
        if (showOutline) {
            hexPaint.color = Color.WHITE
            hexPaint.alpha = (60 + brightness * 60).toInt()
            hexPaint.style = Paint.Style.STROKE
            hexPaint.strokeWidth = 1.5f
            canvas.drawPath(hexPath, hexPaint)
        }
    }
    
    private fun drawIconAtOrigin(canvas: Canvas, app: AppInfo, yOffset: Float) {
        val baseIconSize = hexRadius * 1.1f
        val iconSize = (baseIconSize * iconSizeMultiplier - iconPadding * 2).toInt()
        val halfSize = iconSize / 2
        
        app.icon.setBounds(-halfSize, -halfSize + yOffset.toInt(), halfSize, halfSize + yOffset.toInt())
        app.icon.draw(canvas)
    }
    
    private fun drawLabelAt(canvas: Canvas, x: Float, y: Float, label: String) {
        val maxChars = 9
        val displayLabel = if (label.length > maxChars) label.take(maxChars - 1) + "…" else label
        canvas.drawText(displayLabel, x, y, textPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true)
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val index = findAppIndexAt(e.x, e.y)
            if (index >= 0 && index < apps.size) {
                val app = apps[index]
                if (!AppSorter.isPlaceholder(app)) {
                    startTapAnimation(index)
                    onAppClickListener?.invoke(app)
                }
            }
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            val index = findAppIndexAt(e.x, e.y)
            if (index >= 0 && index < apps.size) {
                val app = apps[index]
                if (!AppSorter.isPlaceholder(app)) {
                    onAppLongClickListener?.invoke(app, e.x, e.y)
                }
            }
        }
        
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            offsetX = (offsetX - dx).coerceIn(minOffsetX, maxOffsetX)
            offsetY = (offsetY - dy).coerceIn(minOffsetY, maxOffsetY)
            invalidate()
            return true
        }
        
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val subtleVelocityX = (velocityX * 0.3f).toInt()
            val subtleVelocityY = (velocityY * 0.3f).toInt()
            
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                subtleVelocityX, subtleVelocityY,
                minOffsetX.toInt(), maxOffsetX.toInt(),
                minOffsetY.toInt(), maxOffsetY.toInt()
            )
            invalidate()
            return true
        }
        
        override fun onDown(e: MotionEvent): Boolean = true
    }
    
    private fun startTapAnimation(index: Int) {
        tappedIndex = index
        tapAnimator?.cancel()
        tapAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                tapBrightness = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun findAppIndexAt(x: Float, y: Float): Int {
        val centerX = width / 2f + offsetX
        val centerY = height / 2f + offsetY
        val hex = calculator.pixelToHex(x, y, centerX, centerY)
        
        return hexPositions.indexOf(hex)
    }
}
