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
import com.hexgrid.launcher.ui.SettingsHubActivity
import com.hexgrid.launcher.ui.WidgetManagementActivity
import com.hexgrid.launcher.util.SettingsManager
import com.hexgrid.launcher.widget.WidgetHost
import com.hexgrid.launcher.widget.WidgetManager
import com.hexgrid.launcher.widget.WidgetStore

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLACEMENT_WIDGET_ID = "extra_placement_widget_id"
        const val EXTRA_ENTER_EDIT_MODE = "extra_enter_edit_mode"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var allApps: List<AppInfo> = emptyList()
    private lateinit var launcherAppsService: LauncherApps

    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetStore: WidgetStore
    private lateinit var widgetManager: WidgetManager

    private var widgetFadeAnimator: ValueAnimator? = null

    // Edit Mode state — null when overlay is not attached.
    private var editModeOverlay: com.hexgrid.launcher.ui.EditModeOverlay? = null

    private var editModeBackCallback: androidx.activity.OnBackPressedCallback? = null

    // Dark-theme recreate is deferred while Edit Mode is active to avoid
    // destroying the overlay mid-session. Flushed in exitEditMode().
    private var pendingDarkThemeRecreate: Boolean = false

    // SharedPreferences change listener — drives live grid preview.
    // Registered in onCreate(), unregistered in onDestroy().
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            // Visual grid settings — the View caches these in fields, so we must call
            // refreshSettings() (loadSettings + invalidate) rather than just invalidate().
            "hex_radius", "icon_size_multiplier", "icon_padding",
            "outline_width", "corner_radius", "show_outline",
            "show_labels", "show_notification_glow",
            "unified_bucket_colors", "tile_transparency" -> {
                binding.hexGrid.refreshSettings()
            }
            "dock_transparency" -> {
                getCurrentDock().refreshSettings()
            }
            "dark_theme" -> {
                if (editModeOverlay == null) recreate()
                else pendingDarkThemeRecreate = true
            }
            "hex_orientation"     -> recomputeGridAndInvalidate()
            "sort_order"          -> reloadAppsAndInvalidate()
            "search_position"     -> {
                // setupDock() un-hides the dock; suppress while Edit Mode is active so the
                // dock doesn't re-appear over the toolbar pill. Effect lands on exitEditMode.
                if (editModeOverlay == null) repositionSearchBar()
            }
            "search_with_mic"     -> if (editModeOverlay == null) updateSearchMicButton()
            "shortcut_icon_shape" -> {
                com.hexgrid.launcher.util.SettingsManager.setIconCacheDirty(this, true)
                binding.hexGrid.refreshSettings()
            }
            "dim_status_bar"      -> applyStatusBarDim()
            "wallpaper_opacity"   -> applyWallpaperOpacity()
        }
    }

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

    // ── Package suspension receiver ───────────────────────────────────────────
    // Fires for Samsung Modes "Work mode", Samsung Forest (Digital Wellbeing) app timers,
    // enterprise policy suspensions, and parental controls. Required because Samsung
    // does not reliably invoke LauncherApps.Callback.onPackagesSuspended for these flows.
    private val packageSuspendReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_PACKAGES_SUSPENDED,
                Intent.ACTION_PACKAGES_UNSUSPENDED -> viewModel.reloadApps()
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
        // Suspension fast-path (API 28+). The broadcast receiver covers Samsung devices where
        // these callbacks may not fire for Modes/Forest suspensions — both routes converge on reload.
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle) {
            viewModel.reloadApps()
        }
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle) {
            viewModel.reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(prefsListener)

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

        // Suspension broadcasts (no data scheme — system-wide, sent on suspend/unsuspend).
        val suspendFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGES_SUSPENDED)
            addAction(Intent.ACTION_PACKAGES_UNSUSPENDED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageSuspendReceiver, suspendFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageSuspendReceiver, suspendFilter)
        }

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
            containerHeight = { binding.hexGridContainer.height },
            onLayoutChanged = { updateOccupiedCells() }
        )

        setupGrid()
        setupDock()
        setupBackHandler()
        setupWidgetScrollSync()

        // startListening MUST come before restoreWidgets so that
        // AppWidgetHostViews receive their initial RemoteViews update.
        widgetManager.startListening()
        widgetManager.restoreWidgets()
        updateOccupiedCells()

        viewModel.loadApps()

        applyWallpaperOpacity()

        // Handle placement intent if launched for widget placement
        handlePlacementIntent(intent)

        if (intent?.getBooleanExtra(EXTRA_ENTER_EDIT_MODE, false) == true) {
            intent.removeExtra(EXTRA_ENTER_EDIT_MODE)
            binding.root.post { enterEditMode() }
        }
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
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
        launcherAppsService.unregisterCallback(launcherAppsCallback)
        unregisterReceiver(packageChangeReceiver)
        unregisterReceiver(packageSuspendReceiver)
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
        intent?.let {
            if (it.getBooleanExtra(EXTRA_ENTER_EDIT_MODE, false) == true) {
                it.removeExtra(EXTRA_ENTER_EDIT_MODE)
                binding.root.post { enterEditMode() }
            }
        }
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
        dock.onSettingsClick = { startActivity(Intent(this, SettingsHubActivity::class.java)) }
        dock.onAppClick = { app ->
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsHubActivity::class.java))
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

        val views = widgetManager.loadedViews()
        if (views.isEmpty()) return  // no widgets to animate

        val targetAlpha = if (visible) 1f else 0f
        val startAlpha = views.first().alpha
        if (startAlpha == targetAlpha) return  // already at target — skip degenerate animation

        widgetFadeAnimator?.cancel()
        widgetFadeAnimator = ValueAnimator.ofFloat(startAlpha, targetAlpha).apply {
            duration = 200
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                views.forEach { it.alpha = alpha }
            }
            start()
        }
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsHubActivity::class.java))
            } else {
                viewModel.launchApp(app)
            }
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ -> showContextMenu(app) }

        binding.hexGrid.onEmptyAreaLongPress = { _ -> enterEditMode() }

        viewModel.apps.observe(this) { apps ->
            val isFiltering = viewModel.currentQuery.isNotBlank()
            binding.hexGrid.setApps(apps, centerOnChange = isFiltering)
        }

        viewModel.allApps.observe(this) { apps ->
            allApps = apps
            getCurrentDock().loadDockApps(apps)
        }
    }

    /** Reloads hex grid calculator settings (radius, orientation) and invalidates. */
    private fun recomputeGridAndInvalidate() {
        binding.hexGrid.refreshSettings()
    }

    /** Invalidates the app list then redraws — used when sort order changes. */
    private fun reloadAppsAndInvalidate() {
        viewModel.reloadApps()
    }

    /** Shows/hides/repositions the search dock based on current search_position setting. */
    private fun repositionSearchBar() {
        setupDock()
    }

    /** Toggles the mic button in the dock based on search_with_mic setting. */
    private fun updateSearchMicButton() {
        getCurrentDock().refreshSettings()
    }

    /**
     * Applies dim_status_bar setting via WindowInsetsController.
     * In Edit Mode the effect is hidden behind the overlay — the Style panel
     * label "(applied on exit)" is a UX hint.
     */
    /**
     * Wallpaper opacity scrim. The activity theme has windowBackground=transparent so the
     * device wallpaper shows behind the launcher. Painting an alpha-tinted black background
     * on hexGridContainer (which sits in front of the wallpaper but behind hexGrid drawing)
     * dims the wallpaper without modifying it. opacity=100 → fully transparent (full
     * wallpaper); opacity=0 → fully black (wallpaper hidden).
     */
    private fun applyWallpaperOpacity() {
        val opacity = SettingsManager.getWallpaperOpacity(this).coerceIn(0, 100)
        val scrimAlpha = ((100 - opacity) * 2.55f).toInt().coerceIn(0, 255)
        binding.hexGridContainer.setBackgroundColor(android.graphics.Color.argb(scrimAlpha, 0, 0, 0))
    }

    private fun applyStatusBarDim() {
        val controller = androidx.core.view.WindowCompat
            .getInsetsController(window, binding.root)
        val dim = com.hexgrid.launcher.util.SettingsManager.getDimStatusBar(this)
        controller.isAppearanceLightStatusBars = !dim
    }

    fun enterEditMode() {
        if (editModeOverlay != null) return

        val overlay = com.hexgrid.launcher.ui.EditModeOverlay(this)
        overlay.onDone = { exitEditMode() }
        overlay.onMore = {
            exitEditMode()
            startActivity(Intent(this, com.hexgrid.launcher.ui.SettingsHubActivity::class.java))
        }

        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        binding.hexGridContainer.addView(overlay, params)
        editModeOverlay = overlay

        // Keep the dock visible during Edit Mode so users can see where the search bar will land
        // when they change its position. DockView sets elevation=8px (≈3dp); raise the overlay's
        // elevation well above that so the toolbar pill paints on top of the dock when they
        // overlap (search position = BOTTOM). Programmatic px guarantees the relationship is
        // independent of theme attribute density math.
        overlay.elevation = 16f * resources.displayMetrics.density

        androidx.core.view.ViewCompat.requestApplyInsets(overlay)
        overlay.show(com.hexgrid.launcher.ui.EditModeOverlay.Mode.SHAPE)

        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitEditMode()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        editModeBackCallback = callback
    }

    fun exitEditMode() {
        val overlay = editModeOverlay ?: return
        // Mark the field null synchronously so re-entrant enterEditMode() during the
        // hide animation creates a fresh overlay instead of double-attaching.
        editModeOverlay = null
        overlay.hide {
            binding.hexGridContainer.removeView(overlay)
            // Remove back-press callback only after hide() finishes — during the 200ms
            // animation the overlay is still visible and back-press should still exit.
            editModeBackCallback?.remove()
            editModeBackCallback = null
            if (pendingDarkThemeRecreate) {
                pendingDarkThemeRecreate = false
                recreate()
            }
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
