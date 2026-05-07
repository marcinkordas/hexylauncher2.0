package com.hexgrid.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.hexgrid.launcher.MainActivity
import com.hexgrid.launcher.R
import com.hexgrid.launcher.databinding.ActivityWidgetManagementBinding
import com.hexgrid.launcher.util.SettingsManager
import com.hexgrid.launcher.widget.WidgetEntry
import com.hexgrid.launcher.widget.WidgetHost
import com.hexgrid.launcher.widget.WidgetStore

class WidgetManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetManagementBinding
    private lateinit var widgetHost: WidgetHost
    private lateinit var widgetStore: WidgetStore
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingAppWidgetId: Int = -1

    // Step 1: launch system widget picker
    private val widgetPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val pickedId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (pickedId != -1) onWidgetPicked(pickedId, data)
            else releasePending()
        } else {
            releasePending()
        }
    }

    // Step 2 (if needed): request BIND_APPWIDGET permission
    private val bindPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingAppWidgetId != -1) {
            launchConfigureOrPlacement(pendingAppWidgetId)
        } else {
            releasePending()
        }
    }

    // Step 3 (if needed): widget configuration activity
    private val configureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingAppWidgetId != -1) {
            launchPlacementInMain(pendingAppWidgetId)
        } else {
            releasePending()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Widgets"

        widgetHost = WidgetHost(this)
        widgetStore = WidgetStore(this)

        binding.btnAddWidget.setOnClickListener { startWidgetPicker() }

        binding.switchShowWidgetsDuringSearch.isChecked =
            SettingsManager.getShowWidgetsDuringSearch(this)
        binding.switchShowWidgetsDuringSearch.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setShowWidgetsDuringSearch(this, isChecked)
        }

        setupRecycler()
    }

    private fun startWidgetPicker() {
        pendingAppWidgetId = widgetHost.allocateId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
        }
        widgetPickerLauncher.launch(intent)
    }

    private fun onWidgetPicked(appWidgetId: Int, resultData: Intent?) {
        pendingAppWidgetId = appWidgetId

        // The system picker may have already bound the widget (device/version dependent).
        // If so, skip straight to configure-or-placement.
        if (appWidgetManager.getAppWidgetInfo(appWidgetId) != null) {
            launchConfigureOrPlacement(appWidgetId)
            return
        }

        // Not yet bound — get provider from picker result and bind manually.
        val provider: ComponentName? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultData?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            resultData?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        }
        if (provider == null) { releasePending(); return }

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        if (bound) {
            launchConfigureOrPlacement(appWidgetId)
        } else {
            // Need explicit user permission
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            }
            bindPermissionLauncher.launch(intent)
        }
    }

    private fun launchConfigureOrPlacement(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            // Widget requires configuration before it can render.
            pendingAppWidgetId = appWidgetId
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            configureLauncher.launch(intent)
        } else {
            launchPlacementInMain(appWidgetId)
        }
    }

    private fun launchPlacementInMain(appWidgetId: Int) {
        pendingAppWidgetId = -1
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            putExtra(MainActivity.EXTRA_PLACEMENT_WIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun releasePending() {
        if (pendingAppWidgetId != -1) {
            widgetHost.releaseId(pendingAppWidgetId)
            pendingAppWidgetId = -1
        }
    }

    private fun setupRecycler() {
        binding.recyclerWidgets.layoutManager = LinearLayoutManager(this)
        refreshList()
    }

    private fun refreshList() {
        val entries = widgetStore.loadAll()
        binding.recyclerWidgets.adapter = WidgetAdapter(entries) { entry ->
            // Remove from store so the list refreshes immediately, then notify
            // MainActivity's WidgetManager (which owns the AppWidgetHost lifecycle
            // and is responsible for calling releaseId — do NOT call it here too).
            widgetStore.remove(entry.widgetId)
            sendBroadcast(Intent(ACTION_WIDGET_REMOVED).apply {
                putExtra(EXTRA_WIDGET_ID, entry.widgetId)
                setPackage(packageName)
            })
            refreshList()
            Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private inner class WidgetAdapter(
        private val entries: List<WidgetEntry>,
        private val onRemove: (WidgetEntry) -> Unit
    ) : RecyclerView.Adapter<WidgetAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.imgWidgetIcon)
            val name: TextView = view.findViewById(R.id.textWidgetName)
            val remove: MaterialButton = view.findViewById(R.id.btnRemoveWidget)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_widget, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            val info = appWidgetManager.getAppWidgetInfo(entry.appWidgetId)
            holder.name.text = info?.loadLabel(packageManager) ?: "Widget ${entry.widgetId}"
            info?.loadPreviewImage(this@WidgetManagementActivity, 0)?.let {
                holder.icon.setImageDrawable(it)
            } ?: info?.loadIcon(this@WidgetManagementActivity, 0)?.let {
                holder.icon.setImageDrawable(it)
            }
            holder.remove.setOnClickListener { onRemove(entry) }
        }

        override fun getItemCount() = entries.size
    }

    companion object {
        const val ACTION_WIDGET_REMOVED = "com.hexgrid.launcher.ACTION_WIDGET_REMOVED"
        const val EXTRA_WIDGET_ID = "widget_id"
    }
}
