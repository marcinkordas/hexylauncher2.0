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
    
    // Default values
    const val DEFAULT_HEX_RADIUS = 96f
    const val DEFAULT_ICON_SIZE_MULTIPLIER = 1.0f
    const val DEFAULT_ICON_PADDING = 12f
    const val DEFAULT_SHOW_OUTLINE = true
    const val DEFAULT_SHOW_LABELS = true
    
    // Min/Max ranges
    const val MIN_HEX_RADIUS = 50f
    const val MAX_HEX_RADIUS = 150f
    const val MIN_ICON_SIZE_MULTIPLIER = 0.5f
    const val MAX_ICON_SIZE_MULTIPLIER = 1.5f
    const val MIN_ICON_PADDING = 0f
    const val MAX_ICON_PADDING = 20f
    
    private fun getPrefs(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
    
    fun getHexRadius(context: Context): Float {
        return getPrefs(context).getFloat(KEY_HEX_RADIUS, DEFAULT_HEX_RADIUS)
    }
    
    fun setHexRadius(context: Context, value: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_HEX_RADIUS, value.coerceIn(MIN_HEX_RADIUS, MAX_HEX_RADIUS))
            .apply()
    }
    
    fun getIconSizeMultiplier(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ICON_SIZE_MULTIPLIER, DEFAULT_ICON_SIZE_MULTIPLIER)
    }
    
    fun setIconSizeMultiplier(context: Context, value: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_ICON_SIZE_MULTIPLIER, value.coerceIn(MIN_ICON_SIZE_MULTIPLIER, MAX_ICON_SIZE_MULTIPLIER))
            .apply()
    }
    
    fun getIconPadding(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ICON_PADDING, DEFAULT_ICON_PADDING)
    }
    
    fun setIconPadding(context: Context, value: Float) {
        getPrefs(context).edit()
            .putFloat(KEY_ICON_PADDING, value.coerceIn(MIN_ICON_PADDING, MAX_ICON_PADDING))
            .apply()
    }
    
    fun getShowOutline(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_OUTLINE, DEFAULT_SHOW_OUTLINE)
    }
    
    fun setShowOutline(context: Context, value: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_SHOW_OUTLINE, value)
            .apply()
    }
    
    fun getShowLabels(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_LABELS, DEFAULT_SHOW_LABELS)
    }
    
    fun setShowLabels(context: Context, value: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_SHOW_LABELS, value)
            .apply()
    }
}
