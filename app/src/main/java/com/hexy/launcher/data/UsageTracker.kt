package com.hexy.launcher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple click-based usage tracker.
 * Tracks how many times each app is launched - no special permissions needed.
 */
object UsageTracker {
    
    private const val PREFS_NAME = "hexy_usage_tracker"
    private const val KEY_PREFIX_CLICK_COUNT = "clicks_"
    private const val KEY_PREFIX_LAST_CLICK = "last_click_"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Record an app launch. Call this when user taps an app.
     */
    fun recordClick(context: Context, packageName: String) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getLong(KEY_PREFIX_CLICK_COUNT + packageName, 0L)
        prefs.edit()
            .putLong(KEY_PREFIX_CLICK_COUNT + packageName, currentCount + 1)
            .putLong(KEY_PREFIX_LAST_CLICK + packageName, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get click count for an app.
     */
    fun getClickCount(context: Context, packageName: String): Long {
        return getPrefs(context).getLong(KEY_PREFIX_CLICK_COUNT + packageName, 0L)
    }
    
    /**
     * Get last click timestamp for an app.
     */
    fun getLastClickTime(context: Context, packageName: String): Long {
        return getPrefs(context).getLong(KEY_PREFIX_LAST_CLICK + packageName, 0L)
    }
    
    /**
     * Get all usage stats as a map.
     */
    fun getAllStats(context: Context): Map<String, Pair<Long, Long>> {
        val prefs = getPrefs(context)
        val result = mutableMapOf<String, Pair<Long, Long>>()
        
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX_CLICK_COUNT) && value is Long) {
                val packageName = key.removePrefix(KEY_PREFIX_CLICK_COUNT)
                val lastClick = prefs.getLong(KEY_PREFIX_LAST_CLICK + packageName, 0L)
                result[packageName] = Pair(value, lastClick)
            }
        }
        
        return result
    }
}
