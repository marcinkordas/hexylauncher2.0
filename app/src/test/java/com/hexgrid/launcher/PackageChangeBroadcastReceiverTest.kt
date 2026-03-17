package com.hexgrid.launcher

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the EXTRA_REPLACING debounce logic in the package change receiver.
 */
class PackageChangeBroadcastReceiverTest {

    /** Simulates the should-reload decision from the BroadcastReceiver */
    private fun shouldReload(action: String?, isReplacing: Boolean): Boolean {
        return when (action) {
            Intent.ACTION_PACKAGE_REMOVED -> !isReplacing // ignore if mid-update
            Intent.ACTION_PACKAGE_ADDED -> true           // always reload (new install OR update)
            Intent.ACTION_PACKAGE_REPLACED -> false       // skip — ADDED handles it
            else -> false
        }
    }

    @Test
    fun `real uninstall triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false))
    }

    @Test
    fun `package removed during update is ignored`() {
        assertFalse(shouldReload(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
    }

    @Test
    fun `new install triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_ADDED, isReplacing = false))
    }

    @Test
    fun `package added during update triggers reload`() {
        assertTrue(shouldReload(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
    }

    @Test
    fun `package replaced is ignored to avoid duplicate reload`() {
        assertFalse(shouldReload(Intent.ACTION_PACKAGE_REPLACED, isReplacing = false))
    }

    @Test
    fun `unknown action does not trigger reload`() {
        assertFalse(shouldReload(null, false))
        assertFalse(shouldReload("com.example.CUSTOM_ACTION", false))
    }
}
