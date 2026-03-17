# Cycle 2 — Widget Support Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Embed Android app widgets in the hexagonal grid — placed over the FrameLayout, scrolling in sync, free-form resizable with hex snap, managed via a new Settings screen.

**Architecture:** Widgets live as `AppWidgetHostView` children of the existing root `FrameLayout` in `activity_main.xml`, sibling to `HexagonalGridView`. `WidgetManager` (owned by `MainActivity`) orchestrates add/remove/resize/move. `WidgetStore` persists entries as a JSON string in SharedPreferences. `HexagonalGridView` exposes a scroll callback so widgets translate in sync, and accepts an occupied-cells set so app icons route around widget areas.

**Tech Stack:** Kotlin, Android AppWidget API (`AppWidgetHost`, `AppWidgetManager`, `AppWidgetHostView`), `ActivityResultLauncher`, `SharedPreferences` + `org.json`, ViewBinding, existing `HexGridCalculator` / `HexCoordinate`.

---

## File Structure

### New Files
| File | Purpose |
|------|---------|
| `widget/WidgetEntry.kt` | Data class: widgetId, appWidgetId, centerHexQ/R, widthPx/heightPx |
| `widget/WidgetHost.kt` | `AppWidgetHost` wrapper — host ID 1024, allocate/delete IDs, create views |
| `widget/WidgetStore.kt` | SharedPreferences JSON persistence for widget entries |
| `widget/WidgetManager.kt` | Orchestrator: add/remove/resize/move flows; holds AppWidgetHostView refs |
| `ui/WidgetManagementActivity.kt` | Settings screen: list widgets, "Add Widget" button |
| `res/layout/activity_widget_management.xml` | RecyclerView + Add button layout |
| `res/layout/item_widget.xml` | Row: icon + name + "Remove" button |
| `test/.../widget/WidgetStoreTest.kt` | JUnit tests for JSON serialization (pure Kotlin, no Context) |
| `test/.../domain/HexGridCalculatorOccupiedTest.kt` | Tests for occupiedCells filtering |

### Modified Files
| File | Change |
|------|--------|
| `AndroidManifest.xml` | Add `BIND_APPWIDGET` permission; register `WidgetManagementActivity` |
| `res/layout/activity_main.xml` | Add `android:id="@+id/hexGridContainer"` to root `FrameLayout` |
| `ui/HexagonalGridView.kt` | `onScrollChanged` callback; `setOccupiedCells()`; placement mode overlay |
| `domain/HexGridCalculator.kt` | `generateSpiralCoordinates(maxRings, occupiedCells = emptySet())` |
| `domain/AppSorter.kt` | `sortApps(apps, context, occupiedCells = emptySet())` — skip occupied spiral slots |
| `MainActivity.kt` | `onStart`/`onStop` widget host; `onNewIntent` placement mode; search fade |
| `util/SettingsManager.kt` | `KEY_WIDGETS`, `KEY_SHOW_WIDGETS_DURING_SEARCH`, getter/setter |
| `util/SettingsExporter.kt` | Add `"widgets"` to `STRING_KEYS` |
| `ui/SettingsActivity.kt` | "Manage Widgets" button + "Show widgets during search" toggle |
| `res/layout/activity_settings.xml` | Widget card with button + toggle |

---

## Chunk 1: Data Layer

### Task 1: `WidgetEntry` data class

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/widget/WidgetEntry.kt`

- [ ] **Step 1: Create `WidgetEntry.kt`**

```kotlin
package com.hexgrid.launcher.widget

data class WidgetEntry(
    val widgetId: Int,       // internal sequential ID
    val appWidgetId: Int,    // Android system widget ID
    val centerHexQ: Int,
    val centerHexR: Int,
    val widthPx: Int,
    val heightPx: Int
)
```

- [ ] **Step 2: Build to verify**

```bash
cd hexylauncher2.0 && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/widget/WidgetEntry.kt
git commit -m "feat(widgets): add WidgetEntry data class"
```

---

### Task 2: `WidgetStore` — JSON persistence

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/widget/WidgetStore.kt`
- Create: `app/src/test/java/com/hexgrid/launcher/widget/WidgetStoreTest.kt`

- [ ] **Step 1: Write the failing test**

File: `app/src/test/java/com/hexgrid/launcher/widget/WidgetStoreTest.kt`

```kotlin
package com.hexgrid.launcher.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetStoreTest {

    private fun entry(id: Int, awId: Int = id * 10) = WidgetEntry(
        widgetId = id, appWidgetId = awId,
        centerHexQ = 1, centerHexR = -1,
        widthPx = 400, heightPx = 200
    )

    @Test
    fun `round-trip empty list`() {
        val json = WidgetStore.entriesToJson(emptyList())
        val result = WidgetStore.parseEntries(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `round-trip preserves all fields`() {
        val original = listOf(entry(1), entry(2))
        val json = WidgetStore.entriesToJson(original)
        val parsed = WidgetStore.parseEntries(json)
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].widgetId)
        assertEquals(10, parsed[0].appWidgetId)
        assertEquals(1, parsed[0].centerHexQ)
        assertEquals(-1, parsed[0].centerHexR)
        assertEquals(400, parsed[0].widthPx)
        assertEquals(200, parsed[0].heightPx)
        assertEquals(2, parsed[1].widgetId)
    }

    @Test
    fun `parseEntries returns empty on malformed JSON`() {
        val result = WidgetStore.parseEntries("not json")
        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.hexgrid.launcher.widget.WidgetStoreTest" 2>&1 | tail -20
```
Expected: FAIL — `WidgetStore` not found

- [ ] **Step 3: Implement `WidgetStore.kt`**

```kotlin
package com.hexgrid.launcher.widget

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

class WidgetStore(private val context: Context) {

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    fun loadAll(): List<WidgetEntry> =
        parseEntries(prefs().getString(KEY_WIDGETS, "[]") ?: "[]")

    fun saveAll(entries: List<WidgetEntry>) =
        prefs().edit().putString(KEY_WIDGETS, entriesToJson(entries)).apply()

    fun add(entry: WidgetEntry) = saveAll(loadAll() + entry)

    fun remove(widgetId: Int) = saveAll(loadAll().filter { it.widgetId != widgetId })

    fun update(entry: WidgetEntry) =
        saveAll(loadAll().map { if (it.widgetId == entry.widgetId) entry else it })

    fun nextWidgetId(): Int = (loadAll().maxOfOrNull { it.widgetId } ?: 0) + 1

    companion object {
        const val KEY_WIDGETS = "widgets"

        fun entriesToJson(entries: List<WidgetEntry>): String {
            val arr = JSONArray()
            for (e in entries) {
                arr.put(JSONObject().apply {
                    put("widgetId", e.widgetId)
                    put("appWidgetId", e.appWidgetId)
                    put("centerHexQ", e.centerHexQ)
                    put("centerHexR", e.centerHexR)
                    put("widthPx", e.widthPx)
                    put("heightPx", e.heightPx)
                })
            }
            return arr.toString()
        }

        fun parseEntries(json: String): List<WidgetEntry> {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WidgetEntry(
                        widgetId = o.getInt("widgetId"),
                        appWidgetId = o.getInt("appWidgetId"),
                        centerHexQ = o.getInt("centerHexQ"),
                        centerHexR = o.getInt("centerHexR"),
                        widthPx = o.getInt("widthPx"),
                        heightPx = o.getInt("heightPx")
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:test --tests "com.hexgrid.launcher.widget.WidgetStoreTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/widget/WidgetStore.kt \
        app/src/test/java/com/hexgrid/launcher/widget/WidgetStoreTest.kt
git commit -m "feat(widgets): add WidgetStore with JSON persistence"
```

---

### Task 3: `WidgetHost` — `AppWidgetHost` wrapper

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/widget/WidgetHost.kt`

- [ ] **Step 1: Create `WidgetHost.kt`**

```kotlin
package com.hexgrid.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Wraps AppWidgetHost with hardcoded host ID 1024.
 * Hardcoded ID avoids orphaned widgets if SharedPreferences are cleared.
 */
class WidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    companion object {
        const val HOST_ID = 1024
    }

    fun allocateId(): Int = allocateAppWidgetId()

    fun createHostView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo
    ): AppWidgetHostView = createView(context, appWidgetId, info)

    fun releaseId(appWidgetId: Int) = deleteAppWidgetId(appWidgetId)
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/widget/WidgetHost.kt
git commit -m "feat(widgets): add WidgetHost wrapping AppWidgetHost (ID 1024)"
```

---

### Task 4: `SettingsManager` keys + `SettingsExporter`

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/util/SettingsManager.kt`
- Modify: `app/src/main/java/com/hexgrid/launcher/util/SettingsExporter.kt`

- [ ] **Step 1: Add widget keys to `SettingsManager.kt`**

After the `KEY_ICON_CACHE_DIRTY` line (line 27), add both keys:

```kotlin
    private const val KEY_SHOW_WIDGETS_DURING_SEARCH = "show_widgets_during_search"
    const val KEY_WIDGETS = "widgets"  // also used by WidgetStore and SettingsExporter
```

After the `setIconCacheDirty()` function (end of file, before closing brace), add:

```kotlin
    // Show Widgets During Search
    fun getShowWidgetsDuringSearch(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SHOW_WIDGETS_DURING_SEARCH, false)
    fun setShowWidgetsDuringSearch(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_WIDGETS_DURING_SEARCH, value).apply()
    }
```

- [ ] **Step 2: Add `KEY_WIDGETS` to `SettingsExporter.STRING_KEYS`**

In `SettingsExporter.kt`, change:

```kotlin
    private val STRING_KEYS = listOf(
        "sort_order", "hex_orientation", "search_position"
    )
```

to:

```kotlin
    private val STRING_KEYS = listOf(
        "sort_order", "hex_orientation", "search_position", SettingsManager.KEY_WIDGETS
    )
```

Add the import at the top of `SettingsExporter.kt` if not already present:

```kotlin
import com.hexgrid.launcher.util.SettingsManager
```

- [ ] **Step 3 (update `WidgetStore`):** Change `WidgetStore.KEY_WIDGETS` to delegate to `SettingsManager`:

In `WidgetStore.kt`, replace:
```kotlin
    companion object {
        const val KEY_WIDGETS = "widgets"
```
with:
```kotlin
    companion object {
        val KEY_WIDGETS get() = SettingsManager.KEY_WIDGETS
```
And add the import:
```kotlin
import com.hexgrid.launcher.util.SettingsManager
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/util/SettingsManager.kt \
        app/src/main/java/com/hexgrid/launcher/util/SettingsExporter.kt
git commit -m "feat(widgets): add KEY_WIDGETS and KEY_SHOW_WIDGETS_DURING_SEARCH to SettingsManager"
```

---

## Chunk 2: Domain + Grid Changes

### Task 5: `HexGridCalculator` — optional `occupiedCells`

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/domain/HexGridCalculator.kt`
- Create: `app/src/test/java/com/hexgrid/launcher/domain/HexGridCalculatorOccupiedTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hexgrid.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class HexGridCalculatorOccupiedTest {

    private val calc = HexGridCalculator(96f)

    @Test
    fun `generateSpiralCoordinates without occupied returns full spiral`() {
        val coords = calc.generateSpiralCoordinates(3)
        // ring 0=1, ring1=6, ring2=12, ring3=18 → total 37
        assertEquals(37, coords.size)
    }

    @Test
    fun `generateSpiralCoordinates excludes occupied cells`() {
        val occupied = setOf(HexCoordinate(1, 0), HexCoordinate(-1, 1))
        val filtered = calc.generateSpiralCoordinates(3, occupied)
        assertFalse(filtered.contains(HexCoordinate(1, 0)))
        assertFalse(filtered.contains(HexCoordinate(-1, 1)))
        assertEquals(35, filtered.size) // 37 - 2
    }

    @Test
    fun `generateSpiralCoordinates with empty occupied returns same as without`() {
        val a = calc.generateSpiralCoordinates(5)
        val b = calc.generateSpiralCoordinates(5, emptySet())
        assertEquals(a, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.hexgrid.launcher.domain.HexGridCalculatorOccupiedTest" 2>&1 | tail -20
```
Expected: FAIL — `generateSpiralCoordinates` has no `occupiedCells` parameter

- [ ] **Step 3: Update `HexGridCalculator.generateSpiralCoordinates()`**

Replace the existing method (lines 115-117):

```kotlin
    /**
     * Legacy method for compatibility.
     */
    fun generateSpiralCoordinates(maxRings: Int): List<HexCoordinate> {
        return generateWindmillSpiral(maxRings).map { it.first }
    }
```

with:

```kotlin
    /**
     * Returns hex coordinates in spiral order, optionally skipping occupied cells.
     * HexagonalGridView uses this to route app icons around widget areas.
     */
    fun generateSpiralCoordinates(
        maxRings: Int,
        occupiedCells: Set<HexCoordinate> = emptySet()
    ): List<HexCoordinate> {
        val all = generateWindmillSpiral(maxRings).map { it.first }
        return if (occupiedCells.isEmpty()) all else all.filter { it !in occupiedCells }
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:test --tests "com.hexgrid.launcher.domain.HexGridCalculatorOccupiedTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/domain/HexGridCalculator.kt \
        app/src/test/java/com/hexgrid/launcher/domain/HexGridCalculatorOccupiedTest.kt
git commit -m "feat(widgets): generateSpiralCoordinates accepts optional occupiedCells set"
```

---

### Task 6: `AppSorter` — skip occupied positions

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/domain/AppSorter.kt`
- Modify (add test): `app/src/test/java/com/hexy/launcher/domain/AppSorterTest.kt`

- [ ] **Step 1: Add failing test for occupiedCells in AppSorterTest.kt**

Add to the bottom of `AppSorterTest` class:

```kotlin
    @Test
    fun `occupied cells reduce result count for outer rings`() {
        val apps = (0..20).map { i -> createMockApp("app$i", usageCount = (20 - i).toLong()) }
        val occupied = setOf(
            HexCoordinate(2, 0),
            HexCoordinate(2, -1)
        )
        val full = AppSorter.sortApps(apps, mockContext)
        val filtered = AppSorter.sortApps(apps, mockContext, occupied)
        // filtered result should be smaller or equal (occupied positions become gaps)
        assertTrue(filtered.size <= full.size)
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.hexy.launcher.domain.AppSorterTest.occupied*" 2>&1 | tail -20
```
Expected: FAIL — `sortApps` has no 3rd parameter

- [ ] **Step 3: Update `AppSorter.sortApps()` signature and outer loop**

In `AppSorter.kt`, change the function signature from:

```kotlin
    fun sortApps(apps: List<AppInfo>, context: Context): List<AppInfo> {
```

to:

```kotlin
    fun sortApps(
        apps: List<AppInfo>,
        context: Context,
        occupiedCells: Set<HexCoordinate> = emptySet()
    ): List<AppInfo> {
```

Replace the outer apps loop `for (i in 7 until spiral.size)` with the full version including the occupied-cells check:

```kotlin
        // Add outer apps (ring 2+) - position 7 onwards
        for (i in 7 until spiral.size) {
            if (occupiedCells.isNotEmpty() && spiral[i].first in occupiedCells) continue

            val bucket = spiral[i].second

            if (bucket in 0..10 && bucketQueues[bucket].isNotEmpty()) {
                result.add(bucketQueues[bucket].removeAt(0))
            } else {
                // Empty slot - use placeholder
                result.add(emptyPlaceholder!!)
            }

            // Stop if all buckets empty
            if (bucketQueues.all { it.isEmpty() }) {
                // Fill remaining slots in current ring, then stop
                val currentRing = spiral[i].first.ring
                while (result.size < spiral.size && spiral[result.size].first.ring == currentRing) {
                    result.add(emptyPlaceholder!!)
                }
                break
            }
        }
```

- [ ] **Step 4: Run all AppSorter tests**

```bash
./gradlew :app:test --tests "com.hexy.launcher.domain.AppSorterTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all tests pass (including the new one)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/domain/AppSorter.kt \
        app/src/test/java/com/hexy/launcher/domain/AppSorterTest.kt
git commit -m "feat(widgets): AppSorter.sortApps accepts occupiedCells to skip widget positions"
```

---

### Task 7: `HexagonalGridView` — scroll callback, occupied cells, placement overlay

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt`

Context: `HexagonalGridView` extends `View`. `offsetX`/`offsetY` are currently private. The widget overlay in `FrameLayout` needs to mirror scroll movement. We add a `onScrollChanged` callback, a `setOccupiedCells()` method that re-layouts, and a placement mode overlay for widget placement UX.

- [ ] **Step 1: Add `onScrollChanged` callback**

After the `onAppLongClickListener` declaration, add:

```kotlin
    var onScrollChanged: ((offsetX: Float, offsetY: Float) -> Unit)? = null
```

In `computeScroll()`, after updating `offsetX`/`offsetY`, add the callback call:

```kotlin
    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat().coerceIn(minOffsetX, maxOffsetX)
            offsetY = scroller.currY.toFloat().coerceIn(minOffsetY, maxOffsetY)
            onScrollChanged?.invoke(offsetX, offsetY)
            invalidate()
        }
    }
```

In `animateToOrigin()` update listener, after setting `offsetY`, add:

```kotlin
            addUpdateListener { anim ->
                offsetX = anim.getAnimatedValue("x") as Float
                offsetY = anim.getAnimatedValue("y") as Float
                onScrollChanged?.invoke(offsetX, offsetY)
                invalidate()
            }
```

In `scrollToOrigin()`, after setting offsets to 0, add:

```kotlin
    fun scrollToOrigin() {
        offsetX = 0f
        offsetY = 0f
        onScrollChanged?.invoke(0f, 0f)
        invalidate()
    }
```

- [ ] **Step 2: Add `occupiedCells` set and `setOccupiedCells()`**

After the `hexPositions` declaration, add:

```kotlin
    private var occupiedCells: Set<HexCoordinate> = emptySet()
```

Add the public setter after `refreshSettings()`:

```kotlin
    /**
     * Called by WidgetManager when widgets are added/moved/removed.
     * Re-generates hexPositions excluding widget-occupied cells so icons flow around widgets.
     */
    fun setOccupiedCells(cells: Set<HexCoordinate>) {
        occupiedCells = cells
        hexPositions = calculator.generateSpiralCoordinates(
            maxOf(25, (apps.size / 6) + 5), occupiedCells
        )
        updateScrollBounds()
        invalidate()
    }
```

Update `setApps()` and `refreshSettings()` calls to `generateSpiralCoordinates` to pass `occupiedCells`:

In `setApps()`:
```kotlin
            hexPositions = calculator.generateSpiralCoordinates(
                maxOf(25, (apps.size / 6) + 5), occupiedCells
            )
```

In `refreshSettings()`:
```kotlin
        hexPositions = calculator.generateSpiralCoordinates(
            maxOf(25, (apps.size / 6) + 5), occupiedCells
        )
```

- [ ] **Step 3: Add placement mode fields and methods**

After the `onAppLongClickListener` declaration, add:

```kotlin
    // Placement mode — shown while user positions a new widget
    private var isPlacementMode = false
    private var placementPreviewHex: HexCoordinate? = null
    private var pendingWidgetWidthPx = 0
    private var pendingWidgetHeightPx = 0
    var onPlacementConfirmed: ((HexCoordinate) -> Unit)? = null
    var onPlacementCancelled: (() -> Unit)? = null

    private val placementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8064C8FF")  // semi-transparent cyan
        style = Paint.Style.FILL
    }
    private val placementStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF64C8FF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
```

Add placement mode methods:

```kotlin
    fun enterPlacementMode(widthPx: Int, heightPx: Int) {
        isPlacementMode = true
        pendingWidgetWidthPx = widthPx
        pendingWidgetHeightPx = heightPx
        placementPreviewHex = HexCoordinate.ORIGIN
        invalidate()
    }

    fun exitPlacementMode() {
        isPlacementMode = false
        placementPreviewHex = null
        invalidate()
    }
```

- [ ] **Step 4: Handle touch events in placement mode**

In `onTouchEvent`, add placement mode handling at the top (before the `gestureDetector` call):

```kotlin
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPlacementMode) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val centerX = width / 2f + offsetX
                    val centerY = height / 2f + offsetY
                    placementPreviewHex = calculator.pixelToHex(event.x, event.y, centerX, centerY)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    placementPreviewHex?.let { onPlacementConfirmed?.invoke(it) }
                    return true
                }
            }
            return true
        }
        return gestureDetector.onTouchEvent(event)
    }
```

- [ ] **Step 5: Draw placement overlay in `onDraw`**

At the end of `onDraw()`, before the closing brace, add:

```kotlin
        // Draw placement mode preview
        if (isPlacementMode) {
            placementPreviewHex?.let { hex ->
                val pos = calculator.hexToPixel(hex, width / 2f + offsetX, height / 2f + offsetY)
                val halfW = pendingWidgetWidthPx / 2f
                val halfH = pendingWidgetHeightPx / 2f
                val rect = RectF(pos.x - halfW, pos.y - halfH, pos.x + halfW, pos.y + halfH)
                canvas.drawRoundRect(rect, 12f, 12f, placementPaint)
                canvas.drawRoundRect(rect, 12f, 12f, placementStrokePaint)
            }
        }
```

Note: `RectF` is already available via `android.graphics.*` import.

- [ ] **Step 6: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt
git commit -m "feat(widgets): HexagonalGridView exposes scroll callback, occupied cells, placement mode"
```

---

## Chunk 3: Widget Orchestration

### Task 8: `WidgetManager` — orchestrator

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/widget/WidgetManager.kt`

Context: `WidgetManager` is owned by `MainActivity`. It holds widget host views keyed by `widgetId`, manages their layout params in the `FrameLayout`, and handles edit mode (move/resize). `MainActivity` delegates scroll sync, placement confirmation, and widget cleanup to it.

- [ ] **Step 1: Create `WidgetManager.kt`**

```kotlin
package com.hexgrid.launcher.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
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

        val view = host.createHostView(context, appWidgetId, info)
        attachView(view, entry)
        hostViews[entry.widgetId] = view

        setupEditGestures(view, entry)
    }

    /**
     * Remove a widget — detaches view, releases AppWidget ID, removes from store.
     */
    fun remove(widgetId: Int) {
        hostViews.remove(widgetId)?.let { container.removeView(it) }
        store.loadAll().find { it.widgetId == widgetId }?.let {
            host.releaseId(it.appWidgetId)
        }
        store.remove(widgetId)
    }

    /** Called on every HexagonalGridView scroll event to sync widget positions. */
    fun syncScroll(offsetX: Float, offsetY: Float) {
        for (entry in store.loadAll()) {
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
        for (entry in store.loadAll()) {
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

    fun loadedEntries(): List<WidgetEntry> = store.loadAll()

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun attachView(view: AppWidgetHostView, entry: WidgetEntry) {
        val lp = FrameLayout.LayoutParams(entry.widthPx, entry.heightPx)
        container.addView(view, lp)
        // Position after layout pass; use post() because width/height may be 0 at this point
        container.post { syncScroll(0f, 0f) }
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
        val currentEntry = store.loadAll().find { it.widgetId == originalEntry.widgetId } ?: return
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
                        val calc = hexCalculator()
                        val hexR = calc.let {
                            // Access hexRadius via reflection-free approach: snap to hexRadius steps
                            // We use a fixed step of the view's current min-dimension / 3 as approximation
                            minOf(originalWidthPx, originalHeightPx) / 3f
                        }
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
        val newHex = calc.pixelToHex(centerX, centerY, w / 2f, h / 2f)
        val updated = entry.copy(centerHexQ = newHex.q, centerHexR = newHex.r)
        store.update(updated)
        syncScroll(0f, 0f)
    }

    private fun saveResizedDimensions(view: AppWidgetHostView, entry: WidgetEntry) {
        val lp = view.layoutParams as FrameLayout.LayoutParams
        val updated = entry.copy(widthPx = lp.width, heightPx = lp.height)
        store.update(updated)
        notifyWidgetSize(entry.appWidgetId, lp.width, lp.height)
    }

    private fun notifyWidgetSize(appWidgetId: Int, widthPx: Int, heightPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appWidgetManager.updateAppWidgetSize(
                Bundle(), listOf(SizeF(widthPx.toFloat(), heightPx.toFloat()))
            )
        } else {
            @Suppress("DEPRECATION")
            appWidgetManager.updateAppWidgetSize(Bundle(), widthPx, heightPx, widthPx, heightPx)
        }
    }

    private fun snapToStep(value: Int, step: Int): Int {
        if (step <= 0) return value
        return (value / step) * step
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/widget/WidgetManager.kt
git commit -m "feat(widgets): add WidgetManager orchestrator for add/remove/resize/move"
```

---

## Chunk 4: UI Layer

### Task 9: `WidgetManagementActivity` + layouts

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/ui/WidgetManagementActivity.kt`
- Create: `app/src/main/res/layout/activity_widget_management.xml`
- Create: `app/src/main/res/layout/item_widget.xml`

- [ ] **Step 1: Create `activity_widget_management.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@android:color/background_dark">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnAddWidget"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:text="Add Widget"
        android:textColor="@android:color/white" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerWidgets"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:paddingHorizontal="16dp"
        android:clipToPadding="false" />

</LinearLayout>
```

- [ ] **Step 2: Create `item_widget.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="12dp">

    <ImageView
        android:id="@+id/imgWidgetIcon"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:scaleType="fitCenter" />

    <TextView
        android:id="@+id/textWidgetName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="12dp"
        android:textColor="@android:color/white"
        android:textSize="16sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnRemoveWidget"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Remove"
        style="@style/Widget.MaterialComponents.Button.OutlinedButton"
        android:textColor="@android:color/white" />

</LinearLayout>
```

- [ ] **Step 3: Create `WidgetManagementActivity.kt`**

```kotlin
package com.hexgrid.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.hexgrid.launcher.MainActivity
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.ActivityWidgetManagementBinding
import com.hexgrid.launcher.widget.WidgetEntry
import com.hexgrid.launcher.widget.WidgetHost
import com.hexgrid.launcher.widget.WidgetStore

class WidgetManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetManagementBinding
    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetStore: WidgetStore
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingAppWidgetId: Int = -1

    // Step 1: launch system widget picker
    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val pickedId = result.data
                ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (pickedId != -1) onWidgetPicked(pickedId)
            else releasePending()
        } else {
            releasePending()
        }
    }

    // Step 2 (if needed): request BIND_APPWIDGET permission
    private val bindPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingAppWidgetId != -1) {
            launchPlacementInMain(pendingAppWidgetId)
        } else {
            releasePending()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Widgets"

        widgetHost = WidgetHost(this)
        widgetStore = WidgetStore(this)

        binding.btnAddWidget.setOnClickListener { startWidgetPicker() }
        setupRecycler()
    }

    private fun startWidgetPicker() {
        pendingAppWidgetId = widgetHost.allocateId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
        }
        widgetPickerLauncher.launch(intent)
    }

    private fun onWidgetPicked(appWidgetId: Int) {
        pendingAppWidgetId = appWidgetId
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
            ?: run { releasePending(); return }

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        if (bound) {
            launchPlacementInMain(appWidgetId)
        } else {
            // Need explicit user permission
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            }
            bindPermissionLauncher.launch(intent)
        }
    }

    private fun launchPlacementInMain(appWidgetId: Int) {
        pendingAppWidgetId = -1
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            putExtra(MainActivity.EXTRA_PLACEMENT_WIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun releasePending() {
        if (pendingAppWidgetId != -1) {
            widgetHost.releaseId(pendingAppWidgetId)
            pendingAppWidgetId = -1
        }
    }

    private fun setupRecycler() {
        binding.recyclerWidgets.layoutManager = LinearLayoutManager(this)
        refreshList()
    }

    private fun refreshList() {
        val entries = widgetStore.loadAll()
        binding.recyclerWidgets.adapter = WidgetAdapter(entries) { entry ->
            // Remove: tell WidgetManager via broadcast or direct call
            // Since WidgetManager lives in MainActivity, we use a simple approach:
            // remove from store here and notify MainActivity via a custom broadcast.
            widgetHost.releaseId(entry.appWidgetId)
            widgetStore.remove(entry.widgetId)
            // Notify MainActivity to detach the view
            sendBroadcast(Intent(ACTION_WIDGET_REMOVED).apply {
                putExtra(EXTRA_WIDGET_ID, entry.widgetId)
                setPackage(packageName)
            })
            refreshList()
            Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private inner class WidgetAdapter(
        private val entries: List<WidgetEntry>,
        private val onRemove: (WidgetEntry) -> Unit
    ) : RecyclerView.Adapter<WidgetAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.imgWidgetIcon)
            val name: TextView = view.findViewById(R.id.textWidgetName)
            val remove: MaterialButton = view.findViewById(R.id.btnRemoveWidget)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            val info = appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            holder.name.text = info?.loadLabel(packageManager) ?: "Widget ${entry.widgetId}"
            info?.loadPreviewImage(this@WidgetManagementActivity, 0)?.let {
                holder.icon.setImageDrawable(it)
            } ?: info?.loadIcon(this@WidgetManagementActivity, 0)?.let {
                holder.icon.setImageDrawable(it)
            }
            holder.remove.setOnClickListener { onRemove(entry) }
        }

        override fun getItemCount() = entries.size
    }

    companion object {
        const val ACTION_WIDGET_REMOVED = "com.hexgrid.launcher.ACTION_WIDGET_REMOVED"
        const val EXTRA_WIDGET_ID = "widget_id"
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL` (MainActivity.EXTRA_PLACEMENT_WIDGET_ID will compile once Task 10 adds it)

If it fails on `MainActivity.EXTRA_PLACEMENT_WIDGET_ID`, temporarily use the string literal `"extra_placement_widget_id"` and update after Task 10.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/WidgetManagementActivity.kt \
        app/src/main/res/layout/activity_widget_management.xml \
        app/src/main/res/layout/item_widget.xml
git commit -m "feat(widgets): add WidgetManagementActivity with picker flow and widget list"
```

---

### Task 10: `MainActivity` integration

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/MainActivity.kt`

Context: `MainActivity` owns `WidgetManager`. On `onCreate`, it restores widgets and registers a broadcast receiver to handle removal from `WidgetManagementActivity`. On `onStart`/`onStop`, it calls `widgetManager.startListening()`/`stopListening()`. On `onNewIntent`, it detects `EXTRA_PLACEMENT_WIDGET_ID` and enters placement mode. Search fade animates widget alpha based on `currentQuery`.

- [ ] **Step 1: Update `MainActivity.kt`**

Replace the entire file with:

```kotlin
package com.hexgrid.launcher

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.UserHandle
import android.view.View
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexgrid.launcher.databinding.ActivityMainBinding
import com.hexgrid.launcher.domain.HexCoordinate
import com.hexgrid.launcher.domain.HexGridCalculator
import com.hexgrid.launcher.ui.LauncherViewModel
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.ui.SettingsActivity
import com.hexgrid.launcher.ui.WidgetManagementActivity
import com.hexgrid.launcher.util.SettingsManager
import com.hexgrid.launcher.widget.WidgetHost
import com.hexgrid.launcher.widget.WidgetManager
import com.hexgrid.launcher.widget.WidgetStore

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLACEMENT_WIDGET_ID = "extra_placement_widget_id"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var allApps: List<AppInfo> = emptyList()
    private lateinit var launcherAppsService: LauncherApps

    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetStore: WidgetStore
    private lateinit var widgetManager: WidgetManager

    private var widgetFadeAnimator: ValueAnimator? = null

    // ── Package change receiver ───────────────────────────────────────────────
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> if (!isReplacing) viewModel.reloadApps()
                Intent.ACTION_PACKAGE_ADDED -> viewModel.reloadApps()
            }
        }
    }

    // ── Legacy shortcut receiver ──────────────────────────────────────────────
    @Suppress("DEPRECATION")
    private val installShortcutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != "com.android.launcher.action.INSTALL_SHORTCUT") return
            val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: return
            val launchIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return
            if (launchIntent.action == null) launchIntent.action = Intent.ACTION_VIEW
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            val builder = ShortcutInfo.Builder(context, "legacy_${System.currentTimeMillis()}")
                .setShortLabel(name)
                .setIntent(launchIntent)
            val iconBitmap = intent.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
            if (iconBitmap != null) builder.setIcon(Icon.createWithBitmap(iconBitmap))
            try { shortcutManager.requestPinShortcut(builder.build(), null) } catch (_: Exception) { }
        }
    }

    // ── Widget removal broadcast from WidgetManagementActivity ────────────────
    private val widgetRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WidgetManagementActivity.ACTION_WIDGET_REMOVED) return
            val widgetId = intent.getIntExtra(WidgetManagementActivity.EXTRA_WIDGET_ID, -1)
            if (widgetId != -1) {
                widgetManager.remove(widgetId)
                updateOccupiedCells()
            }
        }
    }

    // ── LauncherApps.Callback ─────────────────────────────────────────────────
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) { viewModel.reloadApps() }
        override fun onPackageRemoved(packageName: String, user: UserHandle) { viewModel.reloadApps() }
        override fun onPackageChanged(packageName: String, user: UserHandle) { viewModel.reloadApps() }
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            viewModel.markAvailable(packageNames)
        }
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            viewModel.markUnavailable(packageNames)
        }
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            viewModel.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setActivityContext(this)
        launcherAppsService = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Register listeners
        launcherAppsService.registerCallback(launcherAppsCallback)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, pkgFilter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installShortcutReceiver,
                IntentFilter("com.android.launcher.action.INSTALL_SHORTCUT"), RECEIVER_EXPORTED)
            registerReceiver(widgetRemovedReceiver,
                IntentFilter(WidgetManagementActivity.ACTION_WIDGET_REMOVED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installShortcutReceiver,
                IntentFilter("com.android.launcher.action.INSTALL_SHORTCUT"))
            registerReceiver(widgetRemovedReceiver,
                IntentFilter(WidgetManagementActivity.ACTION_WIDGET_REMOVED))
        }

        // Widget setup
        widgetHost = WidgetHost(this)
        widgetStore = WidgetStore(this)
        widgetManager = WidgetManager(
            context = this,
            host = widgetHost,
            store = widgetStore,
            container = binding.hexGridContainer,
            hexCalculator = {
                val r = SettingsManager.getHexRadius(this)
                val o = when (SettingsManager.getHexOrientation(this)) {
                    SettingsManager.HexOrientation.POINTY_TOP -> HexGridCalculator.Orientation.POINTY_TOP
                    SettingsManager.HexOrientation.FLAT_TOP -> HexGridCalculator.Orientation.FLAT_TOP
                }
                HexGridCalculator(r, o)
            },
            containerWidth = { binding.hexGridContainer.width },
            containerHeight = { binding.hexGridContainer.height }
        )

        setupGrid()
        setupDock()
        setupBackHandler()
        setupWidgetScrollSync()

        widgetManager.restoreWidgets()
        updateOccupiedCells()

        viewModel.loadApps()

        // Handle placement intent if launched for widget placement
        handlePlacementIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        widgetManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        launcherAppsService.unregisterCallback(launcherAppsCallback)
        unregisterReceiver(packageChangeReceiver)
        unregisterReceiver(installShortcutReceiver)
        unregisterReceiver(widgetRemovedReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val dock = getCurrentDock()
            if (dock.isInSearchMode()) dock.exitSearchMode()
            binding.hexGrid.animateToOrigin()
        }
        intent?.let { handlePlacementIntent(it) }
    }

    private fun handlePlacementIntent(intent: Intent?) {
        val appWidgetId = intent?.getIntExtra(EXTRA_PLACEMENT_WIDGET_ID, -1) ?: -1
        if (appWidgetId == -1) return
        // Clear the extra so rotation doesn't re-trigger
        intent?.removeExtra(EXTRA_PLACEMENT_WIDGET_ID)

        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId) ?: return
        binding.hexGrid.enterPlacementMode(info.minWidth, info.minHeight)
        binding.hexGrid.onPlacementConfirmed = { centerHex ->
            binding.hexGrid.exitPlacementMode()
            binding.hexGrid.onPlacementConfirmed = null
            binding.hexGrid.onPlacementCancelled = null
            widgetManager.confirmPlacement(appWidgetId, centerHex)
            updateOccupiedCells()
        }
        binding.hexGrid.onPlacementCancelled = {
            binding.hexGrid.exitPlacementMode()
            binding.hexGrid.onPlacementConfirmed = null
            binding.hexGrid.onPlacementCancelled = null
            widgetHost.releaseId(appWidgetId)
        }
    }

    private fun setupWidgetScrollSync() {
        binding.hexGrid.onScrollChanged = { offsetX, offsetY ->
            widgetManager.syncScroll(offsetX, offsetY)
        }
    }

    private fun updateOccupiedCells() {
        val cells = widgetManager.occupiedCells()
        binding.hexGrid.setOccupiedCells(cells)
        // Re-sort apps with new occupied cells
        viewModel.loadApps()
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dock = getCurrentDock()
                if (binding.hexGrid.isInPlacementMode) {
                    binding.hexGrid.exitPlacementMode()
                    binding.hexGrid.onPlacementCancelled?.invoke()
                } else if (dock.isInSearchMode()) {
                    dock.exitSearchMode()
                } else {
                    binding.hexGrid.animateToOrigin()
                }
            }
        })
    }

    private fun getCurrentDock() = when (SettingsManager.getSearchPosition(this)) {
        SettingsManager.SearchPosition.TOP -> binding.dockTop
        else -> binding.dockBottom
    }

    private fun setupDock() {
        val position = SettingsManager.getSearchPosition(this)
        binding.dockTop.visibility = View.GONE
        binding.dockBottom.visibility = View.GONE

        val dock = when (position) {
            SettingsManager.SearchPosition.TOP -> binding.dockTop
            SettingsManager.SearchPosition.BOTTOM -> binding.dockBottom
            SettingsManager.SearchPosition.NONE -> binding.dockBottom
        }
        dock.visibility = View.VISIBLE

        dock.onSearchTextChanged = { query ->
            viewModel.filterApps(query)
            animateWidgetVisibility(query.isBlank())
        }
        dock.onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
        dock.onAppClick = { app ->
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                viewModel.launchApp(app)
            }
        }
        dock.onAppLongClick = { app ->
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(arrayOf("Remove from Dock")) { _, _ -> dock.removeApp(app) }
                .show()
        }
        dock.refreshSettings()
    }

    private fun animateWidgetVisibility(visible: Boolean) {
        val showDuringSearch = SettingsManager.getShowWidgetsDuringSearch(this)
        if (showDuringSearch) return  // always visible — no animation needed

        val targetAlpha = if (visible) 1f else 0f
        widgetFadeAnimator?.cancel()
        widgetFadeAnimator = ValueAnimator.ofFloat(
            widgetManager.loadedEntries()
                .mapNotNull { binding.hexGridContainer.findViewWithTag<View>("widget_${it.widgetId}") }
                .firstOrNull()?.alpha ?: (if (visible) 0f else 1f),
            targetAlpha
        ).apply {
            duration = 200
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                for (i in 0 until binding.hexGridContainer.childCount) {
                    val child = binding.hexGridContainer.getChildAt(i)
                    if (child != binding.hexGrid) child.alpha = alpha
                }
            }
            start()
        }
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                viewModel.launchApp(app)
            }
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ -> showContextMenu(app) }

        viewModel.apps.observe(this) { apps ->
            val isFiltering = viewModel.currentQuery.isNotBlank()
            binding.hexGrid.setApps(apps, centerOnChange = isFiltering)
        }

        viewModel.allApps.observe(this) { apps ->
            allApps = apps
            getCurrentDock().loadDockApps(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Pin to Dock", "Hide App", "App Info", "Uninstall")) { _, which ->
                when (which) {
                    0 -> getCurrentDock().addApp(app)
                    1 -> viewModel.hideApp(app)
                    2 -> startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${app.packageName}")
                        }
                    )
                    3 -> startActivity(
                        Intent(Intent.ACTION_DELETE).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                    )
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding.hexGrid.refreshSettings()
        setupDock()
        if (SettingsManager.getIconCacheDirty(this)) {
            viewModel.reloadApps()
        } else {
            viewModel.loadApps()
        }
    }
}
```

**Note on back-press handler:** The reflection approach for checking `isPlacementMode` is fragile. Instead, expose a `val isInPlacementMode: Boolean get() = isPlacementMode` property on `HexagonalGridView` and use that. Add the following to `HexagonalGridView.kt`:

```kotlin
    val isInPlacementMode: Boolean get() = isPlacementMode
```

Then update the back handler in `MainActivity`:
```kotlin
if (binding.hexGrid.isInPlacementMode) {
```

- [ ] **Step 2: Add `isInPlacementMode` to `HexagonalGridView.kt`**

The `MainActivity` above references `binding.hexGrid.isInPlacementMode`. Add this property to `HexagonalGridView.kt` after `exitPlacementMode()`:

```kotlin
    val isInPlacementMode: Boolean get() = isPlacementMode
```

This must be done **before** Step 1 is compiled (or at the same time), since `MainActivity` depends on it.

- [ ] **Step 3: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt \
        app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt
git commit -m "feat(widgets): integrate WidgetManager into MainActivity with scroll sync and placement mode"
```

---

### Task 11: `AndroidManifest.xml` + `activity_main.xml`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Add `android:id` to `activity_main.xml` root FrameLayout**

The root `FrameLayout` currently has no ID. Add only the `android:id` attribute — do NOT change any other attributes or child views. The file currently looks like:

```xml
<FrameLayout xmlns:android="..."
    xmlns:app="..."
    android:layout_width="match_parent"
    android:layout_height="match_parent">
```

Change to:

```xml
<FrameLayout xmlns:android="..."
    xmlns:app="..."
    android:id="@+id/hexGridContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
```

All three child views (`HexagonalGridView`, `dockTop`, `dockBottom`) remain exactly as they are. Only the `android:id` attribute is new.

- [ ] **Step 2: Update `AndroidManifest.xml`**

Add after the existing `uses-permission` block:

```xml
    <uses-permission android:name="android.permission.BIND_APPWIDGET" />
```

Add after the `PinShortcutActivity` block:

```xml
        <activity
            android:name=".ui.WidgetManagementActivity"
            android:exported="false"
            android:label="Manage Widgets" />
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Full debug build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
        app/src/main/res/layout/activity_main.xml
git commit -m "feat(widgets): add BIND_APPWIDGET permission and register WidgetManagementActivity"
```

---

### Task 12: `SettingsActivity` + `activity_settings.xml` — widget controls

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/SettingsActivity.kt`
- Modify: `app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: Add widget card to `activity_settings.xml`**

Append a new `MaterialCardView` at the end of the `LinearLayout` (before `</LinearLayout>` closing tag), after the last existing card:

```xml
        <!-- Widgets Card -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="4dp"
            app:cardBackgroundColor="#1E1E1E">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Widgets"
                    android:textSize="18sp"
                    android:textStyle="bold"
                    android:textColor="@android:color/white"
                    android:layout_marginBottom="16dp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnManageWidgets"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:text="Manage Widgets"
                    android:textColor="@android:color/white" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="Show widgets during search"
                        android:textColor="@android:color/white"
                        android:textSize="14sp" />

                    <androidx.appcompat.widget.SwitchCompat
                        android:id="@+id/switchShowWidgetsDuringSearch"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />

                </LinearLayout>

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Wire up controls in `SettingsActivity.kt`**

Add a call to `setupWidgetControls()` inside `onCreate()`, after `setupBackupRestore()`:

```kotlin
        setupWidgetControls()
```

Add the method:

```kotlin
    private fun setupWidgetControls() {
        binding.btnManageWidgets.setOnClickListener {
            startActivity(Intent(this, WidgetManagementActivity::class.java))
        }

        binding.switchShowWidgetsDuringSearch.isChecked =
            SettingsManager.getShowWidgetsDuringSearch(this)
        binding.switchShowWidgetsDuringSearch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowWidgetsDuringSearch(this, isChecked)
        }
    }
```

Add the import at the top of `SettingsActivity.kt`:

```kotlin
import com.hexgrid.launcher.ui.WidgetManagementActivity
```

- [ ] **Step 3: Full build to verify**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all unit tests**

```bash
./gradlew :app:test 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`, all tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/SettingsActivity.kt \
        app/src/main/res/layout/activity_settings.xml
git commit -m "feat(widgets): add Manage Widgets button and show-during-search toggle in Settings"
```

---

## Post-implementation Verification

- [ ] **Verify full build and tests pass**

```bash
./gradlew :app:assembleDebug :app:test 2>&1 | tail -40
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Manual smoke test checklist (on device)**

1. Open Settings → Widgets card is visible ✓
2. Tap "Manage Widgets" → `WidgetManagementActivity` opens ✓
3. Tap "Add Widget" → system widget picker opens ✓
4. Select a widget (e.g. Clock) → returns to grid in placement mode ✓
5. Tap a hex cell → widget appears on grid ✓
6. Scroll the grid → widget scrolls in sync ✓
7. Long-press widget → edit mode enters, drag moves widget ✓
8. Drag from corner → resize handles work ✓
9. Type in search bar → widget fades out (if toggle is off) ✓
10. Clear search → widget fades back in ✓
11. Open Manage Widgets → widget listed with Remove button ✓
12. Remove widget → disappears from grid ✓
13. Rotate screen → widget restored from store ✓
14. Uninstall widget's app → cleaned up on next launch ✓
