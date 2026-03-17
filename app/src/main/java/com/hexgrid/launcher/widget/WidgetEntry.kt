package com.hexgrid.launcher.widget

data class WidgetEntry(
    val widgetId: Int,       // internal sequential ID
    val appWidgetId: Int,    // Android system widget ID
    val centerHexQ: Int,
    val centerHexR: Int,
    val widthPx: Int,
    val heightPx: Int
)
