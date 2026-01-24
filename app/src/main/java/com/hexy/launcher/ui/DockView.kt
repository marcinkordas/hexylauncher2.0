package com.hexy.launcher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.hexy.launcher.R
import com.hexy.launcher.data.AppInfo
import com.hexy.launcher.util.SettingsManager

/**
 * Dock bar with pinned apps, search icon, and settings button.
 * Features:
 * - One UI 8 Style: Pill-shaped floating island
 * - Inline search with slide animation
 * - Scrollable app icons when overflow
 * - Configurable transparency
 */
class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dockApps = mutableListOf<AppInfo>()
    
    // View references
    private lateinit var searchIcon: ImageView
    private lateinit var settingsIcon: ImageView
    private lateinit var appsScrollView: HorizontalScrollView
    private lateinit var appsContainer: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var searchCloseIcon: ImageView
    
    // State
    private var isSearchMode = false
    
    // Callbacks
    var onSearchClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null
    var onSearchTextChanged: ((String) -> Unit)? = null
    
    // Store reference to all apps for drag-and-drop lookup
    private var allAppsRef: List<AppInfo> = emptyList()
    
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
        setBackgroundResource(R.drawable.dock_bg_oneui)
        elevation = 8f
        
        initViews()
        setupDragListener()
        applyTransparency()
        rebuildDock()
    }

    private fun initViews() {
        // 1. Search Icon (Left) - triggers search mode
        searchIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginEnd = dpToPx(8)
            }
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.parseColor("#555555"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener { enterSearchMode() }
        }
        
        // 2. Apps ScrollView (Center - scrollable when many icons)
        appsScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
            isFillViewport = true  // This makes the child fill the viewport, enabling centering
            // Add fading edges for scroll indication
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dpToPx(20))
        }
        
        appsContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,  // Fill parent to enable centering
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = HORIZONTAL
            gravity = Gravity.CENTER  // Center horizontally and vertically
        }
        appsScrollView.addView(appsContainer)
        
        // 3. Search EditText (hidden by default, shown in search mode)
        searchEditText = EditText(context).apply {
            layoutParams = LayoutParams(0, dpToPx(40), 1f).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
            hint = "Search apps..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.parseColor("#333333"))
            background = null
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isSingleLine = true
            visibility = View.GONE
            alpha = 0f
            
            // Live filtering as user types
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onSearchTextChanged?.invoke(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            
            // Handle keyboard "search" action
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    true
                } else false
            }
        }
        
        // 4. Search Close Icon (hidden by default, shown in search mode)
        searchCloseIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(36), dpToPx(36)).apply {
                marginEnd = dpToPx(4)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#555555"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            alpha = 0f
            setOnClickListener { exitSearchMode() }
        }

        // 5. Settings Icon (Right)
        settingsIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginStart = dpToPx(8)
            }
            setImageResource(android.R.drawable.ic_menu_more)
            setColorFilter(Color.parseColor("#555555"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener { onSettingsClick?.invoke() }
        }
        
        // Add views in order
        addView(searchIcon)
        addView(appsScrollView)
        addView(searchEditText)
        addView(searchCloseIcon)
        addView(settingsIcon)
    }
    
    private fun applyTransparency() {
        val transparency = SettingsManager.getDockTransparency(context)
        val alpha = (transparency * 2.55).toInt().coerceIn(0, 255)
        
        // Update background drawable alpha
        val bgDrawable = background
        if (bgDrawable is GradientDrawable) {
            bgDrawable.alpha = alpha
        } else {
            background?.alpha = alpha
        }
    }
    
    fun refreshSettings() {
        applyTransparency()
    }
    
    /**
     * Setup drag listener to allow dropping apps onto the dock to pin them
     */
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
    
    /**
     * Add an app to the dock by its package name (used for drag-and-drop)
     */
    fun addAppByPackage(packageName: String): Boolean {
        // Check if already in dock
        if (dockApps.any { it.packageName == packageName }) return false
        
        // Find the app from our reference list
        val app = allAppsRef.find { it.packageName == packageName } ?: return false
        
        dockApps.add(app)
        saveDockApps()
        rebuildDock()
        return true
    }
    
    /**
     * Enter search mode: slide apps away, show search field, focus keyboard
     */
    fun enterSearchMode() {
        if (isSearchMode) return
        isSearchMode = true
        
        val duration = 200L
        val interpolator = FastOutSlowInInterpolator()
        
        // Cancel any ongoing animations first
        searchEditText.animate().cancel()
        searchCloseIcon.animate().cancel()
        appsScrollView.animate().cancel()
        settingsIcon.animate().cancel()
        
        // Hide apps with slide-left + fade
        appsScrollView.animate()
            .translationX(-width * 0.3f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .setListener(null)
            .withEndAction {
                appsScrollView.visibility = View.GONE
            }
            .start()
        
        // Hide settings icon
        settingsIcon.animate()
            .alpha(0f)
            .setDuration(duration / 2)
            .setListener(null)
            .withEndAction {
                settingsIcon.visibility = View.GONE
            }
            .start()
        
        // Show search field with slide-in
        searchEditText.translationX = width * 0.3f
        searchEditText.alpha = 0f
        searchEditText.visibility = View.VISIBLE
        searchEditText.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .setListener(null)
            .start()
        
        // Show close button
        searchCloseIcon.alpha = 0f
        searchCloseIcon.visibility = View.VISIBLE
        searchCloseIcon.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
            .start()
        
        // Focus search field and show keyboard
        searchEditText.postDelayed({
            searchEditText.requestFocus()
            showKeyboard()
        }, duration)
    }
    
    /**
     * Exit search mode: hide search, restore apps
     */
    fun exitSearchMode() {
        if (!isSearchMode) return
        isSearchMode = false
        
        // Clear search and notify
        searchEditText.setText("")
        onSearchTextChanged?.invoke("")
        hideKeyboard()
        
        val duration = 200L
        val interpolator = FastOutSlowInInterpolator()
        
        // Cancel any ongoing animations first
        searchEditText.animate().cancel()
        searchCloseIcon.animate().cancel()
        appsScrollView.animate().cancel()
        settingsIcon.animate().cancel()
        
        // Hide search field
        searchEditText.animate()
            .translationX(width * 0.3f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .setListener(null) // Clear any previous listener
            .withEndAction {
                searchEditText.visibility = View.GONE
                searchEditText.translationX = 0f
            }
            .start()
        
        // Hide close button
        searchCloseIcon.animate()
            .alpha(0f)
            .setDuration(duration / 2)
            .setListener(null) // Clear any previous listener
            .withEndAction {
                searchCloseIcon.visibility = View.GONE
            }
            .start()
        
        // Show apps with slide-in - reset position first
        appsScrollView.translationX = -width * 0.3f
        appsScrollView.alpha = 0f
        appsScrollView.visibility = View.VISIBLE
        appsScrollView.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .setListener(null)
            .start()
        
        // Show settings icon
        settingsIcon.alpha = 0f
        settingsIcon.visibility = View.VISIBLE
        settingsIcon.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
            .start()
    }
    
    fun isInSearchMode(): Boolean = isSearchMode
    
    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }
    
    fun setDockApps(apps: List<AppInfo>) {
        dockApps.clear()
        dockApps.addAll(apps) // No limit - scrollable
        saveDockApps()
        rebuildDock()
    }
    
    fun addApp(app: AppInfo): Boolean {
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
        allAppsRef = allApps  // Store reference for drag-and-drop lookup
        val savedPackages = SettingsManager.getDockApps(context)
        dockApps.clear()
        savedPackages.forEach { pkg ->
            allApps.find { it.packageName == pkg }?.let { dockApps.add(it) }
        }
        rebuildDock()
    }
    
    private fun rebuildDock() {
        appsContainer.removeAllViews()
        
        dockApps.forEachIndexed { index, app ->
            appsContainer.addView(createAppSlot(app, index))
        }
    }
    
    private fun createAppSlot(app: AppInfo, index: Int): View {
        return ImageView(context).apply {
            val size = dpToPx(48)
            layoutParams = LayoutParams(size, size).apply {
                marginStart = dpToPx(6)
                marginEnd = dpToPx(6)
            }
            tag = index
            
            // Clone drawable
            try {
                val iconDrawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
                setImageDrawable(iconDrawable)
            } catch (e: Exception) {
                setImageDrawable(app.icon)
            }
            
            scaleType = ImageView.ScaleType.FIT_CENTER
            
            // Handle touch for long-press popup vs drag
            var startX = 0f
            var startY = 0f
            var isLongPress = false
            val longPressHandler = Handler(Looper.getMainLooper())
            val longPressRunnable = Runnable {
                isLongPress = true
                // Show context menu
                showDockAppMenu(this, app, index)
            }
            
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        isLongPress = false
                        // Schedule long press (500ms)
                        longPressHandler.postDelayed(longPressRunnable, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        
                        // If moved enough, cancel long press and start drag
                        if (distance > dpToPx(10) && !isLongPress) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            startDragReorder(v, app, index)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longPressHandler.removeCallbacks(longPressRunnable)
                        if (!isLongPress) {
                            // Short tap - launch app
                            val dx = event.rawX - startX
                            val dy = event.rawY - startY
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (distance < dpToPx(10)) {
                                onAppClick?.invoke(app)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    private fun showDockAppMenu(anchor: View, app: AppInfo, index: Int) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Remove from Dock")
        popup.menu.add(0, 2, 1, "App Info")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    // Remove from dock
                    dockApps.removeAt(index)
                    saveDockApps()
                    rebuildDock()
                    true
                }
                2 -> {
                    // App info
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    // Drag reorder state
    private var draggedIndex = -1
    private var draggedApp: AppInfo? = null
    private var isOutsideDock = false  // Track if dragged outside
    
    private fun startDragReorder(view: View, app: AppInfo, index: Int) {
        draggedIndex = index
        draggedApp = app
        isOutsideDock = false
        
        // Create drag shadow
        val shadow = View.DragShadowBuilder(view)
        
        // Start drag
        val clipData = android.content.ClipData.newPlainText("dock_reorder", app.packageName)
        view.startDragAndDrop(clipData, shadow, index, 0)
        
        // Fade out the original icon
        view.alpha = 0.3f
        
        // Setup drag listeners
        setupReorderDragListener()
    }
    
    private fun setupReorderDragListener() {
        // Listen on the dock itself
        setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    isOutsideDock = false
                    true
                }
                
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Returned to dock - don't unpin
                    isOutsideDock = false
                    // Restore visibility
                    for (i in 0 until appsContainer.childCount) {
                        if (i == draggedIndex) {
                            appsContainer.getChildAt(i).alpha = 0.3f
                            appsContainer.getChildAt(i).scaleX = 1f
                            appsContainer.getChildAt(i).scaleY = 1f
                        }
                    }
                    true
                }
                
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Left the dock - mark for unpin
                    isOutsideDock = true
                    // Visual feedback
                    for (i in 0 until appsContainer.childCount) {
                        if (i == draggedIndex) {
                            appsContainer.getChildAt(i).alpha = 0.1f
                            appsContainer.getChildAt(i).scaleX = 0.5f
                            appsContainer.getChildAt(i).scaleY = 0.5f
                        }
                    }
                    true
                }
                
                DragEvent.ACTION_DRAG_LOCATION -> true
                
                DragEvent.ACTION_DROP -> true
                
                DragEvent.ACTION_DRAG_ENDED -> {
                    // If icon was outside dock when released, remove it
                    if (isOutsideDock && draggedApp != null && draggedIndex >= 0 && draggedIndex < dockApps.size) {
                        dockApps.removeAt(draggedIndex)
                        saveDockApps()
                    }
                    
                    // Reset state
                    isOutsideDock = false
                    draggedIndex = -1
                    draggedApp = null
                    setOnDragListener(null)
                    appsContainer.setOnDragListener(null)
                    rebuildDock()
                    true
                }
                
                else -> false
            }
        }
        
        // Also listen on apps container for reordering within dock
        appsContainer.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                
                DragEvent.ACTION_DRAG_LOCATION -> {
                    // Find which position the drag is over
                    val dropIndex = findDropIndex(event.x)
                    if (dropIndex != draggedIndex && dropIndex >= 0 && draggedIndex >= 0) {
                        // Move the item in the list
                        val app = draggedApp ?: return@setOnDragListener true
                        dockApps.removeAt(draggedIndex)
                        dockApps.add(dropIndex, app)
                        draggedIndex = dropIndex
                        
                        // Rebuild
                        rebuildDockAnimated()
                    }
                    true
                }
                
                DragEvent.ACTION_DROP -> {
                    saveDockApps()
                    true
                }
                
                else -> true
            }
        }
    }
    
    private fun findDropIndex(x: Float): Int {
        var accumulatedWidth = 0f
        for (i in 0 until appsContainer.childCount) {
            val child = appsContainer.getChildAt(i)
            val childWidth = child.width + dpToPx(12)
            if (x < accumulatedWidth + childWidth / 2) {
                return i
            }
            accumulatedWidth += childWidth
        }
        return dockApps.size - 1
    }
    
    private fun rebuildDockAnimated() {
        appsContainer.removeAllViews()
        dockApps.forEachIndexed { index, app ->
            val view = createAppSlot(app, index)
            if (index == draggedIndex) {
                view.alpha = 0.3f
            }
            appsContainer.addView(view)
        }
    }
    
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}

