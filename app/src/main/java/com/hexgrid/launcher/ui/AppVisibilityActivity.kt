package com.hexgrid.launcher.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.data.AppRepository
import com.hexgrid.launcher.databinding.ActivityAppVisibilityBinding
import com.hexgrid.launcher.util.SettingsManager
import kotlinx.coroutines.launch

class AppVisibilityActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAppVisibilityBinding
    private val repository by lazy { AppRepository(this) }
    private var allApps = listOf<AppInfo>()
    private var displayedApps = listOf<AppInfo>()
    private var sortMode = SettingsManager.SortOrder.NAME
    private val hiddenApps = mutableSetOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppVisibilityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage App Visibility"
        
        hiddenApps.addAll(SettingsManager.getHiddenApps(this))
        
        setupSortSpinner()
        
        binding.listApps.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        binding.listApps.setOnItemClickListener { _, _, position, _ ->
            val app = displayedApps[position]
            if (binding.listApps.isItemChecked(position)) {
                // If checked in UI (which means "selected"), let's treat selection as "Visible" or "Hidden"?
                // Standard convention: Check = selected/active.
                // Let's say Check = VISIBLE (default). Uncheck = HIDDEN.
                // Or Check = HIDDEN?
                // Text says "Manage App Visibility".
                // Let's use Check = Hidden for explicit action?
                // No, usually list of apps -> Check to Hide is common.
                // Let's assume selection = Hidden.
                hiddenApps.add(app.packageName)
            } else {
                hiddenApps.remove(app.packageName)
            }
            SettingsManager.setHiddenApps(this, hiddenApps)
        }
        
        loadApps()
    }
    
    private fun setupSortSpinner() {
        val sortOptions = arrayOf("Name", "Usage Frequency", "Usage Time", "Notification Count")
        binding.spinnerSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        binding.spinnerSort.setSelection(0)
        binding.spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                sortMode = SettingsManager.SortOrder.values()[position]
                updateList()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun loadApps() {
        lifecycleScope.launch {
            allApps = repository.loadInstalledApps()
            updateList()
        }
    }
    
    private fun updateList() {
        displayedApps = when (sortMode) {
            SettingsManager.SortOrder.NAME -> allApps.sortedBy { it.label.lowercase() }
            SettingsManager.SortOrder.USAGE_FREQUENCY -> allApps.sortedByDescending { it.usageCount }
            SettingsManager.SortOrder.USAGE_TIME -> allApps.sortedByDescending { it.lastUsedTimestamp }
            SettingsManager.SortOrder.NOTIFICATION_COUNT -> allApps.sortedByDescending { it.notificationCount }
        }
        
        val items = displayedApps.map { app ->
            "${app.label} (${app.usageCount})"
        }
        
        // Use multiple choice layout
        // NOTE: We interpret CHECKED as HIDDEN
        binding.listApps.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, items)
        
        // Restore checked state based on hiddenApps
        for (i in displayedApps.indices) {
            if (displayedApps[i].packageName in hiddenApps) {
                binding.listApps.setItemChecked(i, true)
            } else {
                binding.listApps.setItemChecked(i, false)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
