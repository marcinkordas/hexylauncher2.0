package com.hexy.launcher.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object SettingsManager {
    
    private const val KEY_HEX_RADIUS = "hex_radius"
    private const val KEY_ICON_SIZE_MULTIPLIER = "icon_size_multiplier"
    private const val KEY_ICON_PADDING = "icon_padding"
    private const val KEY_SHOW_OUTLINE = "show_outline"
    private const val KEY_SHOW_LABELS = "show_labels"
    private const val KEY_SORT_ORDER = "sort_order"
    private const val KEY_SHOW_NOTIFICATION_GLOW = "show_notification_glow"
    private const val KEY_HEX_ORIENTATION = "hex_orientation"
    private const val KEY_OUTLINE_WIDTH = "outline_width"
    private const val KEY_SEARCH_POSITION = "search_position"
    private const val KEY_SEARCH_WITH_MIC = "search_with_mic"
    private const val KEY_CORNER_RADIUS = "corner_radius"
    private const val KEY_DIM_STATUS_BAR = "dim_status_bar"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_UNIFIED_BUCKET_COLORS = "unified_bucket_colors"
    private const val KEY_TILE_TRANSPARENCY = "tile_transparency"
    private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
    
    // Default values
    const val DEFAULT_HEX_RADIUS = 96f
    const val DEFAULT_ICON_SIZE_MULTIPLIER = 1.0f
    const val DEFAULT_ICON_PADDING = 12f
    const val DEFAULT_SHOW_OUTLINE = true
    const val DEFAULT_SHOW_LABELS = true
    const val DEFAULT_SHOW_NOTIFICATION_GLOW = true
    const val DEFAULT_OUTLINE_WIDTH = 1.5f
    const val DEFAULT_SEARCH_WITH_MIC = true
    const val DEFAULT_CORNER_RADIUS = 0f
    const val DEFAULT_DIM_STATUS_BAR = true
    const val DEFAULT_DARK_THEME = true
    const val DEFAULT_UNIFIED_BUCKET_COLORS = false
    const val DEFAULT_TILE_TRANSPARENCY = 50  // 0-100 (50% = current behavior)
    const val DEFAULT_DOCK_TRANSPARENCY = 90  // 0-100 (90% = mostly opaque)
    
    // Min/Max ranges
    const val MIN_HEX_RADIUS = 50f
    const val MAX_HEX_RADIUS = 150f
    const val MIN_ICON_SIZE_MULTIPLIER = 0.5f
    const val MAX_ICON_SIZE_MULTIPLIER = 1.5f
    const val MIN_ICON_PADDING = 0f
    const val MAX_ICON_PADDING = 20f
    const val MIN_OUTLINE_WIDTH = 0.5f
    const val MAX_OUTLINE_WIDTH = 4.0f
    const val MIN_CORNER_RADIUS = 0f
    const val MAX_CORNER_RADIUS = 20f
    
    enum class SortOrder {
        NAME,
        USAGE_FREQUENCY,
        USAGE_TIME,
        NOTIFICATION_COUNT
    }
    
    enum class HexOrientation {
        POINTY_TOP,
        FLAT_TOP
    }
    
    enum class SearchPosition {
        NONE,
        TOP,
        BOTTOM
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
    
    // Hex Radius
    fun getHexRadius(context: Context): Float = getPrefs(context).getFloat(KEY_HEX_RADIUS, DEFAULT_HEX_RADIUS)
    fun setHexRadius(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_HEX_RADIUS, value.coerceIn(MIN_HEX_RADIUS, MAX_HEX_RADIUS)).apply()
    }
    
    // Icon Size
    fun getIconSizeMultiplier(context: Context): Float = getPrefs(context).getFloat(KEY_ICON_SIZE_MULTIPLIER, DEFAULT_ICON_SIZE_MULTIPLIER)
    fun setIconSizeMultiplier(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ICON_SIZE_MULTIPLIER, value.coerceIn(MIN_ICON_SIZE_MULTIPLIER, MAX_ICON_SIZE_MULTIPLIER)).apply()
    }
    
    // Icon Padding
    fun getIconPadding(context: Context): Float = getPrefs(context).getFloat(KEY_ICON_PADDING, DEFAULT_ICON_PADDING)
    fun setIconPadding(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_ICON_PADDING, value.coerceIn(MIN_ICON_PADDING, MAX_ICON_PADDING)).apply()
    }
    
    // Show Outline
    fun getShowOutline(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHOW_OUTLINE, DEFAULT_SHOW_OUTLINE)
    fun setShowOutline(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_OUTLINE, value).apply()
    }
    
    // Show Labels
    fun getShowLabels(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHOW_LABELS, DEFAULT_SHOW_LABELS)
    fun setShowLabels(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_LABELS, value).apply()
    }
    
    // Sort Order
    fun getSortOrder(context: Context): SortOrder {
        val name = getPrefs(context).getString(KEY_SORT_ORDER, SortOrder.USAGE_FREQUENCY.name)
        return SortOrder.valueOf(name ?: SortOrder.USAGE_FREQUENCY.name)
    }
    fun setSortOrder(context: Context, value: SortOrder) {
        getPrefs(context).edit().putString(KEY_SORT_ORDER, value.name).apply()
    }
    
    // Show Notification Glow
    fun getShowNotificationGlow(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SHOW_NOTIFICATION_GLOW, DEFAULT_SHOW_NOTIFICATION_GLOW)
    fun setShowNotificationGlow(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_NOTIFICATION_GLOW, value).apply()
    }
    
    // Hex Orientation
    fun getHexOrientation(context: Context): HexOrientation {
        val name = getPrefs(context).getString(KEY_HEX_ORIENTATION, HexOrientation.POINTY_TOP.name)
        return HexOrientation.valueOf(name ?: HexOrientation.POINTY_TOP.name)
    }
    fun setHexOrientation(context: Context, value: HexOrientation) {
        getPrefs(context).edit().putString(KEY_HEX_ORIENTATION, value.name).apply()
    }
    
    // Outline Width
    fun getOutlineWidth(context: Context): Float = getPrefs(context).getFloat(KEY_OUTLINE_WIDTH, DEFAULT_OUTLINE_WIDTH)
    fun setOutlineWidth(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_OUTLINE_WIDTH, value.coerceIn(MIN_OUTLINE_WIDTH, MAX_OUTLINE_WIDTH)).apply()
    }
    
    // Search Position
    fun getSearchPosition(context: Context): SearchPosition {
        val name = getPrefs(context).getString(KEY_SEARCH_POSITION, SearchPosition.NONE.name)
        return SearchPosition.valueOf(name ?: SearchPosition.NONE.name)
    }
    fun setSearchPosition(context: Context, value: SearchPosition) {
        getPrefs(context).edit().putString(KEY_SEARCH_POSITION, value.name).apply()
    }
    
    // Search With Mic
    fun getSearchWithMic(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SEARCH_WITH_MIC, DEFAULT_SEARCH_WITH_MIC)
    fun setSearchWithMic(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SEARCH_WITH_MIC, value).apply()
    }
    
    // Corner Radius (tile faceting)
    fun getCornerRadius(context: Context): Float = getPrefs(context).getFloat(KEY_CORNER_RADIUS, DEFAULT_CORNER_RADIUS)
    fun setCornerRadius(context: Context, value: Float) {
        getPrefs(context).edit().putFloat(KEY_CORNER_RADIUS, value.coerceIn(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS)).apply()
    }
    
    // Dim Status Bar
    fun getDimStatusBar(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DIM_STATUS_BAR, DEFAULT_DIM_STATUS_BAR)
    fun setDimStatusBar(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DIM_STATUS_BAR, value).apply()
    }
    
    // Dark Theme
    fun getDarkTheme(context: Context): Boolean = getPrefs(context).getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
    fun setDarkTheme(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_THEME, value).apply()
    }

    // Hidden Apps
    fun getHiddenApps(context: Context): Set<String> = getPrefs(context).getStringSet("hidden_apps", emptySet()) ?: emptySet()
    fun setHiddenApps(context: Context, hiddenApps: Set<String>) {
        getPrefs(context).edit().putStringSet("hidden_apps", hiddenApps).apply()
    }
    
    // Unified Bucket Colors (use bucket color instead of app dominant color)
    fun getUnifiedBucketColors(context: Context): Boolean = getPrefs(context).getBoolean(KEY_UNIFIED_BUCKET_COLORS, DEFAULT_UNIFIED_BUCKET_COLORS)
    fun setUnifiedBucketColors(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_UNIFIED_BUCKET_COLORS, value).apply()
    }
    
    // Dock Apps (pinned to dock bar)
    private const val KEY_DOCK_APPS = "dock_apps"
    fun getDockApps(context: Context): Set<String> = getPrefs(context).getStringSet(KEY_DOCK_APPS, emptySet()) ?: emptySet()
    fun setDockApps(context: Context, dockApps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_DOCK_APPS, dockApps).apply()
    }
    
    // Tile Transparency (0-100, where 100 is fully opaque)
    fun getTileTransparency(context: Context): Int = getPrefs(context).getInt(KEY_TILE_TRANSPARENCY, DEFAULT_TILE_TRANSPARENCY)
    fun setTileTransparency(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_TILE_TRANSPARENCY, value.coerceIn(0, 100)).apply()
    }
    
    // Dock Transparency (0-100, where 100 is fully opaque)
    fun getDockTransparency(context: Context): Int = getPrefs(context).getInt(KEY_DOCK_TRANSPARENCY, DEFAULT_DOCK_TRANSPARENCY)
    fun setDockTransparency(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_DOCK_TRANSPARENCY, value.coerceIn(0, 100)).apply()
    }
}
