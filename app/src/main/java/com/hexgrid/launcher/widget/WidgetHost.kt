package com.hexgrid.launcher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Wraps AppWidgetHost with hardcoded host ID 1024.
 * Hardcoded ID avoids orphaned widgets if SharedPreferences are cleared.
 */
class WidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    companion object {
        const val HOST_ID = 1024
    }

    fun allocateId(): Int = allocateAppWidgetId()

    fun createHostView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo
    ): AppWidgetHostView = createView(context, appWidgetId, info)

    fun releaseId(appWidgetId: Int) = deleteAppWidgetId(appWidgetId)
}
