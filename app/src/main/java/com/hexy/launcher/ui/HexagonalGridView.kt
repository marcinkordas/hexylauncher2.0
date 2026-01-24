package com.hexy.launcher.ui

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.OverScroller
import androidx.core.animation.doOnEnd
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.domain.AppSorter
import com.hexy.launcher.domain.HexCoordinate
import com.hexy.launcher.domain.HexGridCalculator
import com.hexy.launcher.util.ColorExtractor
import com.hexy.launcher.util.SettingsManager
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
    private var showNotificationGlow = true
    private var outlineWidth = 1.5f
    private var unifiedBucketColors = false
    private var cornerRadius = 0f
    
    private var offsetX = 0f
    private var offsetY = 0f
    
    private var minOffsetX = 0f
    private var maxOffsetX = 0f
    private var minOffsetY = 0f
    private var maxOffsetY = 0f
    
    // Path for filling (may be smaller if outline off)
    private val fillPath = Path()
    // Path for stroking (always full size)
    private val strokePath = Path()
    
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private var animationProgress = 1f
    private var animator: ValueAnimator? = null
    private var centerAnimator: ValueAnimator? = null
    
    private val scroller = OverScroller(context).apply {
        setFriction(0.05f)
    }
    
    private var tappedIndex = -1
    private var tapBrightness = 0f
    private var tapAnimator: ValueAnimator? = null
    
    private var onAppClickListener: ((AppInfo) -> Unit)? = null
    private var onAppLongClickListener: ((AppInfo, Float, Float) -> Unit)? = null
    
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
        loadSettings()
        hexPositions = calculator.generateSpiralCoordinates(200) // Support more apps
        precomputeHexPaths()
    }
    
    private fun precomputeHexPaths() {
        strokePath.reset()
        fillPath.reset()
        
        val settingsOrientation = SettingsManager.getHexOrientation(context)
        val orientation = when (settingsOrientation) {
            SettingsManager.HexOrientation.POINTY_TOP -> HexGridCalculator.Orientation.POINTY_TOP
            SettingsManager.HexOrientation.FLAT_TOP -> HexGridCalculator.Orientation.FLAT_TOP
        }
        
        // If outline OFF, we shrink the fill path to create a gap (transparency)
        // Gap size is proportional to outlineWidth or a default if outlineWidth is 0
        val gap = if (!showOutline) (if(outlineWidth > 0) outlineWidth * 2 else 4f) else 0f
        val fillRadius = hexRadius - gap
        
        generateHexPath(strokePath, hexRadius, orientation)
        generateHexPath(fillPath, fillRadius, orientation)
    }
    
    private fun generateHexPath(path: Path, radius: Float, orientation: HexGridCalculator.Orientation) {
        path.reset()
        when (orientation) {
            HexGridCalculator.Orientation.POINTY_TOP -> {
                for (i in 0 until 6) {
                    val angle = Math.toRadians((60.0 * i - 30.0)).toFloat()
                    val x = radius * cos(angle)
                    val y = radius * sin(angle)
                    if (i == 0) path.moveTo(x, y)
                    else path.lineTo(x, y)
                }
            }
            HexGridCalculator.Orientation.FLAT_TOP -> {
                for (i in 0 until 6) {
                    val angle = Math.toRadians((60.0 * i)).toFloat()
                    val x = radius * cos(angle)
                    val y = radius * sin(angle)
                    if (i == 0) path.moveTo(x, y)
                    else path.lineTo(x, y)
                }
            }
        }
        path.close()
    }
    
    private fun loadSettings() {
        hexRadius = SettingsManager.getHexRadius(context)
        iconSizeMultiplier = SettingsManager.getIconSizeMultiplier(context)
        iconPadding = SettingsManager.getIconPadding(context)
        showOutline = SettingsManager.getShowOutline(context)
        showLabels = SettingsManager.getShowLabels(context)
        showNotificationGlow = SettingsManager.getShowNotificationGlow(context)
        outlineWidth = SettingsManager.getOutlineWidth(context)
        unifiedBucketColors = SettingsManager.getUnifiedBucketColors(context)
        cornerRadius = SettingsManager.getCornerRadius(context)
        
        // Apply corner rounding effect to paints
        val cornerEffect = if (cornerRadius > 0) android.graphics.CornerPathEffect(cornerRadius) else null
        hexPaint.pathEffect = cornerEffect
        whitePaint.pathEffect = cornerEffect
        
        val orientation = SettingsManager.getHexOrientation(context)
        val orientationEnum = when (orientation) {
            SettingsManager.HexOrientation.POINTY_TOP -> HexGridCalculator.Orientation.POINTY_TOP
            SettingsManager.HexOrientation.FLAT_TOP -> HexGridCalculator.Orientation.FLAT_TOP
        }
        
        calculator = HexGridCalculator(hexRadius, orientationEnum)
        textPaint.textSize = hexRadius * 0.16f
        precomputeHexPaths()
    }
    
    fun refreshSettings() {
        loadSettings()
        // Regenerate spiral to match new calculator settings if needed
        hexPositions = calculator.generateSpiralCoordinates(maxOf(25, (apps.size / 6) + 5))
        updateScrollBounds()
        invalidate()
    }
    
    fun setApps(appList: List<AppInfo>) {
        if (apps != appList) {
            apps = appList
            hexPositions = calculator.generateSpiralCoordinates(maxOf(25, (apps.size / 6) + 5))
            updateScrollBounds()
            startRadialAnimation()
        }
    }
    
    fun scrollToOrigin() {
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }
    
    fun animateToOrigin() {
        if (offsetX == 0f && offsetY == 0f) return
        
        centerAnimator?.cancel()
        
        val startX = offsetX
        val startY = offsetY
        
        centerAnimator = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat("x", startX, 0f),
            PropertyValuesHolder.ofFloat("y", startY, 0f)
        ).apply {
            duration = 600
            interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener { anim ->
                offsetX = anim.getAnimatedValue("x") as Float
                offsetY = anim.getAnimatedValue("y") as Float
                invalidate()
            }
            start()
        }
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
            duration = 600
            interpolator = OvershootInterpolator(0.8f)
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
        
        // Draw notification glows
        if (showNotificationGlow) {
            drawNotificationGlows(canvas, centerX, centerY)
        }
        
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
            val ringProgress = ((animationProgress * 15) - ring * 0.8f).coerceIn(0f, 1f)
            val isPlaceholder = AppSorter.isPlaceholder(app)
            
            if (ringProgress > 0f) {
                canvas.save()
                canvas.translate(pos.x, pos.y)
                canvas.scale(ringProgress, ringProgress)
                
                if (isPlaceholder) {
                    canvas.drawPath(fillPath, emptyPaint)
                } else {
                    val brightness = if (index == tappedIndex) tapBrightness else 0f
                    
                    if (index < 7) {
                        drawInnerHexagon(canvas, brightness)
                    } else {
                        val colorToUse = if (unifiedBucketColors && app.colorBucket in ColorExtractor.BUCKET_COLORS.indices) {
                            ColorExtractor.BUCKET_COLORS[app.colorBucket]
                        } else {
                            app.dominantColor
                        }
                        drawHexagonAtOrigin(canvas, colorToUse, brightness)
                    }
                    
                    // When labels shown, offset icon up to center the icon+label unit
                    val iconOffset = if (showLabels) -hexRadius * 0.12f else 0f
                    drawIconAtOrigin(canvas, app, iconOffset)
                }
                
                canvas.restore()
                
                if (showLabels && ringProgress >= 1f && !isPlaceholder) {
                    // Position label closer to icon for visual balance
                    drawLabelAt(canvas, pos.x, pos.y + hexRadius * 0.55f, app.label)
                }
            }
        }
    }
    
    private fun drawNotificationGlows(canvas: Canvas, centerX: Float, centerY: Float) {
        val glowHexes = mutableListOf<Int>()
        apps.forEachIndexed { index, app ->
            if (app.notificationCount > 0 && !AppSorter.isPlaceholder(app)) {
                glowHexes.add(index)
            }
        }
        
        glowHexes.forEach { index ->
            if (index >= hexPositions.size) return@forEach
            val hex = hexPositions[index]
            val pos = calculator.hexToPixel(hex, centerX, centerY)
            
            canvas.save()
            canvas.translate(pos.x, pos.y)
            
            val glowRadius = hexRadius * 1.3f
            val gradient = RadialGradient(
                0f, 0f, glowRadius,
                intArrayOf(
                    Color.argb(80, 255, 200, 100),
                    Color.argb(40, 255, 150, 50),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.3f, 0.7f, 1.0f),
                Shader.TileMode.CLAMP
            )
            glowPaint.shader = gradient
            canvas.drawCircle(0f, 0f, glowRadius, glowPaint)
            
            canvas.restore()
        }
    }
    
    private fun drawInnerHexagon(canvas: Canvas, brightness: Float) {
        whitePaint.alpha = (70 + brightness * 50).toInt()
        
        // Always draw fill using fillPath (which handles gap if needed)
        canvas.drawPath(fillPath, whitePaint)
        
        if (showOutline) {
            hexPaint.color = Color.WHITE
            hexPaint.alpha = (80 + brightness * 40).toInt()
            hexPaint.style = Paint.Style.STROKE
            hexPaint.strokeWidth = outlineWidth
            canvas.drawPath(strokePath, hexPaint)
        }
    }
    
    private fun drawHexagonAtOrigin(canvas: Canvas, color: Int, brightness: Float) {
        hexPaint.color = color
        hexPaint.alpha = (50 + brightness * 80).toInt()
        hexPaint.style = Paint.Style.FILL
        
        // Draw fill using fillPath
        canvas.drawPath(fillPath, hexPaint)
        
        if (showOutline) {
            hexPaint.color = Color.WHITE
            hexPaint.alpha = (60 + brightness * 60).toInt()
            hexPaint.style = Paint.Style.STROKE
            hexPaint.strokeWidth = outlineWidth
            canvas.drawPath(strokePath, hexPaint)
        }
    }
    
    private fun drawIconAtOrigin(canvas: Canvas, app: AppInfo, yOffset: Float) {
        val baseIconSize = hexRadius * 1.1f
        val maxSize = (baseIconSize * iconSizeMultiplier - iconPadding * 2).toInt()
        
        // Preserve aspect ratio
        val intrinsicW = app.icon.intrinsicWidth
        val intrinsicH = app.icon.intrinsicHeight
        
        val scale = if (intrinsicW > 0 && intrinsicH > 0) {
            minOf(maxSize.toFloat() / intrinsicW, maxSize.toFloat() / intrinsicH)
        } else 1f
        
        val width = (intrinsicW * scale).toInt().coerceAtLeast(1)
        val height = (intrinsicH * scale).toInt().coerceAtLeast(1)
        val halfW = width / 2
        val halfH = height / 2
        
        app.icon.setBounds(-halfW, -halfH + yOffset.toInt(), halfW, halfH + yOffset.toInt())
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
            centerAnimator?.cancel()
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
                    // Start system drag-and-drop
                    // val item = android.content.ClipData.Item(app.packageName)
                    // val dragData = android.content.ClipData(
                    //     app.label,
                    //     arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN),
                    //     item
                    // )
                    
                    // val shadow = android.view.View.DragShadowBuilder(this@HexagonalGridView)
                    // @Suppress("DEPRECATION")
                    // startDrag(dragData, shadow, null, 0)
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    
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
            val responsiveVelocityX = (velocityX * 0.5f).toInt()
            val responsiveVelocityY = (velocityY * 0.5f).toInt()
            
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                responsiveVelocityX, responsiveVelocityY,
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
