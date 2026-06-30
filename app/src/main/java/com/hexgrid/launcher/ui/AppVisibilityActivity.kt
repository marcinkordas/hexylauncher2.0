package com.hexgrid.launcher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.data.AppRepository
import com.hexgrid.launcher.databinding.ActivityAppVisibilityBinding
import com.hexgrid.launcher.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * App Visibility — list with icon + label + Visible toggle.
 *
 * Semantics: switch ON = app is VISIBLE on the launcher; switch OFF = HIDDEN.
 * (Inversion of the previous "checked = hidden" UX, which several users reported as
 * counter-intuitive.)
 *
 * Hidden rows render their icon at alpha=0.35 so the visibility state reads at a glance.
 */
class AppVisibilityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppVisibilityBinding
    private val repository by lazy { AppRepository(this) }
    private var allApps = listOf<AppInfo>()
    private var displayedApps = listOf<AppInfo>()
    private var sortMode = SettingsManager.SortOrder.NAME
    private val hiddenApps = mutableSetOf<String>()
    private lateinit var adapter: AppRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppVisibilityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "App visibility"

        hiddenApps.addAll(SettingsManager.getHiddenApps(this))

        adapter = AppRowAdapter()
        binding.listApps.adapter = adapter

        setupSortSpinner()
        loadApps()
    }

    private fun setupSortSpinner() {
        val sortOptions = arrayOf("Name", "Usage Frequency", "Usage Time", "Notification Count")
        binding.spinnerSort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSort.setSelection(0)
        binding.spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
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
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class AppRowAdapter : BaseAdapter() {
        override fun getCount(): Int = displayedApps.size
        override fun getItem(position: Int): AppInfo = displayedApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = displayedApps[position]
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(com.hexgrid.launcher.R.layout.item_app_visibility, parent, false)

            val icon = view.findViewById<ImageView>(com.hexgrid.launcher.R.id.iconApp)
            val label = view.findViewById<TextView>(com.hexgrid.launcher.R.id.textLabel)
            val meta = view.findViewById<TextView>(com.hexgrid.launcher.R.id.textMeta)
            val switch = view.findViewById<SwitchMaterial>(com.hexgrid.launcher.R.id.switchVisible)

            icon.setImageDrawable(app.icon)
            label.text = app.label
            meta.text = "${app.packageName} · ${app.usageCount}×"

            // Detach previous listener before setting state, otherwise recycled rows fire stale
            // toggles when the adapter rebinds.
            switch.setOnCheckedChangeListener(null)
            val isVisible = app.packageName !in hiddenApps
            switch.isChecked = isVisible
            applyVisibilityVisuals(isVisible, icon, label)

            switch.setOnCheckedChangeListener { _, checked ->
                if (checked) hiddenApps.remove(app.packageName)
                else         hiddenApps.add(app.packageName)
                SettingsManager.setHiddenApps(this@AppVisibilityActivity, hiddenApps)
                applyVisibilityVisuals(checked, icon, label)
            }

            return view
        }

        private fun applyVisibilityVisuals(isVisible: Boolean, icon: ImageView, label: TextView) {
            if (isVisible) {
                icon.alpha = 1f
                label.alpha = 1f
            } else {
                icon.alpha = 0.35f
                label.alpha = 0.55f
            }
        }
    }
}
