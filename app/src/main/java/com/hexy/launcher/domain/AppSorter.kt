package com.hexy.launcher.domain

import com.hexy.launcher.data.AppInfo

/**
 * Sorts apps for hexagonal grid placement with WINDMILL layout.
 *
 * Algorithm:
 * - Ring 0 (center): Most used app
 * - Ring 1 (6 apps): Next 6 most used apps (usage-based, not color)
 * - Ring 2+: Apps placed in color bucket sectors
 *   - Each sector gets apps of that color, sorted by usage
 *   - If bucket empty, slot stays empty (placeholder)
 */
object AppSorter {
    
    // Placeholder app for empty slots
    private var emptyPlaceholder: AppInfo? = null
    
    fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()
        
        // Sort all apps by usage
        val sortedByUsage = apps.sortedByDescending { it.usageCount }
        
        // Ring 0 + Ring 1 = first 7 apps by usage (not color)
        val innerApps = sortedByUsage.take(7)
        val outerApps = sortedByUsage.drop(7)
        
        // Create placeholder for empty slots
        emptyPlaceholder = sortedByUsage.first().copy(
            packageName = "_empty_",
            label = "",
            usageCount = -1
        )
        
        // Group outer apps by color bucket, sorted by usage within each
        val bucketQueues = (0..5).map { bucket ->
            outerApps.filter { it.colorBucket == bucket }
                .sortedByDescending { it.usageCount }
                .toMutableList()
        }
        
        // Generate spiral with bucket assignments
        val spiral = HexGridCalculator(1f).generateWindmillSpiral(25)
        
        // Build result
        val result = mutableListOf<AppInfo>()
        
        // Add inner apps (ring 0 + ring 1) - first 7 positions
        for (i in 0 until minOf(7, innerApps.size)) {
            result.add(innerApps[i])
        }
        
        // Fill remaining inner positions if not enough apps
        while (result.size < 7 && innerApps.isNotEmpty()) {
            result.add(emptyPlaceholder!!)
        }
        
        // Add outer apps (ring 2+) - position 7 onwards
        for (i in 7 until spiral.size) {
            val bucket = spiral[i].second
            
            if (bucket in 0..5 && bucketQueues[bucket].isNotEmpty()) {
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
