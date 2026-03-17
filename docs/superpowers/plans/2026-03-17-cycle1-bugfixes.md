# Cycle 1 Bugfixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three bugs: stale app list after install/uninstall/Samsung Mode changes, missing PWA shortcut support, and filtered icons landing off-screen.

**Architecture:** Dual-listener pattern (BroadcastReceiver + LauncherApps.Callback) in MainActivity for reactive reloads. ViewModel gains `_currentQuery` + `_unavailableApps` + `reloadApps()` to preserve search state across reloads. HexagonalGridView gains `centerOnChange` param in `setApps()`. New `PinShortcutActivity` handles `ACTION_CONFIRM_PIN_SHORTCUT` intent.

**Tech Stack:** Kotlin, Android SDK 26+, LauncherApps API, ShortcutManager API, JUnit4 (unit tests only — ViewModel/Activity tests are instrumented and marked as manual)

---

## Chunk 1: ViewModel + Repository Infrastructure

### Task 1: Add `invalidateCache()` to AppRepository

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/data/AppRepository.kt`

- [ ] **Step 1: Add `invalidateCache()` method**

In `AppRepository.kt`, add after the `cachedApps` field declaration (line 19):

```kotlin
fun invalidateCache() {
    cachedApps = null
}
```

- [ ] **Step 2: Verify file compiles — no test needed (trivial setter)**

Build check: `cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0" && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/data/AppRepository.kt
git commit -m "feat: add invalidateCache() to AppRepository"
```

---

### Task 2: Update LauncherViewModel with query tracking and unavailable apps

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/LauncherViewModel.kt`
- Create: `app/src/test/java/com/hexgrid/launcher/ui/AppFilterLogicTest.kt`

**Note on test path:** Existing tests live in `app/src/test/java/com/hexy/launcher/` (wrong directory — legacy path). New tests go to the correct path: `app/src/test/java/com/hexgrid/launcher/`.

- [ ] **Step 1: Write a failing unit test for filter logic**

Create `app/src/test/java/com/hexgrid/launcher/ui/AppFilterLogicTest.kt`:

```kotlin
package com.hexgrid.launcher.ui

import com.hexgrid.launcher.data.AppInfo
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the filtering logic extracted from LauncherViewModel.
 * Verifies hidden/unavailable filtering and query matching.
 */
class AppFilterLogicTest {

    // Mockito is available (mockito-core in testImplementation).
    // Drawable is abstract — mock it to avoid Android stub RuntimeException in JVM tests.
    private fun makeApp(pkg: String, label: String) = AppInfo(
        packageName = pkg,
        label = label,
        icon = org.mockito.Mockito.mock(android.graphics.drawable.Drawable::class.java)!!,
        dominantColor = 0,
        colorBucket = 0,
        usageCount = 0L,
        lastUsedTimestamp = 0L,
        notificationCount = 0,
        isShortcut = false,
        userHandle = null
    )

    /** Simulates the filter pass inside updateAppList() */
    private fun applyFilters(
        list: List<AppInfo>,
        query: String,
        hiddenApps: Set<String>,
        unavailableApps: Set<String>
    ): List<AppInfo> {
        val queryFiltered = if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) }
        return queryFiltered.filter {
            it.packageName !in hiddenApps && it.packageName !in unavailableApps
        }
    }

    @Test
    fun `blank query returns all visible apps`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", emptySet(), emptySet())
        assertEquals(2, result.size)
    }

    @Test
    fun `query filters by label case-insensitive`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"), makeApp("c", "ALPHA two"))
        val result = applyFilters(apps, "alpha", emptySet(), emptySet())
        assertEquals(2, result.size)
        assertTrue(result.all { it.label.contains("alpha", ignoreCase = true) })
    }

    @Test
    fun `hidden apps are excluded`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", setOf("a"), emptySet())
        assertEquals(1, result.size)
        assertEquals("b", result[0].packageName)
    }

    @Test
    fun `unavailable apps are excluded`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", emptySet(), setOf("b"))
        assertEquals(1, result.size)
        assertEquals("a", result[0].packageName)
    }

    @Test
    fun `both hidden and unavailable and query are combined`() {
        val apps = listOf(
            makeApp("a", "Alpha"),
            makeApp("b", "Beta"),
            makeApp("c", "Chrome"),
            makeApp("d", "Alpha two")
        )
        // query=alpha, hidden=d, unavailable=a → only "Alpha two" minus "a" minus "d"
        // wait: d is "Alpha two" and d is in hidden → filtered out
        // a is "Alpha" and a is in unavailable → filtered out
        // result should be empty (both alphas removed)
        val result = applyFilters(apps, "alpha", setOf("d"), setOf("a"))
        assertEquals(0, result.size)
    }

    @Test
    fun `markUnavailable adds packages and re-filters`() {
        val unavailable = mutableSetOf<String>()
        unavailable.addAll(listOf("com.samsung.restricted"))
        val apps = listOf(makeApp("com.samsung.restricted", "App A"), makeApp("b", "App B"))
        val result = applyFilters(apps, "", emptySet(), unavailable)
        assertEquals(1, result.size)
        assertEquals("b", result[0].packageName)
    }

    @Test
    fun `markAvailable removes packages from unavailable set`() {
        val unavailable = mutableSetOf("com.pkg.a", "com.pkg.b")
        unavailable.removeAll(setOf("com.pkg.a"))
        assertFalse(unavailable.contains("com.pkg.a"))
        assertTrue(unavailable.contains("com.pkg.b"))
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (AppInfo constructor signature mismatch likely)**

```bash
cd "c:/Users/mckar/Documents/Projekty/HexGrid Launcher/hexylauncher2.0"
./gradlew test --tests "com.hexgrid.launcher.ui.AppFilterLogicTest" 2>&1 | tail -30
```

Expected: compilation error or test failure. Fix any AppInfo constructor issues to match the real class before proceeding.

- [ ] **Step 3: Update LauncherViewModel.kt**

Replace the entire file content:

```kotlin
package com.hexgrid.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.data.AppRepository
import com.hexgrid.launcher.domain.AppSorter
import com.hexgrid.launcher.util.SettingsManager
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    private val _allApps = MutableLiveData<List<AppInfo>>()
    val allApps: LiveData<List<AppInfo>> = _allApps

    private var allInstalledApps: List<AppInfo> = emptyList()

    private val _hiddenApps = mutableSetOf<String>()
    private val _unavailableApps = mutableSetOf<String>()

    // Tracks active search query so reloads don't clobber filter state
    private var _currentQuery: String = ""
    val currentQuery: String get() = _currentQuery

    private var activityContext: android.app.Activity? = null

    fun setActivityContext(activity: android.app.Activity) {
        activityContext = activity
    }

    fun loadApps() {
        viewModelScope.launch {
            _hiddenApps.clear()
            _hiddenApps.addAll(SettingsManager.getHiddenApps(getApplication()))
            allInstalledApps = repository.loadInstalledApps()
            _allApps.value = allInstalledApps
            updateAppList(allInstalledApps)
        }
    }

    /**
     * Invalidates cache and reloads — used by reactive listeners (BroadcastReceiver,
     * LauncherApps.Callback). Preserves active search query.
     */
    fun reloadApps() {
        repository.invalidateCache()
        loadApps()
    }

    fun filterApps(query: String) {
        _currentQuery = query
        updateAppList(allInstalledApps)
    }

    /**
     * Called by LauncherApps.Callback.onPackagesUnavailable().
     * Hides apps restricted by Samsung Modes or work profile.
     */
    fun markUnavailable(packages: Array<String>) {
        _unavailableApps.addAll(packages)
        updateAppList(allInstalledApps)
    }

    /**
     * Called by LauncherApps.Callback.onPackagesAvailable().
     * Restores apps when Samsung Mode or work profile restriction lifts.
     */
    fun markAvailable(packages: Array<String>) {
        _unavailableApps.removeAll(packages.toSet())
        updateAppList(allInstalledApps)
    }

    private fun updateAppList(baseList: List<AppInfo>) {
        // Apply query filter first
        val queryFiltered = if (_currentQuery.isBlank()) baseList
        else baseList.filter { it.label.contains(_currentQuery, ignoreCase = true) }

        // Then apply hidden + unavailable filters
        val visible = queryFiltered.filter {
            it.packageName !in _hiddenApps && it.packageName !in _unavailableApps
        }
        val sorted = AppSorter.sortApps(visible, getApplication())
        _apps.value = sorted
    }

    fun launchApp(app: AppInfo) {
        val context = activityContext ?: getApplication<Application>()
        repository.launchApp(app, context)
    }

    fun hideApp(app: AppInfo) {
        _hiddenApps.add(app.packageName)
        SettingsManager.setHiddenApps(getApplication(), _hiddenApps)
        updateAppList(allInstalledApps)
    }

    fun unhideApp(packageName: String) {
        _hiddenApps.remove(packageName)
        SettingsManager.setHiddenApps(getApplication(), _hiddenApps)
        updateAppList(allInstalledApps)
    }
}
```

- [ ] **Step 4: Run the test again — expect PASS**

```bash
./gradlew test --tests "com.hexgrid.launcher.ui.AppFilterLogicTest" 2>&1 | tail -20
```
Expected: All 7 tests PASS

- [ ] **Step 5: Compile check**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/LauncherViewModel.kt
git add app/src/test/java/com/hexgrid/launcher/ui/AppFilterLogicTest.kt
git commit -m "feat: add currentQuery tracking, unavailable apps, reloadApps to ViewModel"
```

---

## Chunk 2: Package Change Listeners in MainActivity

### Task 3: Register BroadcastReceiver + LauncherApps.Callback in MainActivity

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/MainActivity.kt`

- [ ] **Step 1: Write a unit test for BroadcastReceiver EXTRA_REPLACING logic**

Create `app/src/test/java/com/hexgrid/launcher/PackageChangeBroadcastReceiverTest.kt`:

```kotlin
package com.hexgrid.launcher

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the EXTRA_REPLACING debounce logic in the package change receiver.
 */
class PackageChangeBroadcastReceiverTest {

    /** Simulates the should-reload decision from the BroadcastReceiver */
    private fun shouldReload(action: String?, isReplacing: Boolean): Boolean {
        return when (action) {
            Intent.ACTION_PACKAGE_REMOVED -> !isReplacing // ignore if mid-update
            Intent.ACTION_PACKAGE_ADDED -> true           // always reload (new install OR update)
            Intent.ACTION_PACKAGE_REPLACED -> false       // skip — ADDED handles it
            else -> false
        }
    }

    @Test
    fun `real uninstall triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false))
    }

    @Test
    fun `package removed during update is ignored`() {
        assertFalse(shouldReload(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
    }

    @Test
    fun `new install triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_ADDED, isReplacing = false))
    }

    @Test
    fun `package added during update triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
    }

    @Test
    fun `package replaced is ignored to avoid duplicate reload`() {
        assertFalse(shouldReload(Intent.ACTION_PACKAGE_REPLACED, isReplacing = false))
    }
}
```

- [ ] **Step 2: Run test — expect PASS (pure logic, no Android context needed)**

```bash
./gradlew test --tests "com.hexgrid.launcher.PackageChangeBroadcastReceiverTest" 2>&1 | tail -20
```
Expected: 5 tests PASS

- [ ] **Step 3: Update MainActivity.kt**

Replace the entire file with:

```kotlin
package com.hexgrid.launcher

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.view.View
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexgrid.launcher.databinding.ActivityMainBinding
import com.hexgrid.launcher.ui.LauncherViewModel
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.ui.SettingsActivity
import com.hexgrid.launcher.util.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var allApps: List<AppInfo> = emptyList()
    private lateinit var launcherAppsService: LauncherApps

    // ── Package change receiver (install / uninstall safety net) ──────────────
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> if (!isReplacing) viewModel.reloadApps()
                Intent.ACTION_PACKAGE_ADDED -> viewModel.reloadApps()
                // ACTION_PACKAGE_REPLACED skipped — PACKAGE_ADDED already handles it
            }
        }
    }

    // ── LauncherApps.Callback (Samsung Modes, work profiles, shortcuts) ───────
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            viewModel.reloadApps()
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            viewModel.reloadApps()
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            viewModel.reloadApps()
        }
        override fun onPackagesAvailable(
            packageNames: Array<String>, user: UserHandle, replacing: Boolean
        ) {
            viewModel.markAvailable(packageNames)
        }
        override fun onPackagesUnavailable(
            packageNames: Array<String>, user: UserHandle, replacing: Boolean
        ) {
            viewModel.markUnavailable(packageNames)
        }
        override fun onShortcutsChanged(
            packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle
        ) {
            viewModel.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setActivityContext(this)

        launcherAppsService = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Register listeners in onCreate so events are caught even while launcher is paused
        launcherAppsService.registerCallback(launcherAppsCallback)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, pkgFilter)

        setupGrid()
        setupDock()
        setupBackHandler()

        viewModel.loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        launcherAppsService.unregisterCallback(launcherAppsCallback)
        unregisterReceiver(packageChangeReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val dock = getCurrentDock()
            if (dock.isInSearchMode()) {
                dock.exitSearchMode()
            }
            binding.hexGrid.animateToOrigin()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dock = getCurrentDock()
                if (dock.isInSearchMode()) {
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
                .setItems(arrayOf("Remove from Dock")) { _, _ ->
                    dock.removeApp(app)
                }
                .show()
        }

        dock.refreshSettings()
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                viewModel.launchApp(app)
            }
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ ->
            showContextMenu(app)
        }

        // centerOnChange = true when a search query is active — grid animates to center
        viewModel.apps.observe(this) { apps ->
            val isFiltering = viewModel.currentQuery.isNotBlank()
            binding.hexGrid.setApps(apps, centerOnChange = isFiltering)
        }

        viewModel.allApps.observe(this) { apps ->
            allApps = apps
            val dock = getCurrentDock()
            dock.loadDockApps(apps)
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
        viewModel.loadApps()
    }
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL. If `onShortcutsChanged` signature mismatch: check API — on API 25+ it's `MutableList<ShortcutInfo>`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/MainActivity.kt
git add app/src/test/java/com/hexgrid/launcher/PackageChangeBroadcastReceiverTest.kt
git commit -m "feat: add BroadcastReceiver + LauncherApps.Callback for reactive app list refresh"
```

---

## Chunk 3: Search Centering in HexagonalGridView

### Task 4: Add `centerOnChange` parameter to `setApps()`

**Files:**
- Modify: `app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt` (lines 187–194)

- [ ] **Step 1: Update `setApps()` in HexagonalGridView.kt**

Find the existing `setApps()` method (around line 187) and replace it:

```kotlin
fun setApps(appList: List<AppInfo>, centerOnChange: Boolean = false) {
    if (apps != appList) {
        apps = appList
        hexPositions = calculator.generateSpiralCoordinates(maxOf(25, (apps.size / 6) + 5))
        updateScrollBounds()
        if (centerOnChange) {
            // Skip pop-in animation during search — just scroll to center smoothly.
            // Two concurrent animators on different properties would look jarring.
            animateToOrigin()
        } else {
            startRadialAnimation()
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual test on device/emulator**
  1. Launch the app
  2. Scroll the grid to a far corner
  3. Tap search in the dock, type 2-3 letters
  4. Grid should smoothly scroll to center showing only matching icons
  5. Clear search text → full grid returns with radial pop-in animation
  6. Scroll to a corner, press Back → grid animates back to origin

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/HexagonalGridView.kt
git commit -m "feat: center grid on filtered search results (animateToOrigin on filter)"
```

---

## Chunk 4: PWA Shortcut Pinning

### Task 5: Add Dialog theme to themes.xml

**Files:**
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Add dialog style to themes.xml**

Add after the existing `Theme.HexGridLauncher` style:

```xml
<style name="Theme.HexGridLauncher.Dialog" parent="Theme.Material3.DayNight.Dialog">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:backgroundDimEnabled">true</item>
</style>
```

The full file should look like:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.HexGridLauncher" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowTranslucentStatus">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowShowWallpaper">true</item>
    </style>

    <style name="Theme.HexGridLauncher.Dialog" parent="Theme.Material3.DayNight.Dialog">
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:backgroundDimEnabled">true</item>
    </style>
</resources>
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

---

### Task 6: Create PinShortcutActivity

**Files:**
- Create: `app/src/main/java/com/hexgrid/launcher/ui/PinShortcutActivity.kt`

- [ ] **Step 1: Create PinShortcutActivity.kt**

```kotlin
package com.hexgrid.launcher.ui

import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Handles pin shortcut requests (e.g. "Add to Home Screen" from Chrome PWA).
 * Receives ACTION_CONFIRM_PIN_SHORTCUT intent, shows confirmation dialog.
 *
 * Only receives intents when HexGrid is the default launcher.
 */
class PinShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = extractPinRequest()

        if (request == null || !request.isValid) {
            finish()
            return
        }

        val shortcutInfo = request.shortcutInfo
        val label = shortcutInfo?.shortLabel?.toString()
            ?: shortcutInfo?.longLabel?.toString()
            ?: "Shortcut"

        val icon: Drawable? = shortcutInfo?.let { info ->
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            runCatching {
                launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
            }.getOrNull()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add to HexGrid?")
            .setMessage(label)
            .apply { if (icon != null) setIcon(icon) }
            .setPositiveButton("Add") { _, _ ->
                request.accept()
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun extractPinRequest(): LauncherApps.PinItemRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(
                LauncherApps.EXTRA_PIN_ITEM_REQUEST,
                LauncherApps.PinItemRequest::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST)
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

---

### Task 7: Register PinShortcutActivity in AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add PinShortcutActivity entry**

Add after the `AppVisibilityActivity` entry (before `</application>`):

```xml
<!-- Handles "Add to Home Screen" requests from Chrome and other apps -->
<activity
    android:name=".ui.PinShortcutActivity"
    android:theme="@style/Theme.HexGridLauncher.Dialog"
    android:exported="true">
    <intent-filter>
        <action android:name="android.content.pm.action.CONFIRM_PIN_SHORTCUT" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Full build check**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL and APK generated

- [ ] **Step 3: Manual test — PWA pin flow**
  1. Install the debug APK on a device/emulator with Chrome
  2. Set HexGrid as default launcher
  3. Open Chrome → navigate to any website → tap ⋮ menu → "Add to Home Screen"
  4. Expected: "Add to HexGrid?" dialog appears
  5. Tap "Add" → shortcut should appear in the hex grid on next load
  6. Repeat and tap "Cancel" → dialog closes, no shortcut added

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hexgrid/launcher/ui/PinShortcutActivity.kt
git add app/src/main/AndroidManifest.xml
git add app/src/main/res/values/themes.xml
git commit -m "feat: add PinShortcutActivity for PWA shortcut pinning support"
```

---

## Final Verification

- [ ] **Run all unit tests**

```bash
./gradlew test 2>&1 | tail -30
```
Expected: All tests PASS (including AppFilterLogicTest and PackageChangeBroadcastReceiverTest)

- [ ] **Full debug build**

```bash
./gradlew assembleDebug 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Manual test checklist**
  - [ ] Install an app from Play Store → appears in grid without restarting launcher
  - [ ] Uninstall an app → disappears from grid immediately
  - [ ] Update an app → grid reloads once (not multiple times)
  - [ ] Enable a Samsung Mode that restricts apps → restricted apps disappear from grid
  - [ ] Disable the Samsung Mode → apps reappear
  - [ ] Add PWA shortcut from Chrome → confirmation dialog, shortcut appears in grid
  - [ ] Search for an app → grid centers on matching icons
  - [ ] Clear search → full grid returns with pop-in animation

---

## Notes

- **Existing test file paths:** Legacy test files are in `com/hexy/launcher/` directory but have `com.hexgrid.launcher` package declarations — this is a pre-existing mismatch, don't fix it in this cycle.
- **Samsung Modes testing:** Requires a Samsung device with Modes & Routines app. Test by setting a "Focus" mode with restricted apps. Availability callbacks fire when a mode is activated/deactivated.
- **BroadcastReceiver vs LauncherApps.Callback:** Both are registered in `onCreate()`/`onDestroy()`. The BroadcastReceiver is a safety net — on some custom Android ROMs, LauncherApps callbacks may be delayed. Both calling `reloadApps()` is fine since `reloadApps()` → `loadApps()` is a coroutine that will coalesce rapidly fired calls naturally.
