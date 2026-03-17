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
        // reloadApps() if icon shape was changed in settings (dirty flag cleared by AppRepository)
        if (SettingsManager.getIconCacheDirty(this)) {
            viewModel.reloadApps()
        } else {
            viewModel.loadApps()
        }
    }
}
