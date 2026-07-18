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
    ): AppWidgetHostView =
        // MUST use applicationContext, NOT an AppCompatActivity context.
        // AppCompatActivity.installViewFactory() puts an AppCompat factory on the activity's
        // LayoutInflater that substitutes ImageView → AppCompatImageView (and ImageButton →
        // AppCompatImageButton). RemoteViews.apply() calls cloneInContext() which copies that
        // factory; RemoteViews then tries to call methods like setImageResource(int) which are
        // whitelisted ONLY for the exact ImageView class (not AppCompatImageView) → throws
        // RemoteViews$ActionException → AppWidgetHostView falls back to "Couldn't add widget.".
        // The Application context has no such factory, so RemoteViews inflate cleanly.
        createView(context.applicationContext, appWidgetId, info)

    fun releaseId(appWidgetId: Int) = deleteAppWidgetId(appWidgetId)
}
