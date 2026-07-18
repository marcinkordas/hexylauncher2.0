package com.hexgrid.launcher.ui.edit

import android.view.View
import android.view.ViewGroup
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/** Common interface for Shape, Style, and Order edit panels. */
interface EditPanel {
    val view: View
    fun attach(parent: ViewGroup)
    fun detach(parent: ViewGroup)
}

/**
 * Clamp [raw] into [Slider.valueFrom]..[Slider.valueTo] and snap onto the step grid
 * before assigning. Material Slider throws IllegalStateException if value is not
 * exactly aligned (e.g. legacy persisted value 142.3 against stepSize=1).
 */
internal fun Slider.setValueSafe(raw: Float) {
    val from = valueFrom
    val to = valueTo
    val step = stepSize
    val clamped = raw.coerceIn(from, to)
    val snapped = if (step > 0f) {
        from + ((clamped - from) / step).roundToInt() * step
    } else clamped
    value = snapped.coerceIn(from, to)
}
