package com.hexy.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.data.UsageStatsHelper
import com.hexy.launcher.databinding.ActivityMainBinding
import com.hexy.launcher.ui.LauncherViewModel
import com.hexy.launcher.data.AppInfo

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        viewModel.setActivityContext(this)
        setupGrid()
        checkUsagePermission()
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
    
    private fun showContextMenu(app: AppInfo, x: Float, y: Float) {
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
        binding.hexGrid.refreshSettings()
        if (UsageStatsHelper.hasPermission(this)) {
            viewModel.loadApps()
        }
    }
}
