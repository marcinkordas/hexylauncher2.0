package com.hexy.launcher.domain

import com.hexy.launcher.data.AppInfo

/**
 * Sorts apps for hexagonal grid placement.
 *
 * Algorithm:
 * 1. Ring 0 (center): 1 most-used app overall
 * 2. Rings 1-2 (inner rings): 18 most RECENTLY used apps (6 + 12 = 18 slots)
 * 3. Ring 3+: Apps grouped by color bucket, then sorted by usage within bucket
 *
 * Color distribution:
 * - Divide outer rings into 6 sectors (60° each)
 * - Each sector corresponds to one color bucket
 * - Within sector, more-used apps are closer to center
 */
object AppSorter {
    
    fun sortApps(apps: List<AppInfo>): List<AppInfo> {
        if (apps.isEmpty()) return emptyList()
        
        val result = mutableListOf<AppInfo>()
        val remaining = apps.toMutableList()
        
        // 1. Ring 0 (center): Single most-used app
        val mostUsed = remaining.maxByOrNull { it.usageCount }
        if (mostUsed != null) {
            result.add(mostUsed)
            remaining.remove(mostUsed)
        }
        
        // 2. Rings 1-2 (18 slots total: 6 + 12): Most recently used apps
        val recentApps = remaining
            .sortedByDescending { it.lastUsedTimestamp }
            .take(18)
        result.addAll(recentApps)
        remaining.removeAll(recentApps.toSet())
        
        // 3. Remaining: Group by color bucket, sort by usage
        val byBucket = remaining.groupBy { it.colorBucket }
        
        // Interleave buckets for even distribution
        val sortedBuckets = (0..5).map { bucket ->
            byBucket[bucket]?.sortedByDescending { it.usageCount } ?: emptyList()
        }
        
        // Zip buckets together (round-robin)
        val maxSize = sortedBuckets.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (bucket in sortedBuckets) {
                if (i < bucket.size) {
                    result.add(bucket[i])
                }
            }
        }
        
        return result
    }
}
