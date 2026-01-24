package com.hexgrid.launcher.util

import android.content.Context

/**
 * Tracks app positions to enable inertial/slower migration.
 * Apps move max 2 positions per session to avoid confusing users.
 */
object PositionTracker {
    
    private const val PREFS_NAME = "position_tracker"
    private const val KEY_POSITIONS = "app_positions"
    private const val MAX_MOVE_PER_SESSION = 2
    
    private var cachedPositions: MutableMap<String, Int>? = null
    
    private fun getPrefs(context: Context) = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Load saved positions from storage.
     */
    fun loadPositions(context: Context): Map<String, Int> {
        if (cachedPositions != null) return cachedPositions!!
        
        val prefs = getPrefs(context)
        val positionsStr = prefs.getString(KEY_POSITIONS, "") ?: ""
        
        cachedPositions = mutableMapOf()
        if (positionsStr.isNotEmpty()) {
            positionsStr.split(";").forEach { entry ->
                val parts = entry.split("=")
                if (parts.size == 2) {
                    cachedPositions!![parts[0]] = parts[1].toIntOrNull() ?: 0
                }
            }
        }
        
        return cachedPositions!!
    }
    
    /**
     * Save positions to storage.
     */
    private fun savePositions(context: Context) {
        val positionsStr = cachedPositions?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: ""
        getPrefs(context).edit().putString(KEY_POSITIONS, positionsStr).apply()
    }
    
    /**
     * Calculate adjusted position with inertial constraint.
     * Returns the position the app should actually be placed at.
     */
    fun getAdjustedPosition(context: Context, packageName: String, targetPosition: Int): Int {
        val positions = loadPositions(context)
        val previousPosition = positions[packageName] ?: targetPosition
        
        // Calculate allowed movement
        val delta = targetPosition - previousPosition
        val clampedDelta = delta.coerceIn(-MAX_MOVE_PER_SESSION, MAX_MOVE_PER_SESSION)
        val newPosition = previousPosition + clampedDelta
        
        // Update cache
        if (cachedPositions == null) cachedPositions = mutableMapOf()
        cachedPositions!![packageName] = newPosition
        
        return newPosition
    }
    
    /**
     * Commit all position changes to storage.
     * Call this after sorting is complete.
     */
    fun commitPositions(context: Context) {
        savePositions(context)
    }
    
    /**
     * Clear all tracked positions (for reset).
     */
    fun clearPositions(context: Context) {
        cachedPositions = mutableMapOf()
        getPrefs(context).edit().remove(KEY_POSITIONS).apply()
    }
}
