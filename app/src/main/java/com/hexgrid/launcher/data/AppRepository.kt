package com.hexgrid.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import com.hexgrid.launcher.util.ColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
    
    // Memory cache to prevent sluggish reloading
    private var cachedApps: List<AppInfo>? = null

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val usageStats = UsageTracker.getAllStats(context)
        
        // If we have cached apps, just update their usage stats and return
        cachedApps?.let { cache ->
            return@withContext cache.map { app ->
                val key = if (app.isShortcut && app.shortcutId != null) "${app.packageName}_${app.shortcutId}" else app.packageName
                val stats = usageStats[key]
                val notifCount = com.hexgrid.launcher.service.NotificationListener.getNotificationCount(app.packageName)
                app.copy(
                    usageCount = stats?.first ?: 0L,
                    lastUsedTimestamp = stats?.second ?: 0L,
                    notificationCount = notifCount
                )
            }
        }

        val apps = mutableListOf<AppInfo>()
        
        // 1. Load regular launcher apps
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val currentUser = android.os.Process.myUserHandle()

        resolveInfos.mapNotNullTo(apps) { ri ->
            try {
                val packageName = ri.activityInfo.packageName
                val label = ri.loadLabel(packageManager).toString()
                val icon: Drawable = ri.loadIcon(packageManager)
                // Note: System clock apps typically have their own dynamic icons
                // that update automatically - no need to wrap them
                
                val (dominantColor, bucket) = ColorExtractor.extractColor(icon, packageName)
                val stats = usageStats[packageName]

                val notifCount = com.hexgrid.launcher.service.NotificationListener.getNotificationCount(packageName)

                AppInfo(
                    packageName = packageName,
                    label = label,
                    icon = icon,
                    dominantColor = dominantColor,
                    colorBucket = bucket,
                    usageCount = stats?.first ?: 0L,
                    lastUsedTimestamp = stats?.second ?: 0L,
                    notificationCount = notifCount,
                    isShortcut = false,
                    userHandle = currentUser
                )
            } catch (e: Exception) {
                null
            }
        }

        // 2. Load pinned shortcuts (including PWAs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
             try {
                val query = android.content.pm.LauncherApps.ShortcutQuery()
                query.setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                val shortcuts = launcherApps.getShortcuts(query, currentUser) ?: emptyList()

                shortcuts.mapNotNullTo(apps) { shortcut ->
                    try {
                        val icon = launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi) 
                            ?: context.packageManager.getApplicationIcon(shortcut.`package`)
                            
                        val (dominantColor, bucket) = ColorExtractor.extractColor(icon, shortcut.`package`)
                        val shortcutKey = "${shortcut.`package`}_${shortcut.id}"
                        val stats = usageStats[shortcutKey]

                        val notifCount = com.hexgrid.launcher.service.NotificationListener.getNotificationCount(shortcut.`package`)
                        
                        AppInfo(
                            packageName = shortcut.`package`,
                            label = shortcut.shortLabel?.toString() ?: shortcut.id,
                            icon = icon,
                            dominantColor = dominantColor,
                            colorBucket = bucket,
                            usageCount = stats?.first ?: 0L,
                            lastUsedTimestamp = stats?.second ?: 0L,
                            notificationCount = notifCount,
                            isShortcut = true,
                            shortcutId = shortcut.id,
                            userHandle = shortcut.userHandle
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Update cache
        cachedApps = apps
        apps
    }

    fun launchApp(app: AppInfo, context: Context) {
        val key = if (app.isShortcut && app.shortcutId != null) "${app.packageName}_${app.shortcutId}" else app.packageName
        UsageTracker.recordClick(context, key)
        
        if (app.isShortcut && app.shortcutId != null && app.userHandle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                launcherApps.startShortcut(app.packageName, app.shortcutId, null, null, app.userHandle)
            } catch (e: Exception) {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.let { context.startActivity(it) }
            }
        } else {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            intent?.let { context.startActivity(it) }
        }
    }
}
