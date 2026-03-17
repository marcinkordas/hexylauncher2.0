package com.hexgrid.launcher.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.widget.FrameLayout
import com.hexgrid.launcher.domain.HexCoordinate
import com.hexgrid.launcher.domain.HexGridCalculator

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
    private val containerHeight: () -> Int
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

        for (entry in entries) {
            val info = appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            if (info == null) {
                host.releaseId(entry.appWidgetId)
                invalid.add(entry)
                continue
            }
            val view = host.createHostView(context, entry.appWidgetId, info)
            attachView(view, entry)
            hostViews[entry.widgetId] = view
        }

        if (invalid.isNotEmpty()) {
            val cleaned = entries.filter { it !in invalid }
            store.saveAll(cleaned)
            cachedEntries = cleaned
        } else {
            cachedEntries = entries
        }
    }

    /**
     * Called after the system widget picker+bind flow completes.
     * Creates and attaches a new widget at the given hex center.
     */
    fun confirmPlacement(appWidgetId: Int, centerHex: HexCoordinate) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: run {
            host.releaseId(appWidgetId)
            return
        }
        val widthPx = info.minWidth.coerceAtLeast(100)
        val heightPx = info.minHeight.coerceAtLeast(80)

        val entry = WidgetEntry(
            widgetId = store.nextWidgetId(),
            appWidgetId = appWidgetId,
            centerHexQ = centerHex.q,
            centerHexR = centerHex.r,
            widthPx = widthPx,
            heightPx = heightPx
        )
        store.add(entry)
        cachedEntries = cachedEntries + entry

        val view = host.createHostView(context, appWidgetId, info)
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
        val cells = mutableSetOf<HexCoordinate>()
        for (entry in cachedEntries) {
            // Sample corners + center of the widget bounding box to find occupied hexes
            val centerPx = calc.hexToPixel(HexCoordinate(entry.centerHexQ, entry.centerHexR), w / 2f, h / 2f)
            val halfW = entry.widthPx / 2f
            val halfH = entry.heightPx / 2f
            val samplePoints = listOf(
                PointF(centerPx.x, centerPx.y),
                PointF(centerPx.x - halfW, centerPx.y - halfH),
                PointF(centerPx.x + halfW, centerPx.y - halfH),
                PointF(centerPx.x - halfW, centerPx.y + halfH),
                PointF(centerPx.x + halfW, centerPx.y + halfH),
                PointF(centerPx.x, centerPx.y - halfH),
                PointF(centerPx.x, centerPx.y + halfH),
                PointF(centerPx.x - halfW, centerPx.y),
                PointF(centerPx.x + halfW, centerPx.y)
            )
            for (p in samplePoints) {
                cells.add(calc.pixelToHex(p.x, p.y, w / 2f, h / 2f))
            }
        }
        return cells
    }

    fun loadedEntries(): List<WidgetEntry> = cachedEntries

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun attachView(view: AppWidgetHostView, entry: WidgetEntry) {
        view.tag = "widget_${entry.widgetId}"
        val lp = FrameLayout.LayoutParams(entry.widthPx, entry.heightPx)
        container.addView(view, lp)
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

                // Check if touch is near a corner (resize) or center (move)
                val touchInView = PointF(event.x, event.y)
                val cornerZone = minOf(currentEntry.widthPx, currentEntry.heightPx) * 0.25f
                editMode = if (touchInView.x < cornerZone || touchInView.x > currentEntry.widthPx - cornerZone ||
                               touchInView.y < cornerZone || touchInView.y > currentEntry.heightPx - cornerZone) {
                    EditMode.RESIZE
                } else {
                    EditMode.MOVE
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
                        val hexR = minOf(originalWidthPx, originalHeightPx) / 3f
                        val newW = snapToStep((originalWidthPx + dx).toInt(), hexR.toInt())
                            .coerceAtLeast(currentEntry.widthPx.coerceAtLeast(100))
                        val newH = snapToStep((originalHeightPx + dy).toInt(), hexR.toInt())
                            .coerceAtLeast(currentEntry.heightPx.coerceAtLeast(80))
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
        // view.x/y is top-left; widget center is view.x + width/2, view.y + height/2
        val centerX = view.x + entry.widthPx / 2f
        val centerY = view.y + entry.heightPx / 2f
        val newHex = calc.pixelToHex(centerX, centerY, w / 2f + currentScrollX, h / 2f + currentScrollY)
        val updated = entry.copy(centerHexQ = newHex.q, centerHexR = newHex.r)
        store.update(updated)
        cachedEntries = cachedEntries.map { if (it.widgetId == updated.widgetId) updated else it }
        syncScroll(currentScrollX, currentScrollY)
    }

    private fun saveResizedDimensions(view: AppWidgetHostView, entry: WidgetEntry) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        val updated = entry.copy(widthPx = lp.width, heightPx = lp.height)
        store.update(updated)
        cachedEntries = cachedEntries.map { if (it.widgetId == updated.widgetId) updated else it }
        notifyWidgetSize(view, lp.width, lp.height)
    }

    private fun notifyWidgetSize(view: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthPx.toFloat(), heightPx.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(Bundle(), widthPx, heightPx, widthPx, heightPx)
        }
    }

    private fun snapToStep(value: Int, step: Int): Int {
        if (step <= 0) return value
        return (value / step) * step
    }
}
