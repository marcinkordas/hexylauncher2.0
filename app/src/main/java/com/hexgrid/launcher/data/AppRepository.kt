package com.hexgrid.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.hexgrid.launcher.util.ColorExtractor
import com.hexgrid.launcher.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps

    // Memory cache to prevent sluggish reloading
    private var cachedApps: List<AppInfo>? = null

    /**
     * Icons are decoded by the platform at their native asset resolution — an
     * AdaptiveIconDrawable in particular carries full foreground+background layers
     * (often 300-450px on high-density screens) regardless of how large it's ever
     * actually drawn. The largest an icon can appear on screen is in the hex grid,
     * capped at hexRadius * 1.1 * iconSizeMultiplier (see [HexagonalGridView] and
     * [SettingsManager.MAX_HEX_RADIUS] / [SettingsManager.MAX_ICON_SIZE_MULTIPLIER]).
     * Rasterizing every icon down to that worst-case pixel size once at load time
     * (instead of retaining native-resolution drawables for every installed app for
     * the process lifetime) avoids holding several hundred KB of unused pixels per
     * icon. Never upscales an icon that's already smaller than this.
     */
    private val maxIconRenderSizePx: Int by lazy {
        kotlin.math.ceil(
            SettingsManager.MAX_HEX_RADIUS * 1.1f * SettingsManager.MAX_ICON_SIZE_MULTIPLIER
        ).toInt()
    }

    fun invalidateCache() {
        cachedApps = null
    }

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val usageStats = UsageTracker.getAllStats(context)

        // If icon shape changed since last load, rebuild from scratch
        if (SettingsManager.getIconCacheDirty(context)) {
            cachedApps = null
            SettingsManager.setIconCacheDirty(context, false)
        }

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

        val cornerRadiusRatio = SettingsManager.getShortcutIconCornerRadiusRatio(context)
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
                // Filter out suspended packages (Samsung Modes, enterprise policy, parental controls).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageManager.isPackageSuspended(packageName)) {
                    return@mapNotNullTo null
                }
                val label = ri.loadLabel(packageManager).toString()
                val rawIcon: Drawable = ri.loadIcon(packageManager)
                // Apply squircle/circle mask to non-adaptive icons (WebAPKs, old apps)
                // AdaptiveIconDrawable icons are already shaped by the system (Samsung squircle etc.)
                val maskedIcon = if (rawIcon !is AdaptiveIconDrawable && cornerRadiusRatio > 0f) {
                    applyIconMask(rawIcon, cornerRadiusRatio)
                } else {
                    rawIcon
                }
                // Extract color from the pre-rasterized drawable so bucketing/sorting
                // output is unaffected by the memory-saving downsample below.
                val (dominantColor, bucket) = ColorExtractor.extractColor(maskedIcon, packageName)
                val icon = rasterizeIcon(maskedIcon)
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
                        // Filter out shortcuts whose package is suspended.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageManager.isPackageSuspended(shortcut.`package`)) {
                            return@mapNotNullTo null
                        }
                        val rawIcon = launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                            ?: context.packageManager.getApplicationIcon(shortcut.`package`)
                        val maskedIcon = if (rawIcon !is AdaptiveIconDrawable && cornerRadiusRatio > 0f) {
                            applyIconMask(rawIcon, cornerRadiusRatio)
                        } else {
                            rawIcon
                        }
                        // Extract color from the pre-rasterized drawable so bucketing/sorting
                        // output is unaffected by the memory-saving downsample below.
                        val (dominantColor, bucket) = ColorExtractor.extractColor(maskedIcon, shortcut.`package`)
                        val icon = rasterizeIcon(maskedIcon)
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

    /**
     * Clips a drawable to a rounded-rect shape at load time.
     * Uses clipPath on a software Canvas (Bitmap-backed) — always works correctly,
     * unlike clipPath on hardware-accelerated View canvas or PorterDuff on some devices.
     * [cornerRadiusRatio] is 0.3 for squircle, 0.5 for circle.
     */
    private fun applyIconMask(drawable: Drawable, cornerRadiusRatio: Float): Drawable {
        val size = (drawable.intrinsicWidth.takeIf { it > 0 } ?: 192).coerceIn(48, maxIconRenderSizePx)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output) // software canvas — clipPath always works

        val cornerRadius = size * cornerRadiusRatio
        val clipPath = Path()
        clipPath.addRoundRect(0f, 0f, size.toFloat(), size.toFloat(),
            cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)

        return BitmapDrawable(context.resources, output)
    }

    /**
     * Flattens an icon Drawable to a single bitmap no larger than
     * [maxIconRenderSizePx] on its longer side. AdaptiveIconDrawable is always
     * flattened — it holds separate foreground/background layers at native asset
     * resolution regardless of its reported intrinsic size. Plain drawables that
     * are already within bounds are returned unchanged to avoid a pointless extra
     * draw pass. Never upscales, so an icon that's already smaller stays untouched.
     */
    private fun rasterizeIcon(drawable: Drawable): Drawable {
        if (drawable !is AdaptiveIconDrawable) {
            val iw = drawable.intrinsicWidth
            val ih = drawable.intrinsicHeight
            if (iw in 1..maxIconRenderSizePx && ih in 1..maxIconRenderSizePx) return drawable
        }

        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxIconRenderSizePx
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxIconRenderSizePx
        val scale = minOf(maxIconRenderSizePx.toFloat() / iw, maxIconRenderSizePx.toFloat() / ih, 1f)
        val width = (iw * scale).toInt().coerceAtLeast(1)
        val height = (ih * scale).toInt().coerceAtLeast(1)

        val bitmap = drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        return BitmapDrawable(context.resources, bitmap)
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
