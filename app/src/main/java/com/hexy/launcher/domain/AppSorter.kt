package com.hexy.launcher.domain

import com.hexy.launcher.data.AppInfo
import kotlin.math.sqrt

/**
 * Sorts apps for hexagonal grid placement with ANGULAR COLOR SECTORS.
 *
 * Algorithm:
 * 1. Center: most used app
 * 2. Each color bucket (0-5) gets its own 60° angular sector
 * 3. Apps fill their sector DENSELY, radiating outward
 * 4. Sectors can be very uneven (30 Red apps, 3 Blue apps)
 * 5. Result: Colored wedges of varying lengths
 */
object AppSorter {
    
    fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()
        
        // 1. Find center app
        val sortedByUsage = apps.sortedByDescending { it.usageCount }
        
        var centerApp: AppInfo? = sortedByUsage.firstOrNull()
        if (centerApp != null && centerApp.usageCount == 0L) {
            val phoneApp = apps.firstOrNull { 
                it.packageName.contains("dialer", ignoreCase = true) || 
                it.packageName.contains("phone", ignoreCase = true) 
            }
            if (phoneApp != null) {
                centerApp = phoneApp
            }
        }
        
        if (centerApp == null) return emptyList()
        
        // 2. Get remaining apps grouped by color bucket
        val remaining = apps.toMutableList()
        remaining.remove(centerApp)
        
        val buckets = (0..5).map { bucket ->
            remaining.filter { it.colorBucket == bucket }
                .sortedByDescending { it.usageCount }
        }
        
        // 3. Generate spiral positions and classify by angular bucket
        val hexPositions = HexGridCalculator(1f).generateSpiralCoordinates(25)
        
        // For each spiral position (except center), get its bucket
        // slotsByBucket[bucket] = list of (spiralIndex, hex) pairs, sorted by ring
        val slotsByBucket = (0..5).map { bucket ->
            (1 until hexPositions.size)
                .map { index -> Pair(index, hexPositions[index]) }
                .filter { (_, hex) -> getIdealBucketForHex(hex) == bucket }
                .sortedBy { (_, hex) -> hex.ring }
        }
        
        // 4. Build result: Place apps in their sector's slots
        // Collect (spiralIndex, app) pairs
        val placements = mutableListOf<Pair<Int, AppInfo>>()
        placements.add(Pair(0, centerApp)) // Center at index 0
        
        for (bucket in 0..5) {
            val appList = buckets[bucket]
            val slots = slotsByBucket[bucket]
            
            for ((i, app) in appList.withIndex()) {
                if (i < slots.size) {
                    val (spiralIndex, _) = slots[i]
                    placements.add(Pair(spiralIndex, app))
                }
            }
        }
        
        // 5. Sort by spiral index and return just the apps
        placements.sortBy { it.first }
        return placements.map { it.second }
    }
    
    private fun getIdealBucketForHex(hex: HexCoordinate): Int {
        val x = sqrt(3.0) * (hex.q + hex.r / 2.0)
        val y = 1.5 * hex.r
        
        val angle = Math.atan2(y, x)
        val normalizedAngle = (Math.toDegrees(angle) + 360) % 360
        
        // Bucket 0: -30° to 30° (centered on 0° = right)
        // Bucket 1: 30° to 90° (centered on 60°)
        // ...
        return ((normalizedAngle + 30) / 60).toInt() % 6
    }
}
