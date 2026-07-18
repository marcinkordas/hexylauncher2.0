package com.hexgrid.launcher.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.widget.FrameLayout
import com.hexgrid.launcher.domain.HexCoordinate
import com.hexgrid.launcher.domain.HexGridCalculator
import kotlin.math.ceil

/**
 * Orchestrates widget lifecycle: attaches/detaches AppWidgetHostViews in the FrameLayout,
 * syncs their position with HexagonalGridView scroll, and handles move/resize gestures.
 */
class WidgetManager(
    private val context: Context,
    private val host: WidgetHost,
    private val store: WidgetStore,
    private val container: FrameLayout,  // hexGridContainer
    private val hexCalculator: () -> HexGridCalculator,
    private val containerWidth: () -> Int,
    private val containerHeight: () -> Int,
    // Fired after a widget move/resize is committed so the grid can recompute occupied cells
    // and re-sort apps around the new widget position.
    private val onLayoutChanged: () -> Unit = {}
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val hostViews = mutableMapOf<Int, AppWidgetHostView>()  // widgetId → view
    private var cachedEntries: List<WidgetEntry> = emptyList()
    private var currentScrollX = 0f
    private var currentScrollY = 0f

    /** Call from MainActivity.onStart() */
    fun startListening() = host.startListening()

    /** Call from MainActivity.onStop() */
    fun stopListening() = host.stopListening()

    /**
     * Restore all widgets from store on startup.
     * Removes entries for uninstalled widget providers.
     */
    fun restoreWidgets() {
        val entries = store.loadAll().toMutableList()
        val invalid = mutableListOf<WidgetEntry>()
        val repaired = mutableListOf<WidgetEntry>()

        for ((index, entry) in entries.withIndex()) {
            val info = appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            if (info == null) {
                host.releaseId(entry.appWidgetId)
                invalid.add(entry)
                continue
            }
            // Self-heal: if a prior session stored a size that violates the provider's
            // min/max (e.g. resized a fixed-size Samsung widget), snap back. Without this,
            // the provider returns null RemoteViews → "Couldn't add widget.".
            val healed = clampToProviderSize(entry, info)
            if (healed != entry) {
                entries[index] = healed
                repaired.add(healed)
            }
            val view = host.createHostView(context, healed.appWidgetId, info)
            notifyWidgetSize(view, healed.widthPx, healed.heightPx)
            attachView(view, healed)
            hostViews[healed.widgetId] = view
        }

        val cleaned = entries.filter { it !in invalid }
        if (invalid.isNotEmpty() || repaired.isNotEmpty()) {
            store.saveAll(cleaned)
        }
        cachedEntries = cleaned
    }

    private fun clampToProviderSize(entry: WidgetEntry, info: AppWidgetProviderInfo): WidgetEntry {
        val density = context.resources.displayMetrics.density
        // ceil() for min ensures round-trip px→dp never falls below the declared min.
        // For max, use ceil too (we want to allow exactly the max dp) — Android floors when
        // converting back, which is fine for max.
        val minWPx = ceil((info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth) * density).toInt()
        val minHPx = ceil((info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight) * density).toInt()
        val maxWPx = if (info.resizeMode == AppWidgetProviderInfo.RESIZE_NONE) ceil(info.minWidth * density).toInt()
                     else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeWidth > 0) ceil(info.maxResizeWidth * density).toInt()
                     else Int.MAX_VALUE
        val maxHPx = if (info.resizeMode == AppWidgetProviderInfo.RESIZE_NONE) ceil(info.minHeight * density).toInt()
                     else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > 0) ceil(info.maxResizeHeight * density).toInt()
                     else Int.MAX_VALUE
        // Ensure min ≤ max (some providers have inconsistent metadata)
        val effectiveMaxW = maxOf(minWPx, maxWPx)
        val effectiveMaxH = maxOf(minHPx, maxHPx)
        val newW = entry.widthPx.coerceIn(minWPx, effectiveMaxW)
        val newH = entry.heightPx.coerceIn(minHPx, effectiveMaxH)
        return if (newW != entry.widthPx || newH != entry.heightPx) entry.copy(widthPx = newW, heightPx = newH) else entry
    }

    /**
     * Called after the system widget picker+bind flow completes.
     * Creates and attaches a new widget at the given hex center.
     */
    fun confirmPlacement(appWidgetId: Int, centerHex: HexCoordinate) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: run {
            android.util.Log.w("HexyWidget", "confirmPlacement: getAppWidgetInfo($appWidgetId) null!")
            host.releaseId(appWidgetId)
            return
        }
        val density = context.resources.displayMetrics.density
        android.util.Log.d("HexyWidget",
            "confirmPlacement id=$appWidgetId provider=${info.provider.shortClassName} " +
            "minWidth=${info.minWidth}dp minHeight=${info.minHeight}dp " +
            "minResize=${info.minResizeWidth}x${info.minResizeHeight}dp " +
            "resizeMode=${info.resizeMode} density=$density"
        )
        val entry = clampToProviderSize(
            WidgetEntry(
                widgetId = store.nextWidgetId(),
                appWidgetId = appWidgetId,
                centerHexQ = centerHex.q,
                centerHexR = centerHex.r,
                // Use ceil() so the px size, when converted back to dp by notifyWidgetSize(),
                // is guaranteed ≥ the provider's declared minimum. Plain toInt() truncates
                // (263 dp × 2.625 = 690.375 → 690 px → /2.625 = 262.85 → toInt 262 dp,
                // 1 dp BELOW the minimum → provider rejects → "Couldn't add widget.").
                widthPx = ceil(info.minWidth * density).toInt(),
                heightPx = ceil(info.minHeight * density).toInt()
            ),
            info
        )
        store.add(entry)
        cachedEntries = cachedEntries + entry

        val view = host.createHostView(context, appWidgetId, info)
        android.util.Log.d("HexyWidget",
            "createHostView id=$appWidgetId returned. " +
            "Initial childCount=${view.childCount} (>0 = RemoteViews already applied)"
        )
        notifyWidgetSize(view, entry.widthPx, entry.heightPx)
        attachView(view, entry)
        hostViews[entry.widgetId] = view
    }

    /**
     * Remove a widget — detaches view, releases AppWidget ID, removes from store.
     */
    fun remove(widgetId: Int) {
        hostViews.remove(widgetId)?.let { container.removeView(it) }
        cachedEntries.find { it.widgetId == widgetId }?.let {
            host.releaseId(it.appWidgetId)
        }
        store.remove(widgetId)
        cachedEntries = cachedEntries.filter { it.widgetId != widgetId }
    }

    /** Called on every HexagonalGridView scroll event to sync widget positions. */
    fun syncScroll(offsetX: Float, offsetY: Float) {
        currentScrollX = offsetX
        currentScrollY = offsetY
        for (entry in cachedEntries) {
            val view = hostViews[entry.widgetId] ?: continue
            val pos = positionForEntry(entry, offsetX, offsetY)
            view.x = pos.x - entry.widthPx / 2f
            view.y = pos.y - entry.heightPx / 2f
        }
    }

    /** Returns the hex cells occupied by all current widgets (bounding rectangles). */
    fun occupiedCells(): Set<HexCoordinate> {
        val calc = hexCalculator()
        val w = containerWidth()
        val h = containerHeight()
        if (w == 0 || h == 0) return emptySet()
        val cells = mutableSetOf<HexCoordinate>()
        for (entry in cachedEntries) {
            val centerPx = calc.hexToPixel(HexCoordinate(entry.centerHexQ, entry.centerHexR), w / 2f, h / 2f)
            val halfW = entry.widthPx / 2f
            val halfH = entry.heightPx / 2f
            // Scan a grid of points across the widget bounding box at half-hex-radius
            // intervals so that every hex cell underneath is detected, even for large widgets.
            val step = calc.getHexRadius() * 0.7f  // ~70% of hex radius ensures no gaps
            var y = centerPx.y - halfH
            while (y <= centerPx.y + halfH) {
                var x = centerPx.x - halfW
                while (x <= centerPx.x + halfW) {
                    cells.add(calc.pixelToHex(x, y, w / 2f, h / 2f))
                    x += step
                }
                y += step
            }
        }
        return cells
    }

    fun loadedEntries(): List<WidgetEntry> = cachedEntries

    /** Snapshot of currently-attached widget host views. Used for alpha animation (avoids
     *  per-frame findViewWithTag lookups and missed-view edge cases). */
    fun loadedViews(): List<AppWidgetHostView> = hostViews.values.toList()

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun attachView(view: AppWidgetHostView, entry: WidgetEntry) {
        view.tag = "widget_${entry.widgetId}"
        // NOTE: previously we called setLayerType(LAYER_TYPE_HARDWARE, null) here for alpha
        // animations. Removing it — it appears to interfere with RemoteViews rendering on real
        // devices (widget falls back to "Couldn't add widget."). Alpha animations will fall back
        // to software composition; we re-enable HW layer only during alpha animations if needed.
        val lp = FrameLayout.LayoutParams(entry.widthPx, entry.heightPx)
        container.addView(view, lp)

        // Diagnostic: log view's render state after the system has had a chance to push RemoteViews.
        view.postDelayed({
            android.util.Log.d("HexyWidget",
                "post500ms id=${entry.appWidgetId} childCount=${view.childCount} " +
                "size=${view.width}x${view.height} pos=${view.x},${view.y} " +
                "vis=${view.visibility} alpha=${view.alpha}"
            )
        }, 500)

        // Position after layout pass; use post() because width/height may be 0 at this point
        container.post { syncScroll(currentScrollX, currentScrollY) }
        setupEditGestures(view, entry)
    }

    private fun positionForEntry(entry: WidgetEntry, offsetX: Float, offsetY: Float): PointF {
        val calc = hexCalculator()
        val w = containerWidth()
        val h = containerHeight()
        return calc.hexToPixel(
            HexCoordinate(entry.centerHexQ, entry.centerHexR),
            w / 2f + offsetX,
            h / 2f + offsetY
        )
    }

    // ── Edit gestures (move + resize) ─────────────────────────────────────────

    private var editingWidgetId: Int = -1
    private var editMode: EditMode = EditMode.NONE

    private enum class EditMode { NONE, MOVE, RESIZE }

    private var dragStartX = 0f
    private var dragStartY = 0f
    private var originalViewX = 0f
    private var originalViewY = 0f
    private var originalWidthPx = 0
    private var originalHeightPx = 0

    private fun setupEditGestures(view: AppWidgetHostView, entry: WidgetEntry) {
        view.setOnLongClickListener {
            if (editingWidgetId == -1) {
                editingWidgetId = entry.widgetId
                editMode = EditMode.MOVE
            }
            true
        }

        view.setOnTouchListener { v, event ->
            if (editingWidgetId != entry.widgetId) return@setOnTouchListener false
            handleEditTouch(v as AppWidgetHostView, entry, event)
            true
        }
    }

    private fun handleEditTouch(view: AppWidgetHostView, originalEntry: WidgetEntry, event: MotionEvent) {
        val currentEntry = cachedEntries.find { it.widgetId == originalEntry.widgetId } ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                originalViewX = view.x
                originalViewY = view.y
                originalWidthPx = currentEntry.widthPx
                originalHeightPx = currentEntry.heightPx

                // Fixed-size widgets (Samsung Clock alarm, many OEM-proprietary widgets) declare
                // resizeMode = RESIZE_NONE. Sending them a non-conforming size makes the provider
                // return null RemoteViews and the host shows "Couldn't add widget.". Force MOVE
                // for these — emulator's AOSP clock has RESIZE_BOTH so the same gesture doesn't
                // break there.
                val info = appWidgetManager.getAppWidgetInfo(currentEntry.appWidgetId)
                val isResizable = info != null && info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE

                editMode = if (!isResizable) {
                    EditMode.MOVE
                } else {
                    // Check if touch is near a corner (resize) or center (move)
                    val touchInView = PointF(event.x, event.y)
                    val cornerZone = minOf(currentEntry.widthPx, currentEntry.heightPx) * 0.25f
                    if (touchInView.x < cornerZone || touchInView.x > currentEntry.widthPx - cornerZone ||
                        touchInView.y < cornerZone || touchInView.y > currentEntry.heightPx - cornerZone) {
                        EditMode.RESIZE
                    } else {
                        EditMode.MOVE
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartX
                val dy = event.rawY - dragStartY
                when (editMode) {
                    EditMode.MOVE -> {
                        view.x = originalViewX + dx
                        view.y = originalViewY + dy
                    }
                    EditMode.RESIZE -> {
                        val info = appWidgetManager.getAppWidgetInfo(currentEntry.appWidgetId)
                        val density = context.resources.displayMetrics.density
                        // Provider-declared min/max (dp → px). Fall back to sensible defaults if absent.
                        val minWPx = ((info?.minResizeWidth?.takeIf { it > 0 } ?: info?.minWidth ?: 100) * density).toInt()
                        val minHPx = ((info?.minResizeHeight?.takeIf { it > 0 } ?: info?.minHeight ?: 80) * density).toInt()
                        val maxWPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (info?.maxResizeWidth ?: 0) > 0)
                            (info!!.maxResizeWidth * density).toInt() else Int.MAX_VALUE
                        val maxHPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (info?.maxResizeHeight ?: 0) > 0)
                            (info!!.maxResizeHeight * density).toInt() else Int.MAX_VALUE

                        val hexR = minOf(originalWidthPx, originalHeightPx) / 3f
                        val newW = snapToStep((originalWidthPx + dx).toInt(), hexR.toInt())
                            .coerceIn(minWPx, maxWPx)
                        val newH = snapToStep((originalHeightPx + dy).toInt(), hexR.toInt())
                            .coerceIn(minHPx, maxHPx)
                        val lp = view.layoutParams as FrameLayout.LayoutParams
                        lp.width = newW
                        lp.height = newH
                        view.layoutParams = lp
                    }
                    EditMode.NONE -> {}
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (editMode) {
                    EditMode.MOVE -> saveMovedPosition(view, currentEntry)
                    EditMode.RESIZE -> saveResizedDimensions(view, currentEntry)
                    EditMode.NONE -> {}
                }
                editingWidgetId = -1
                editMode = EditMode.NONE
            }
        }
    }

    private fun saveMovedPosition(view: AppWidgetHostView, entry: WidgetEntry) {
        val calc = hexCalculator()
        val w = containerWidth()
        val h = containerHeight()
        // Clamp the visual center to within the container so the widget cannot be dragged
        // off-grid into a position that can't be reached or that clips the host view.
        val halfW = entry.widthPx / 2f
        val halfH = entry.heightPx / 2f
        val rawCenterX = view.x + halfW
        val rawCenterY = view.y + halfH
        val centerX = rawCenterX.coerceIn(halfW, w - halfW)
        val centerY = rawCenterY.coerceIn(halfH, h - halfH)
        val newHex = calc.pixelToHex(centerX, centerY, w / 2f + currentScrollX, h / 2f + currentScrollY)
        val updated = entry.copy(centerHexQ = newHex.q, centerHexR = newHex.r)
        store.update(updated)
        cachedEntries = cachedEntries.map { if (it.widgetId == updated.widgetId) updated else it }
        syncScroll(currentScrollX, currentScrollY)
        onLayoutChanged()
    }

    private fun saveResizedDimensions(view: AppWidgetHostView, entry: WidgetEntry) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        val updated = entry.copy(widthPx = lp.width, heightPx = lp.height)
        store.update(updated)
        cachedEntries = cachedEntries.map { if (it.widgetId == updated.widgetId) updated else it }
        notifyWidgetSize(view, lp.width, lp.height)
        onLayoutChanged()
    }

    private fun notifyWidgetSize(view: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        // updateAppWidgetSize expects sizes in dp, NOT px. On API 31+ pass float dp (no truncation).
        // On older API, use ceil() so we never round DOWN below the provider's declared minResize
        // (the round-down was the cause of "Couldn't add widget." on Samsung).
        val density = context.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val widthDp = widthPx / density
            val heightDp = heightPx / density
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            val widthDp = ceil(widthPx / density).toInt()
            @Suppress("DEPRECATION")
            val heightDp = ceil(heightPx / density).toInt()
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
    }

    private fun snapToStep(value: Int, step: Int): Int {
        if (step <= 0) return value
        return (value / step) * step
    }
}
