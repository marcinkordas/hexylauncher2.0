package com.hexgrid.launcher.ui

import com.hexgrid.launcher.data.AppInfo
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the filter algorithm used inside [LauncherViewModel.updateAppList] in isolation.
 *
 * Note: Full ViewModel integration (markUnavailable, markAvailable, reloadApps) requires
 * instrumented tests since LauncherViewModel is an AndroidViewModel. These tests cover
 * the pure filtering logic only.
 */
class AppFilterLogicTest {

    // Mockito is available (mockito-core in testImplementation).
    // Drawable is abstract — mock it to avoid Android stub RuntimeException in JVM tests.
    private fun makeApp(pkg: String, label: String) = AppInfo(
        packageName = pkg,
        label = label,
        icon = org.mockito.Mockito.mock(android.graphics.drawable.Drawable::class.java)!!,
        dominantColor = 0,
        colorBucket = 0,
        usageCount = 0L,
        lastUsedTimestamp = 0L,
        notificationCount = 0,
        isShortcut = false,
        userHandle = null
    )

    /** Simulates the filter pass inside updateAppList() */
    private fun applyFilters(
        list: List<AppInfo>,
        query: String,
        hiddenApps: Set<String>,
        unavailableApps: Set<String>
    ): List<AppInfo> {
        val queryFiltered = if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) }
        return queryFiltered.filter {
            it.packageName !in hiddenApps && it.packageName !in unavailableApps
        }
    }

    @Test
    fun `blank query returns all visible apps`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", emptySet(), emptySet())
        assertEquals(2, result.size)
    }

    @Test
    fun `query filters by label case-insensitive`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"), makeApp("c", "ALPHA two"))
        val result = applyFilters(apps, "alpha", emptySet(), emptySet())
        assertEquals(2, result.size)
        assertTrue(result.all { it.label.contains("alpha", ignoreCase = true) })
    }

    @Test
    fun `hidden apps are excluded`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", setOf("a"), emptySet())
        assertEquals(1, result.size)
        assertEquals("b", result[0].packageName)
    }

    @Test
    fun `unavailable apps are excluded`() {
        val apps = listOf(makeApp("a", "Alpha"), makeApp("b", "Beta"))
        val result = applyFilters(apps, "", emptySet(), setOf("b"))
        assertEquals(1, result.size)
        assertEquals("a", result[0].packageName)
    }

    @Test
    fun `both hidden and unavailable and query are combined`() {
        val apps = listOf(
            makeApp("a", "Alpha"),
            makeApp("b", "Beta"),
            makeApp("c", "Chrome"),
            makeApp("d", "Alpha two")
        )
        val result = applyFilters(apps, "alpha", setOf("d"), setOf("a"))
        assertEquals(0, result.size)
    }

    @Test
    fun `filtering with unavailable set excludes restricted packages`() {
        val unavailable = mutableSetOf<String>()
        unavailable.addAll(listOf("com.samsung.restricted"))
        val apps = listOf(makeApp("com.samsung.restricted", "App A"), makeApp("b", "App B"))
        val result = applyFilters(apps, "", emptySet(), unavailable)
        assertEquals(1, result.size)
        assertEquals("b", result[0].packageName)
    }

    @Test
    fun `removing from unavailable set restores those packages to visible`() {
        val unavailable = mutableSetOf("com.pkg.a", "com.pkg.b")
        unavailable.removeAll(setOf("com.pkg.a"))
        assertFalse(unavailable.contains("com.pkg.a"))
        assertTrue(unavailable.contains("com.pkg.b"))
    }
}
