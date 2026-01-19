package com.hexy.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.hexy.launcher.databinding.ActivityMainBinding
import com.hexy.launcher.ui.LauncherViewModel
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.setActivityContext(this)
        setupGrid()
        setupFab()
        
        // Load apps immediately - no permission needed with click-based tracking
        viewModel.loadApps()
    }
    
    private fun setupFab() {
        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupGrid() {
        binding.hexGrid.setOnAppClick { app ->
            viewModel.launchApp(app)
        }

        binding.hexGrid.setOnAppLongClick { app, _, _ ->
            showContextMenu(app)
        }

        viewModel.apps.observe(this) { apps ->
            binding.hexGrid.setApps(apps)
        }
    }

    private fun showContextMenu(app: AppInfo) {
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

    override fun onResume() {
        super.onResume()
        binding.hexGrid.refreshSettings()
        binding.hexGrid.scrollToOrigin() // Reset view to center most frequent app
        viewModel.loadApps()
    }
}
