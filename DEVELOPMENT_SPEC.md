# Hexy Launcher - Complete Development Specification

> **Target Audience**: AI Code Assistant (Google Gemini Flash)
> **Project Type**: Android Launcher Application
> **Language**: Kotlin
> **Min SDK**: 26 (Android 8.0)
> **Target SDK**: 34 (Android 14)

---

## 1. Project Overview

Build an Android Launcher with a **hexagonal grid** layout. Apps are displayed as hexagonal icons, sorted by:

1. **Dominant Color** (6 color palette bins)
2. **Usage Frequency** (most used = closer to center)
3. **Recency** (2 inner rings = most recently used)

### Core Features

- Hexagonal grid with infinite scroll
- Widget support (snap to hex grid)
- Configurable search bar (top/bottom/hidden)
- Long-press context menu (hide/delete app)
- Blurred status bar overlay

---

## 2. Project Structure

Create exact file structure:

```
HexyLauncher/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/hexy/launcher/
│           │   ├── HexyLauncherApp.kt
│           │   ├── MainActivity.kt
│           │   ├── data/
│           │   │   ├── AppInfo.kt
│           │   │   ├── AppRepository.kt
│           │   │   └── UsageStatsHelper.kt
│           │   ├── domain/
│           │   │   ├── HexCoordinate.kt
│           │   │   ├── HexGridCalculator.kt
│           │   │   └── AppSorter.kt
│           │   ├── ui/
│           │   │   ├── HexagonalGridView.kt
│           │   │   ├── HexagonDrawable.kt
│           │   │   ├── LauncherViewModel.kt
│           │   │   └── SearchBar.kt
│           │   └── util/
│           │       └── ColorExtractor.kt
│           └── res/
│               ├── layout/
│               │   └── activity_main.xml
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── drawable/
│                   └── hexagon_mask.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 3. Detailed File Specifications

### 3.1 Root `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "HexyLauncher"
include(":app")
```

### 3.2 Root `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
}
```

### 3.3 `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hexy.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hexy.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 3.4 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 4. Core Data Classes

### 4.1 `data/AppInfo.kt`

```kotlin
package com.hexy.launcher.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val dominantColor: Int,      // Extracted from icon
    val colorBucket: Int,        // 0-5 (6 buckets)
    val usageCount: Long,        // From UsageStats
    val lastUsedTimestamp: Long, // For recency sorting
    val isShortcut: Boolean = false,  // True if PWA/shortcut
    val shortcutId: String? = null    // ShortcutInfo ID if applicable
)
```

### 4.2 `domain/HexCoordinate.kt`

```kotlin
package com.hexy.launcher.domain

/**
 * Axial coordinate system for hexagonal grids.
 * q = column, r = row (axial coordinates)
 *
 * Neighbors in pointy-top hexagon:
 *   (+1, 0), (-1, 0), (0, +1), (0, -1), (+1, -1), (-1, +1)
 */
data class HexCoordinate(val q: Int, val r: Int) {

    // Convert to cube coordinates for distance calculation
    val s: Int get() = -q - r

    // Ring number (distance from center)
    val ring: Int get() = maxOf(
        kotlin.math.abs(q),
        kotlin.math.abs(r),
        kotlin.math.abs(s)
    )

    fun neighbors(): List<HexCoordinate> = listOf(
        HexCoordinate(q + 1, r),
        HexCoordinate(q - 1, r),
        HexCoordinate(q, r + 1),
        HexCoordinate(q, r - 1),
        HexCoordinate(q + 1, r - 1),
        HexCoordinate(q - 1, r + 1)
    )

    companion object {
        val ORIGIN = HexCoordinate(0, 0)
    }
}
```

---

## 5. Hexagonal Grid Math

### 5.1 `domain/HexGridCalculator.kt`

```kotlin
package com.hexy.launcher.domain

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Converts hex coordinates to screen pixels and vice versa.
 * Uses "pointy-top" orientation.
 */
class HexGridCalculator(
    private val hexRadius: Float  // Distance from center to vertex
) {
    // Hex dimensions
    private val hexWidth = hexRadius * 2f
    private val hexHeight = sqrt(3f) * hexRadius

    /**
     * Convert axial (q, r) to screen pixel (x, y).
     * Center hex (0,0) is at screen center.
     */
    fun hexToPixel(hex: HexCoordinate, centerX: Float, centerY: Float): PointF {
        val x = hexRadius * (3f / 2f * hex.q)
        val y = hexRadius * (sqrt(3f) / 2f * hex.q + sqrt(3f) * hex.r)
        return PointF(centerX + x, centerY + y)
    }

    /**
     * Convert screen pixel to nearest hex coordinate.
     */
    fun pixelToHex(px: Float, py: Float, centerX: Float, centerY: Float): HexCoordinate {
        val x = px - centerX
        val y = py - centerY

        val q = (2f / 3f * x) / hexRadius
        val r = (-1f / 3f * x + sqrt(3f) / 3f * y) / hexRadius

        return axialRound(q, r)
    }

    /**
     * Generate hex coordinates in spiral order from center.
     * Ring 0 = center (1 hex)
     * Ring 1 = 6 hexes around center
     * Ring N = 6*N hexes
     */
    fun generateSpiralCoordinates(maxRings: Int): List<HexCoordinate> {
        val result = mutableListOf<HexCoordinate>()
        result.add(HexCoordinate.ORIGIN)

        val directions = listOf(
            HexCoordinate(1, 0), HexCoordinate(0, 1), HexCoordinate(-1, 1),
            HexCoordinate(-1, 0), HexCoordinate(0, -1), HexCoordinate(1, -1)
        )

        for (ring in 1..maxRings) {
            var hex = HexCoordinate(-ring, ring) // Start at top-left of ring
            for (dir in 0 until 6) {
                for (step in 0 until ring) {
                    result.add(hex)
                    hex = HexCoordinate(
                        hex.q + directions[dir].q,
                        hex.r + directions[dir].r
                    )
                }
            }
        }
        return result
    }

    private fun axialRound(q: Float, r: Float): HexCoordinate {
        val s = -q - r
        var rq = kotlin.math.round(q).toInt()
        var rr = kotlin.math.round(r).toInt()
        var rs = kotlin.math.round(s).toInt()

        val qDiff = kotlin.math.abs(rq - q)
        val rDiff = kotlin.math.abs(rr - r)
        val sDiff = kotlin.math.abs(rs - s)

        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs
        } else if (rDiff > sDiff) {
            rr = -rq - rs
        }

        return HexCoordinate(rq, rr)
    }
}
```

---

## 6. Color Extraction & Sorting

### 6.1 `util/ColorExtractor.kt`

```kotlin
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
```

### 6.2 `domain/AppSorter.kt`

```kotlin
package com.hexy.launcher.domain

import com.hexy.launcher.data.AppInfo

/**
 * Sorts apps for hexagonal grid placement.
 *
 * Algorithm:
 * 1. Ring 0 (center): 1 most-used app overall
 * 2. Rings 1-2 (inner rings): 18 most RECENTLY used apps (6 + 12 = 18 slots)
 * 3. Ring 3+: Apps grouped by color bucket, then sorted by usage within bucket
 *
 * Color distribution:
 * - Divide outer rings into 6 sectors (60° each)
 * - Each sector corresponds to one color bucket
 * - Within sector, more-used apps are closer to center
 */
object AppSorter {

    fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()

        val result = mutableListOf<AppInfo>()
        val remaining = apps.toMutableList()

        // 1. Ring 0 (center): Single most-used app
        val mostUsed = remaining.maxByOrNull { it.usageCount }
        if (mostUsed != null) {
            result.add(mostUsed)
            remaining.remove(mostUsed)
        }

        // 2. Rings 1-2 (18 slots total: 6 + 12): Most recently used apps
        val recentApps = remaining
            .sortedByDescending { it.lastUsedTimestamp }
            .take(18)
        result.addAll(recentApps)
        remaining.removeAll(recentApps.toSet())

        // 3. Remaining: Group by color bucket, sort by usage
        val byBucket = remaining.groupBy { it.colorBucket }

        // Interleave buckets for even distribution
        val sortedBuckets = (0..5).map { bucket ->
            byBucket[bucket]?.sortedByDescending { it.usageCount } ?: emptyList()
        }

        // Zip buckets together (round-robin)
        val maxSize = sortedBuckets.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (bucket in sortedBuckets) {
                if (i < bucket.size) {
                    result.add(bucket[i])
                }
            }
        }

        return result
    }
}
```

---

## 7. Data Layer

### 7.1 `data/AppRepository.kt`

```kotlin
package com.hexy.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.hexy.launcher.util.ColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<AppInfo>()
        val usageStats = UsageStatsHelper.getUsageStats(context)

        // 1. Load regular launcher apps
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        resolveInfos.mapNotNullTo(apps) { ri ->
            try {
                val packageName = ri.activityInfo.packageName
                val label = ri.loadLabel(packageManager).toString()
                val icon = ri.loadIcon(packageManager)
                val (dominantColor, bucket) = ColorExtractor.extractColor(icon)
                val stats = usageStats[packageName]

                AppInfo(
                    packageName = packageName,
                    label = label,
                    icon = icon,
                    dominantColor = dominantColor,
                    colorBucket = bucket,
                    usageCount = stats?.totalTimeInForeground ?: 0L,
                    lastUsedTimestamp = stats?.lastTimeUsed ?: 0L,
                    isShortcut = false
                )
            } catch (e: Exception) {
                null
            }
        }

        // 2. Load pinned shortcuts (including PWAs)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            shortcutManager?.pinnedShortcuts?.mapNotNullTo(apps) { shortcut ->
                try {
                    val icon = shortcutManager.getShortcutIconDrawable(shortcut, 0)
                        ?: context.packageManager.getApplicationIcon(shortcut.`package`)
                    val (dominantColor, bucket) = ColorExtractor.extractColor(icon)

                    AppInfo(
                        packageName = shortcut.`package`,
                        label = shortcut.shortLabel?.toString() ?: shortcut.id,
                        icon = icon,
                        dominantColor = dominantColor,
                        colorBucket = bucket,
                        usageCount = 0L, // Shortcuts don't have usage stats
                        lastUsedTimestamp = 0L,
                        isShortcut = true,
                        shortcutId = shortcut.id
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }

        apps
    }

    fun launchApp(app: AppInfo) {
        if (app.isShortcut && app.shortcutId != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            shortcutManager?.startShortcut(app.packageName, app.shortcutId, null, null)
        } else {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.let { context.startActivity(it) }
        }
    }
}
```

### 7.2 `data/UsageStatsHelper.kt`

```kotlin
package com.hexy.launcher.data

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageStatsHelper {

    fun getUsageStats(context: Context): Map<String, UsageStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30) // Last 30 days
        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return usageStatsList
            .groupBy { it.packageName }
            .mapValues { (_, stats) ->
                // Merge stats for same package
                stats.maxByOrNull { it.lastTimeUsed } ?: stats.first()
            }
    }

    fun hasPermission(context: Context): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.HOUR, -1)
        val startTime = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
        return stats.isNotEmpty()
    }
}
```

---

## 8. UI Components

### 8.1 `ui/HexagonalGridView.kt`

```kotlin
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
    private val calculator = HexGridCalculator(hexRadius)
    private var apps: List<AppInfo> = emptyList()
    private var hexPositions: List<HexCoordinate> = emptyList()

    private var offsetX = 0f
    private var offsetY = 0f

    private val hexPath = Path()
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var onAppClickListener: ((AppInfo) -> Unit)? = null
    private var onAppLongClickListener: ((AppInfo, Float, Float) -> Unit)? = null

    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        // Pre-generate hex positions (enough for ~200 apps)
        hexPositions = calculator.generateSpiralCoordinates(10)
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

            // Draw app icon (clipped to hexagon)
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
        val iconSize = (hexRadius * 1.3f).toInt()
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
```

---

## 9. ViewModel & Activity

### 9.1 `ui/LauncherViewModel.kt`

```kotlin
package com.hexy.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.data.AppRepository
import com.hexy.launcher.domain.AppSorter
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _hiddenApps = mutableSetOf<String>()

    fun loadApps() {
        viewModelScope.launch {
            val allApps = repository.loadInstalledApps()
            val visible = allApps.filter { it.packageName !in _hiddenApps }
            val sorted = AppSorter.sortApps(visible)
            _apps.value = sorted
        }
    }

    fun launchApp(app: AppInfo) {
        repository.launchApp(app.packageName)
    }

    fun hideApp(app: AppInfo) {
        _hiddenApps.add(app.packageName)
        loadApps() // Refresh
    }
}
```

### 9.2 `MainActivity.kt`

```kotlin
package com.hexy.launcher

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.data.UsageStatsHelper
import com.hexy.launcher.databinding.ActivityMainBinding
import com.hexy.launcher.ui.LauncherViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGrid()
        checkUsagePermission()
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            viewModel.launchApp(app)
        }

        binding.hexGrid.setOnAppLongClick { app, x, y ->
            showContextMenu(app, x, y)
        }

        viewModel.apps.observe(this) { apps ->
            binding.hexGrid.setApps(apps)
        }
    }

    private fun showContextMenu(app: com.hexy.launcher.data.AppInfo, x: Float, y: Float) {
        val popup = PopupMenu(this, binding.hexGrid)
        popup.menuInflater.inflate(R.menu.app_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_hide -> {
                    viewModel.hideApp(app)
                    true
                }
                R.id.action_uninstall -> {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun checkUsagePermission() {
        if (!UsageStatsHelper.hasPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            viewModel.loadApps()
        }
    }

    override fun onResume() {
        super.onResume()
        if (UsageStatsHelper.hasPermission(this)) {
            viewModel.loadApps()
        }
    }
}
```

---

## 10. Resources

### 10.1 `res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.hexy.launcher.ui.HexagonalGridView
        android:id="@+id/hexGrid"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Optional: Search Bar at bottom -->
    <EditText
        android:id="@+id/searchBar"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_gravity="bottom"
        android:layout_margin="16dp"
        android:background="@drawable/search_bg"
        android:hint="Search apps..."
        android:padding="12dp"
        android:visibility="gone" />

</FrameLayout>
```

### 10.2 `res/menu/app_context_menu.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_hide"
        android:title="Hide" />
    <item
        android:id="@+id/action_uninstall"
        android:title="Uninstall" />
</menu>
```

### 10.3 `res/drawable/search_bg.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1E1E1E" />
    <corners android:radius="24dp" />
</shape>
```

### 10.4 `res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

### 10.5 `res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.HexyLauncher" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowTranslucentStatus">true</item>
    </style>
</resources>
```

### 10.6 `res/values/strings.xml`

```xml
<resources>
    <string name="app_name">Hexy Launcher</string>
</resources>
```

---

## 11. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions"
        xmlns:tools="http://schemas.android.com/tools" />

    <application
        android:name=".HexyLauncherApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.HexyLauncher">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 11.1 `HexyLauncherApp.kt`

```kotlin
package com.hexy.launcher

import android.app.Application

class HexyLauncherApp : Application()
```

---

## 12. Build & Run Instructions

1. **Open in Android Studio**: File → Open → Select `HexyLauncher` folder
2. **Sync Gradle**: Click "Sync Project with Gradle Files"
3. **Create Launcher Icons**: Right-click `res` → New → Image Asset → Create `ic_launcher`
4. **Run**: Select emulator/device → Click Run (Shift+F10)
5. **Grant Permission**: When prompted, enable "Usage Access" for Hexy Launcher
6. **Set as Home**: When you press Home button, select "Hexy Launcher"

---

## 13. Future Enhancements (Phase 2)

- Widget support (AppWidgetHost integration)
- Search functionality filter
- Configurable search bar position
- App drawer with alphabet scroll
- Gesture support (swipe up/down)
- Theme customization
- Backup/restore hidden apps
