package com.hexgrid.launcher.data

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageStatsHelper {
    
    fun getUsageStats(context: Context): Map<String, UsageStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30) // Last 30 days
        val startTime = calendar.timeInMillis
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        return usageStatsList
            ?.groupBy { it.packageName }
            ?.mapValues { (_, stats) ->
                // Merge stats for same package
                stats.maxByOrNull { it.lastTimeUsed } ?: stats.first()
            } ?: emptyMap()
    }
    
    fun hasPermission(context: Context): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.HOUR, -1)
        val startTime = calendar.timeInMillis
        
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )
        return stats?.isNotEmpty() == true
    }
}
