package com.hexgrid.launcher.widget

import android.content.Context
import androidx.preference.PreferenceManager
import com.hexgrid.launcher.util.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

class WidgetStore(private val context: Context) {

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    fun loadAll(): List<WidgetEntry> =
        parseEntries(prefs().getString(KEY_WIDGETS, "[]") ?: "[]")

    fun saveAll(entries: List<WidgetEntry>) =
        prefs().edit().putString(KEY_WIDGETS, entriesToJson(entries)).apply()

    fun add(entry: WidgetEntry) = saveAll(loadAll() + entry)

    fun remove(widgetId: Int) = saveAll(loadAll().filter { it.widgetId != widgetId })

    fun update(entry: WidgetEntry) =
        saveAll(loadAll().map { if (it.widgetId == entry.widgetId) entry else it })

    fun nextWidgetId(): Int = (loadAll().maxOfOrNull { it.widgetId } ?: 0) + 1

    companion object {
        val KEY_WIDGETS get() = SettingsManager.KEY_WIDGETS

        fun entriesToJson(entries: List<WidgetEntry>): String {
            val arr = JSONArray()
            for (e in entries) {
                arr.put(JSONObject().apply {
                    put("widgetId", e.widgetId)
                    put("appWidgetId", e.appWidgetId)
                    put("centerHexQ", e.centerHexQ)
                    put("centerHexR", e.centerHexR)
                    put("widthPx", e.widthPx)
                    put("heightPx", e.heightPx)
                })
            }
            return arr.toString()
        }

        fun parseEntries(json: String): List<WidgetEntry> {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    WidgetEntry(
                        widgetId = o.getInt("widgetId"),
                        appWidgetId = o.getInt("appWidgetId"),
                        centerHexQ = o.getInt("centerHexQ"),
                        centerHexR = o.getInt("centerHexR"),
                        widthPx = o.getInt("widthPx"),
                        heightPx = o.getInt("heightPx")
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
