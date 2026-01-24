package com.hexy.launcher

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
        
        viewModel.loadApps()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            binding.hexGrid.animateToOrigin()
        }
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
        
        // Setup callbacks
        dock.onSearchClick = { showSearchDialog() }
        dock.onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) }
        dock.onAppClick = { app -> viewModel.launchApp(app) }
        dock.onAppLongClick = { app -> 
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(arrayOf("Remove from Dock")) { _, _ ->
                    dock.removeApp(app)
                }
                .show()
        }
    }
    
    private fun showSearchDialog() {
        val editText = EditText(this).apply {
            hint = "Search apps..."
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(editText)
            .setPositiveButton("Search") { dialog, _ ->
                val query = editText.text.toString()
                if (query.isNotBlank()) {
                    viewModel.filterApps(query)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                viewModel.filterApps("") // Reset filter
                dialog.dismiss()
            }
            .show()
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            viewModel.launchApp(app)
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ ->
            showContextMenu(app)
        }

        viewModel.apps.observe(this) { apps ->
            allApps = apps
            binding.hexGrid.setApps(apps)
            
            // Load dock apps once we have the full list
            val position = SettingsManager.getSearchPosition(this)
            val dock = if (position == SettingsManager.SearchPosition.TOP) binding.dockTop else binding.dockBottom
            dock.loadDockApps(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Pin to Dock", "Hide App", "App Info", "Uninstall")) { _, which ->
                when (which) {
                    0 -> {
                        val position = SettingsManager.getSearchPosition(this)
                        val dock = if (position == SettingsManager.SearchPosition.TOP) binding.dockTop else binding.dockBottom
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
