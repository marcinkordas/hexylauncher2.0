package com.hexy.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.hexy.launcher.util.ColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    
    private val packageManager: PackageManager = context.packageManager
    
    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<AppInfo>()
        val usageStats = UsageStatsHelper.getUsageStats(context)
        
        // 1. Load regular launcher apps
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        
        resolveInfos.mapNotNullTo(apps) { ri ->
            try {
                val packageName = ri.activityInfo.packageName
                val label = ri.loadLabel(packageManager).toString()
                val icon = ri.loadIcon(packageManager)
                val (dominantColor, bucket) = ColorExtractor.extractColor(icon)
                val stats = usageStats[packageName]
                
                AppInfo(
                    packageName = packageName,
                    label = label,
                    icon = icon,
                    dominantColor = dominantColor,
                    colorBucket = bucket,
                    usageCount = stats?.totalTimeInForeground ?: 0L,
                    lastUsedTimestamp = stats?.lastTimeUsed ?: 0L,
                    isShortcut = false
                )
            } catch (e: Exception) {
                null
            }
        }
        
        // 2. Load pinned shortcuts (including PWAs)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            shortcutManager?.pinnedShortcuts?.mapNotNullTo(apps) { shortcut ->
                try {
                    val icon = shortcutManager.getShortcutIconDrawable(shortcut, 0)
                        ?: context.packageManager.getApplicationIcon(shortcut.`package`)
                    val (dominantColor, bucket) = ColorExtractor.extractColor(icon)
                    
                    AppInfo(
                        packageName = shortcut.`package`,
                        label = shortcut.shortLabel?.toString() ?: shortcut.id,
                        icon = icon,
                        dominantColor = dominantColor,
                        colorBucket = bucket,
                        usageCount = 0L, // Shortcuts don't have usage stats
                        lastUsedTimestamp = 0L,
                        isShortcut = true,
                        shortcutId = shortcut.id
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        apps
    }
    
    fun launchApp(app: AppInfo, context: Context) {
        if (app.isShortcut && app.shortcutId != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            // For shortcuts, we need the proper activity context
            if (context is android.app.Activity) {
                shortcutManager?.startShortcut(app.packageName, app.shortcutId, null, null)
            } else {
                // Fallback: use regular launch intent
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.let { context.startActivity(it) }
            }
        } else {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.let { context.startActivity(it) }
        }
    }
}
