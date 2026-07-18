package com.hexgrid.launcher.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * Read the dominant color of the system wallpaper (API 27+) and expose it as the
 * launcher accent. When `use_wallpaper_accent` is off, falls back to the static
 * brand accent (#7C5CFC, the v2 design token).
 *
 * No-permission read: getWallpaperColors does not require READ_EXTERNAL_STORAGE
 * starting from API 27.
 */
object WallpaperAccent {

    const val DEFAULT_ACCENT: Int = 0xFF7C5CFC.toInt()
    const val DEFAULT_ACCENT_SECONDARY: Int = 0xFF00D4FF.toInt()

    fun resolve(context: Context): Int {
        if (!SettingsManager.getUseWallpaperAccent(context)) return DEFAULT_ACCENT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return DEFAULT_ACCENT
        val wm = WallpaperManager.getInstance(context) ?: return DEFAULT_ACCENT
        val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return DEFAULT_ACCENT
        val primary = colors.primaryColor.toArgb()
        // Saturate / cap brightness so it reads as an accent rather than washed out.
        return punchUp(primary)
    }

    fun resolveSecondary(context: Context): Int {
        if (!SettingsManager.getUseWallpaperAccent(context)) return DEFAULT_ACCENT_SECONDARY
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return DEFAULT_ACCENT_SECONDARY
        val wm = WallpaperManager.getInstance(context) ?: return DEFAULT_ACCENT_SECONDARY
        val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return DEFAULT_ACCENT_SECONDARY
        val secondary = colors.secondaryColor?.toArgb() ?: colors.primaryColor.toArgb()
        return punchUp(secondary)
    }

    /**
     * Avoid muddy or near-white accents — clamp brightness into [0.35, 0.7] and lift
     * saturation to >= 0.55. Preserves the wallpaper's hue.
     */
    private fun punchUp(argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        if (hsv[1] < 0.55f) hsv[1] = 0.55f
        hsv[2] = hsv[2].coerceIn(0.35f, 0.7f)
        return Color.HSVToColor(hsv)
    }
}
