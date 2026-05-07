package com.hexgrid.launcher.ui

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditModeInterceptTest {

    private fun shouldIntercept(x: Float, y: Float, panelRect: Rect, toolbarRect: Rect): Boolean {
        return !panelRect.contains(x.toInt(), y.toInt()) &&
               !toolbarRect.contains(x.toInt(), y.toInt())
    }

    private val panel   = Rect(50, 400, 350, 600)
    private val toolbar = Rect(80, 620, 320, 680)

    @Test
    fun `touch inside panel is NOT intercepted`() {
        assertFalse(shouldIntercept(200f, 500f, panel, toolbar))
    }

    @Test
    fun `touch inside toolbar is NOT intercepted`() {
        assertFalse(shouldIntercept(200f, 650f, panel, toolbar))
    }

    @Test
    fun `touch outside both rects IS intercepted`() {
        assertTrue(shouldIntercept(10f, 10f, panel, toolbar))
    }

    @Test
    fun `touch on panel edge is NOT intercepted`() {
        assertFalse(shouldIntercept(50f, 400f, panel, toolbar))
    }

    @Test
    fun `touch between panel and toolbar IS intercepted`() {
        assertTrue(shouldIntercept(200f, 610f, panel, toolbar))
    }
}
