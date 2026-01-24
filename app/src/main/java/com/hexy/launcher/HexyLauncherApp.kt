package com.hexy.launcher

import android.app.Application
import android.os.Build
import com.google.android.material.color.DynamicColors

class HexyLauncherApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Apply Material You dynamic colors on Android 12+ (API 31)
        // This will use the user's wallpaper-based system accent colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
    }
}
