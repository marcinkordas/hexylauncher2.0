package com.hexy.launcher.ui

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.hexy.launcher.R
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.util.SettingsManager

/**
 * Dock bar with pinned apps, search icon, and settings button.
 * Supports drag-and-drop from hex grid.
 * One UI 8 Style: Rounded floating island, Search Left, Settings Right, Apps Distributed.
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dockApps = mutableListOf<AppInfo>()
    private val maxSlots = 5
    
    // View references
    private lateinit var searchIcon: ImageView
    private lateinit var settingsIcon: ImageView
    private lateinit var appsContainer: LinearLayout
    
    var onSearchClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Padding for the dock container itself
        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        setBackgroundResource(R.drawable.dock_bg_oneui)
        elevation = 8f
        
        // Enable layout animations for smooth add/remove
        layoutTransition = LayoutTransition()
        
        initViews()
        setupDragListener()
        rebuildDock()
    }

    private fun initViews() {
        // 1. Search Icon (Left)
        searchIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginEnd = dpToPx(8)
            }
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.parseColor("#555555"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            // Interaction visual (ripple effect)
            isClickable = true
            isFocusable = true
            setOnClickListener { onSearchClick?.invoke() }
        }
        
        // 2. Apps Container (Center - Weight 1 to take available space)
        appsContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }

        // 3. Settings Icon (Right)
        settingsIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginStart = dpToPx(8)
            }
            // Use "overflow" styled icon (3 dots)
            setImageResource(android.R.drawable.ic_menu_more) 
            
            setColorFilter(Color.parseColor("#555555"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            // Interaction visual (ripple effect)
            isClickable = true
            isFocusable = true
            setOnClickListener { onSettingsClick?.invoke() }
        }
        
        // Add persistent main views
        addView(searchIcon)
        addView(appsContainer)
        addView(settingsIcon)
    }
    
    private fun setupDragListener() {
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    animate().scaleX(1.02f).scaleY(1.02f).duration = 100
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    animate().scaleX(1f).scaleY(1f).duration = 100
                    true
                }
                DragEvent.ACTION_DROP -> {
                    animate().scaleX(1f).scaleY(1f).duration = 100
                    val clipData = event.clipData
                    if (clipData != null && clipData.itemCount > 0) {
                        val packageName = clipData.getItemAt(0).text?.toString()
                        if (packageName != null) {
                            addAppByPackage(packageName)
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    animate().scaleX(1f).scaleY(1f).duration = 100
                    true
                }
                else -> false
            }
        }
    }
    
    private fun addAppByPackage(packageName: String) {
        // Handled by MainActivity
    }
    
    fun setDockApps(apps: List<AppInfo>) {
        dockApps.clear()
        dockApps.addAll(apps.take(maxSlots))
        saveDockApps()
        rebuildDock()
    }
    
    fun addApp(app: AppInfo): Boolean {
        if (dockApps.size >= maxSlots) return false
        if (dockApps.any { it.packageName == app.packageName }) return false
        
        dockApps.add(app)
        saveDockApps()
        rebuildDock()
        return true
    }
    
    fun removeApp(app: AppInfo) {
        dockApps.removeAll { it.packageName == app.packageName }
        saveDockApps()
        rebuildDock()
    }
    
    private fun saveDockApps() {
        val packages = dockApps.map { it.packageName }.toSet()
        SettingsManager.setDockApps(context, packages)
    }
    
    fun loadDockApps(allApps: List<AppInfo>) {
        val savedPackages = SettingsManager.getDockApps(context)
        dockApps.clear()
        savedPackages.forEach { pkg ->
            allApps.find { it.packageName == pkg }?.let { dockApps.add(it) }
        }
        rebuildDock()
    }
    
    private fun rebuildDock() {
        appsContainer.removeAllViews()
        
        dockApps.forEach { app ->
            appsContainer.addView(createAppSlot(app))
        }
    }
    
    private fun createAppSlot(app: AppInfo): View {
        return ImageView(context).apply {
            val size = dpToPx(44)
            layoutParams = LayoutParams(size, size).apply {
                // Distribute evenly by adding margin
                marginStart = dpToPx(6)
                marginEnd = dpToPx(6)
            }
            
            // IMPORTANT: Fix shared drawable state flickering!
            // Mutate the drawable to ensure it has its own independent state (bounds)
            try {
                val iconDrawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
                setImageDrawable(iconDrawable)
            } catch (e: Exception) {
                // Fallback if constantState is null
                setImageDrawable(app.icon)
            }
            
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            setOnClickListener { onAppClick?.invoke(app) }
            setOnLongClickListener { 
                onAppLongClick?.invoke(app)
                true 
            }
        }
    }
    
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
