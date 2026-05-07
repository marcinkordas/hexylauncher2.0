package com.hexgrid.launcher.ui.edit

import android.view.View
import android.view.ViewGroup

/** Common interface for Shape, Style, and Order edit panels. */
interface EditPanel {
    val view: View
    fun attach(parent: ViewGroup)
    fun detach(parent: ViewGroup)
}
