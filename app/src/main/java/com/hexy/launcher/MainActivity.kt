package com.hexy.launcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.databinding.ActivityMainBinding
import com.hexy.launcher.ui.LauncherViewModel
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.ui.SettingsActivity
import com.hexy.launcher.util.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setActivityContext(this)
        setupGrid()
        setupDock()
        setupBackHandler()
        
        viewModel.loadApps()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Exit search mode if active when user presses home
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
                    // Scroll back to center
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
        
        // Show dock based on position setting
        binding.dockTop.visibility = View.GONE
        binding.dockBottom.visibility = View.GONE
        
        val dock = when (position) {
            SettingsManager.SearchPosition.TOP -> binding.dockTop
            SettingsManager.SearchPosition.BOTTOM -> binding.dockBottom
            SettingsManager.SearchPosition.NONE -> binding.dockBottom // Default to bottom
        }
        dock.visibility = View.VISIBLE
        
        // Setup callbacks - inline search with live filtering
        dock.onSearchTextChanged = { query ->
            viewModel.filterApps(query)
        }
        dock.onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
        dock.onAppClick = { app -> 
            // If user taps on HexGrid Launcher itself, open settings
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
        
        // Refresh dock settings (transparency, etc.)
        dock.refreshSettings()
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            // If user taps on HexGrid Launcher itself, open settings
            if (app.packageName == packageName) {
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                viewModel.launchApp(app)
            }
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ ->
            showContextMenu(app)
        }

        // Observe filtered apps for display in grid
        viewModel.apps.observe(this) { apps ->
            binding.hexGrid.setApps(apps)
        }
        
        // Observe ALL apps (unfiltered) for dock operations
        viewModel.allApps.observe(this) { apps ->
            allApps = apps
            // Load dock apps using the full unfiltered list
            val dock = getCurrentDock()
            dock.loadDockApps(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Pin to Dock", "Hide App", "App Info", "Uninstall")) { _, which ->
                when (which) {
                    0 -> {
                        val dock = getCurrentDock()
                        dock.addApp(app)
                    }
                    1 -> viewModel.hideApp(app)
                    2 -> {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${app.packageName}")
                        }
                        startActivity(intent)
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = android.net.Uri.fromParts("package", app.packageName, null)
                        }
                        startActivity(intent)
                    }
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
