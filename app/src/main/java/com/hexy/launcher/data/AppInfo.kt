package com.hexy.launcher.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val dominantColor: Int,      // Extracted from icon
    val colorBucket: Int,        // 0-5 (6 buckets)
    val usageCount: Long,        // From UsageStats
    val lastUsedTimestamp: Long, // For recency sorting
    val notificationCount: Int = 0, // Active notification count
    val isShortcut: Boolean = false,  // True if PWA/shortcut
    val shortcutId: String? = null,    // ShortcutInfo ID if applicable
    val userHandle: android.os.UserHandle? = null // User handle for the app/shortcut
)
