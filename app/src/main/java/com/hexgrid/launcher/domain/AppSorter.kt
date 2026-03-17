package com.hexgrid.launcher.domain

import android.content.Context
import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.util.SettingsManager

/**
 * Sorts apps for hexagonal grid placement with WINDMILL layout.
 *
 * Algorithm:
 * - Ring 0 (center): Most used app
 * - Ring 1 (6 apps): Next 6 most used apps (usage-based, not color)
 * - Ring 2+: Apps placed in color bucket sectors (now 10 buckets)
 */
object AppSorter {
    
    private var emptyPlaceholder: AppInfo? = null
    
    fun sortApps(
        apps: List<AppInfo>,
        context: Context,
        occupiedCells: Set<HexCoordinate> = emptySet()
    ): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()
        
        val sortOrder = SettingsManager.getSortOrder(context)
        
        // Sort all apps according to selected sort order
        val sortedApps = when (sortOrder) {
            SettingsManager.SortOrder.NAME -> 
                apps.sortedBy { it.label.lowercase() }
            SettingsManager.SortOrder.USAGE_FREQUENCY -> 
                apps.sortedByDescending { it.usageCount }
            SettingsManager.SortOrder.USAGE_TIME -> 
                apps.sortedByDescending { it.lastUsedTimestamp }
            SettingsManager.SortOrder.NOTIFICATION_COUNT -> 
                apps.sortedByDescending { it.notificationCount }
        }
        
        // Ring 0 + Ring 1 = first 7 apps by selected sort
        val innerApps = sortedApps.take(7)
        val outerApps = sortedApps.drop(7)
        
        // Create placeholder for empty slots
        emptyPlaceholder = sortedApps.first().copy(
            packageName = "_empty_",
            label = "",
            usageCount = -1
        )
        
        // Group outer apps by color bucket (0-10), sorted by selected sort order
        val bucketQueues = (0..10).map { bucket ->
            outerApps.filter { it.colorBucket == bucket }
                .let { bucketApps ->
                    when (sortOrder) {
                        SettingsManager.SortOrder.NAME -> 
                            bucketApps.sortedBy { it.label.lowercase() }
                        SettingsManager.SortOrder.USAGE_FREQUENCY -> 
                            bucketApps.sortedByDescending { it.usageCount }
                        SettingsManager.SortOrder.USAGE_TIME -> 
                            bucketApps.sortedByDescending { it.lastUsedTimestamp }
                        SettingsManager.SortOrder.NOTIFICATION_COUNT -> 
                            bucketApps.sortedByDescending { it.notificationCount }
                    }
                }
                .toMutableList()
        }
        
        // Generate spiral with 11 bucket assignments
        val spiral = HexGridCalculator(1f).generateWindmillSpiral(25, numBuckets = 11)
        
        // Build result
        val result = mutableListOf<AppInfo>()
        
        // Add inner apps (ring 0 + ring 1) - first 7 spiral positions, skipping occupied
        val innerQueue = innerApps.toMutableList()
        for (i in 0 until minOf(7, spiral.size)) {
            if (occupiedCells.isNotEmpty() && spiral[i].first in occupiedCells) continue
            result.add(innerQueue.removeFirstOrNull() ?: emptyPlaceholder!!)
        }

        // Add outer apps (ring 2+) - position 7 onwards
        for (i in 7 until spiral.size) {
            if (occupiedCells.isNotEmpty() && spiral[i].first in occupiedCells) continue

            val bucket = spiral[i].second

            if (bucket in 0..10 && bucketQueues[bucket].isNotEmpty()) {
                result.add(bucketQueues[bucket].removeAt(0))
            } else {
                // Empty slot - use placeholder
                result.add(emptyPlaceholder!!)
            }

            // Stop if all buckets empty
            if (bucketQueues.all { it.isEmpty() }) {
                // Fill remaining slots in current ring, then stop
                val currentRing = spiral[i].first.ring
                while (result.size < spiral.size && spiral[result.size].first.ring == currentRing) {
                    result.add(emptyPlaceholder!!)
                }
                break
            }
        }
        
        return result
    }
    
    fun isPlaceholder(app: AppInfo): Boolean {
        return app.packageName == "_empty_"
    }
}
