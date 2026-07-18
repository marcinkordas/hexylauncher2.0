package com.hexgrid.launcher.ui.edit

import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Assign [raw] to this slider, clamped to [Slider.valueFrom]..[Slider.valueTo] and
 * snapped to the nearest [Slider.stepSize] multiple.
 *
 * Material's `Slider.setValue()` throws IllegalStateException for values that are
 * out of range or not aligned to the step. Persisted preferences can hold such
 * values when they were restored/imported as raw floats (SharedPreferences XML
 * injection or Settings import bypass the coercing setters), so assigning them
 * directly would crash Edit Mode on entry. This makes the assignment total.
 */
fun Slider.setValueSafely(raw: Float) {
    val clamped = raw.coerceIn(valueFrom, valueTo)
    val snapped = if (stepSize > 0f) {
        valueFrom + ((clamped - valueFrom) / stepSize).roundToInt() * stepSize
    } else {
        clamped
    }
    value = snapped.coerceIn(valueFrom, valueTo)
}
