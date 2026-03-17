package com.hexgrid.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
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
            val pickedId = result.data
                ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (pickedId != -1) onWidgetPicked(pickedId)
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
        setupRecycler()
    }

    private fun startWidgetPicker() {
        pendingAppWidgetId = widgetHost.allocateId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingAppWidgetId)
        }
        widgetPickerLauncher.launch(intent)
    }

    private fun onWidgetPicked(appWidgetId: Int) {
        pendingAppWidgetId = appWidgetId
        val provider = appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider
            ?: run { releasePending(); return }

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        if (bound) {
            launchPlacementInMain(appWidgetId)
        } else {
            // Need explicit user permission
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            }
            bindPermissionLauncher.launch(intent)
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
            // Remove: tell WidgetManager via broadcast or direct call
            // Since WidgetManager lives in MainActivity, we use a simple approach:
            // remove from store here and notify MainActivity via a custom broadcast.
            widgetHost.releaseId(entry.appWidgetId)
            widgetStore.remove(entry.widgetId)
            // Notify MainActivity to detach the view
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
