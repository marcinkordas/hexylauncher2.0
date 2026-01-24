package com.hexgrid.launcher.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility for exporting and importing launcher settings as JSON.
 */
object SettingsExporter {
    
    private const val SETTINGS_VERSION = 1
    
    // Keys to export
    private val FLOAT_KEYS = listOf(
        "hex_radius", "icon_size_multiplier", "icon_padding", 
        "outline_width", "corner_radius"
    )
    
    private val BOOLEAN_KEYS = listOf(
        "show_outline", "show_labels", "show_notification_glow",
        "search_with_mic", "dim_status_bar", "dark_theme", "unified_bucket_colors"
    )
    
    private val STRING_KEYS = listOf(
        "sort_order", "hex_orientation", "search_position"
    )
    
    private val INT_KEYS = listOf(
        "tile_transparency", "dock_transparency"
    )
    
    private val STRING_SET_KEYS = listOf(
        "hidden_apps", "dock_apps"
    )
    
    /**
     * Export all settings to a JSON string
     */
    fun exportToJson(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = JSONObject()
        
        // Version for future compatibility
        json.put("version", SETTINGS_VERSION)
        json.put("exported_at", System.currentTimeMillis())
        
        // Export float settings
        FLOAT_KEYS.forEach { key ->
            if (prefs.contains(key)) {
                json.put(key, prefs.getFloat(key, 0f).toDouble())
            }
        }
        
        // Export boolean settings
        BOOLEAN_KEYS.forEach { key ->
            if (prefs.contains(key)) {
                json.put(key, prefs.getBoolean(key, false))
            }
        }
        
        // Export string settings
        STRING_KEYS.forEach { key ->
            if (prefs.contains(key)) {
                json.put(key, prefs.getString(key, ""))
            }
        }
        
        // Export int settings
        INT_KEYS.forEach { key ->
            if (prefs.contains(key)) {
                json.put(key, prefs.getInt(key, 0))
            }
        }
        
        // Export string sets as JSON arrays
        STRING_SET_KEYS.forEach { key ->
            val set = prefs.getStringSet(key, null)
            if (set != null) {
                val array = JSONArray()
                set.forEach { array.put(it) }
                json.put(key, array)
            }
        }
        
        return json.toString(2) // Pretty print with 2-space indent
    }
    
    /**
     * Import settings from a JSON string
     * @return true if import was successful, false otherwise
     */
    fun importFromJson(context: Context, jsonString: String): Result<Int> {
        return try {
            val json = JSONObject(jsonString)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            var importedCount = 0
            
            // Check version (for future migrations)
            val version = json.optInt("version", 1)
            if (version > SETTINGS_VERSION) {
                return Result.failure(Exception("Settings file is from a newer version"))
            }
            
            // Import float settings
            FLOAT_KEYS.forEach { key ->
                if (json.has(key)) {
                    editor.putFloat(key, json.getDouble(key).toFloat())
                    importedCount++
                }
            }
            
            // Import boolean settings
            BOOLEAN_KEYS.forEach { key ->
                if (json.has(key)) {
                    editor.putBoolean(key, json.getBoolean(key))
                    importedCount++
                }
            }
            
            // Import string settings
            STRING_KEYS.forEach { key ->
                if (json.has(key)) {
                    editor.putString(key, json.getString(key))
                    importedCount++
                }
            }
            
            // Import int settings
            INT_KEYS.forEach { key ->
                if (json.has(key)) {
                    editor.putInt(key, json.getInt(key))
                    importedCount++
                }
            }
            
            // Import string sets from JSON arrays
            STRING_SET_KEYS.forEach { key ->
                if (json.has(key)) {
                    val array = json.getJSONArray(key)
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.getString(i))
                    }
                    editor.putStringSet(key, set)
                    importedCount++
                }
            }
            
            editor.apply()
            Result.success(importedCount)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a suggested filename for the export
     */
    fun getSuggestedFilename(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "hexgrid_launcher_settings_$timestamp.json"
    }
}
