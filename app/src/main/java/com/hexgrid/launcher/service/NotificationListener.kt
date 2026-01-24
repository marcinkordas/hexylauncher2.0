package com.hexgrid.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for notifications and tracks count per package.
 */
class NotificationListener : NotificationListenerService() {
    
    companion object {
        // Package name -> notification count
        private val notificationCounts = ConcurrentHashMap<String, Int>()
        
        fun getNotificationCount(packageName: String): Int {
            return notificationCounts[packageName] ?: 0
        }
        
        fun getAllCounts(): Map<String, Int> = notificationCounts.toMap()
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val pkg = it.packageName
            notificationCounts[pkg] = (notificationCounts[pkg] ?: 0) + 1
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            val pkg = it.packageName
            val current = notificationCounts[pkg] ?: 0
            if (current > 0) {
                notificationCounts[pkg] = current - 1
            }
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        // Initialize counts from active notifications
        try {
            activeNotifications?.forEach { sbn ->
                val pkg = sbn.packageName
                notificationCounts[pkg] = (notificationCounts[pkg] ?: 0) + 1
            }
        } catch (e: Exception) {
            // Permission not granted yet
        }
    }
}
