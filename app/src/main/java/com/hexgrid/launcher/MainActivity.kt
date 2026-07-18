package com.hexgrid.launcher

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.SearchManager
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
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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

    // Voice search: RecognizerIntent result → fills the dock search field.
    private val voiceSearchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    val dock = getCurrentDock()
                    if (!dock.isInSearchMode()) dock.enterSearchMode()
                    dock.setSearchText(spoken)
                }
            }
        }

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
            "hex_radius", "icon_size_multiplier", "icon_padding",
            "outline_width", "corner_radius", "show_outline",
            "show_labels", "show_notification_glow",
            "unified_bucket_colors", "tile_transparency" -> {
                binding.hexGrid.invalidate()
            }
            "dock_transparency" -> {
                getCurrentDock().invalidate()
            }
            "dark_theme" -> {
                if (editModeOverlay == null) recreate()
                else pendingDarkThemeRecreate = true
            }
            "hex_orientation"     -> recomputeGridAndInvalidate()
            "sort_order"          -> reloadAppsAndInvalidate()
            "search_position"     -> repositionSearchBar()
            "search_with_mic"     -> updateSearchMicButton()
            "shortcut_icon_shape" -> {
                com.hexgrid.launcher.util.SettingsManager.setIconCacheDirty(this, true)
                binding.hexGrid.invalidate()
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
        override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) { viewModel.reloadApps() }
        override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) { viewModel.reloadApps() }
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
            updateGoogleChip(query)
        }
        dock.onAssistantSwipe = { launchAssistant() }
        dock.onMicClick = { launchVoiceSearch() }
        dock.onSearchModeChanged = { active ->
            setSearchScrim(active)
            if (!active) updateGoogleChip("")
        }
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
        positionChrome(position)
    }

    /**
     * Places the corner Settings button opposite the search bar (top-left when
     * the bar is at the bottom, bottom-left when it's at the top) and anchors
     * the Google chip just above the active bar.
     */
    private fun positionChrome(position: SettingsManager.SearchPosition) {
        val d = resources.displayMetrics.density
        val corner = binding.settingsCorner
        val cornerLp = corner.layoutParams as FrameLayout.LayoutParams
        val chip = binding.googleChip
        val chipLp = chip.layoutParams as FrameLayout.LayoutParams

        if (position == SettingsManager.SearchPosition.TOP) {
            cornerLp.gravity = Gravity.BOTTOM or Gravity.START
            cornerLp.topMargin = 0; cornerLp.bottomMargin = (44 * d).toInt()
            chipLp.gravity = Gravity.TOP; chipLp.topMargin = (116 * d).toInt(); chipLp.bottomMargin = 0
        } else { // BOTTOM or NONE
            cornerLp.gravity = Gravity.TOP or Gravity.START
            cornerLp.topMargin = (44 * d).toInt(); cornerLp.bottomMargin = 0
            chipLp.gravity = Gravity.BOTTOM; chipLp.bottomMargin = (96 * d).toInt(); chipLp.topMargin = 0
        }
        corner.layoutParams = cornerLp
        chip.layoutParams = chipLp

        corner.setOnClickListener { startActivity(Intent(this, SettingsHubActivity::class.java)) }
        chip.setOnClickListener { searchGoogle(getCurrentDock().getSearchText()) }
    }

    /** Fades the search scrim over the grid in/out (Gmail / One UI focus). */
    private fun setSearchScrim(visible: Boolean) {
        val scrim = binding.searchScrim
        scrim.animate().cancel()
        if (visible) {
            scrim.visibility = View.VISIBLE
            scrim.animate().alpha(1f).setDuration(200).start()
        } else {
            scrim.animate().alpha(0f).setDuration(200)
                .withEndAction { scrim.visibility = View.GONE }.start()
        }
    }

    /** Shows/updates the "Search Google for …" chip for the current query. */
    private fun updateGoogleChip(query: String) {
        val chip = binding.googleChip
        val show = query.isNotBlank() && SettingsManager.getSearchGoogleFallback(this)
        if (show) {
            binding.googleChipText.text = "Search Google for \"$query\""
            if (chip.visibility != View.VISIBLE) {
                chip.visibility = View.VISIBLE
                chip.alpha = 0f
                chip.animate().alpha(1f).setDuration(200).start()
            }
        } else if (chip.visibility == View.VISIBLE) {
            chip.animate().cancel()
            chip.animate().alpha(0f).setDuration(150)
                .withEndAction { chip.visibility = View.GONE }.start()
        }
    }

    /** Launches Google Assistant, falling back through voice-command → assist. */
    private fun launchAssistant() {
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (intent in candidates) {
            try { startActivity(intent); return } catch (_: Exception) { }
        }
        Toast.makeText(this, "No assistant app available", Toast.LENGTH_SHORT).show()
    }

    /** Launches the system speech recognizer; the result fills the search field. */
    private fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Voice input unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens a Google web search for [query] (web-search intent → browser fallback). */
    private fun searchGoogle(query: String) {
        if (query.isBlank()) return
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(webSearch); return } catch (_: Exception) { }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        }
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
                widgetManager.loadedEntries().forEach { entry ->
                    binding.hexGridContainer
                        .findViewWithTag<View>("widget_${entry.widgetId}")
                        ?.alpha = alpha
                }
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
    private fun applyStatusBarDim() {
        val controller = androidx.core.view.WindowCompat
            .getInsetsController(window, binding.root)
        val dim = com.hexgrid.launcher.util.SettingsManager.getDimStatusBar(this)
        controller.isAppearanceLightStatusBars = !dim
    }

    /** Dims the wallpaper behind the grid per the wallpaper_opacity setting (0-100). */
    private fun applyWallpaperOpacity() {
        val opacity = SettingsManager.getWallpaperOpacity(this).coerceIn(0, 100)
        val scrimAlpha = ((100 - opacity) * 2.55f).toInt().coerceIn(0, 255)
        binding.hexGridContainer.setBackgroundColor(android.graphics.Color.argb(scrimAlpha, 0, 0, 0))
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
        overlay.elevation = 16f * resources.displayMetrics.density
        binding.hexGridContainer.addView(overlay, params)
        editModeOverlay = overlay

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
