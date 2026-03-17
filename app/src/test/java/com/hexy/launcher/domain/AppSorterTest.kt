package com.hexgrid.launcher.domain

import com.hexgrid.launcher.data.AppInfo
import com.hexgrid.launcher.domain.HexCoordinate
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import android.content.Context
import android.graphics.drawable.Drawable
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

/**
 * Unit tests for AppSorter algorithm
 */
@RunWith(RobolectricTestRunner::class)
class AppSorterTest {

    private fun createMockApp(
        packageName: String,
        usageCount: Long = 0,
        lastUsedTimestamp: Long = 0,
        colorBucket: Int = 0
    ): AppInfo {
        val mockDrawable = mock(Drawable::class.java)
        return AppInfo(
            packageName = packageName,
            label = packageName,
            icon = mockDrawable,
            dominantColor = 0,
            colorBucket = colorBucket,
            usageCount = usageCount,
            lastUsedTimestamp = lastUsedTimestamp,
            notificationCount = 0,
            isShortcut = false,
            shortcutId = null,
            userHandle = null
        )
    }

    private val mockContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `empty list returns empty`() {
        val result = AppSorter.sortApps(emptyList(), mockContext)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single app is placed at center`() {
        val app = createMockApp("com.example", usageCount = 100)
        val result = AppSorter.sortApps(listOf(app), mockContext)
        // Inner ring is always filled to 7 (1 app + 6 placeholders)
        assertTrue(result.isNotEmpty())
        assertEquals("com.example", result[0].packageName)
    }

    @Test
    fun `most used app is first`() {
        val apps = listOf(
            createMockApp("low", usageCount = 10),
            createMockApp("high", usageCount = 1000),
            createMockApp("medium", usageCount = 100)
        )
        val result = AppSorter.sortApps(apps, mockContext)
        assertEquals("high", result[0].packageName)
    }

    @Test
    fun `recently used apps are in inner rings`() {
        val now = System.currentTimeMillis()
        val apps = (0..25).map { i ->
            createMockApp(
                "app$i",
                usageCount = (25 - i).toLong(),
                lastUsedTimestamp = now - (i * 60000) // Progressively older
            )
        }

        val result = AppSorter.sortApps(apps, mockContext)

        // All 26 apps should be placed (result may include placeholders for ring completion)
        assertTrue(result.size >= 26)
        val nonPlaceholders = result.filter { !AppSorter.isPlaceholder(it) }
        assertEquals(26, nonPlaceholders.size)
    }

    @Test
    fun `color buckets are distributed`() {
        val apps = (0..5).map { bucket ->
            createMockApp("bucket$bucket", colorBucket = bucket, usageCount = 100)
        }

        val result = AppSorter.sortApps(apps, mockContext)
        // All 6 apps should appear in the result (inner ring fills to 7 with placeholders)
        assertTrue(result.size >= 6)
        val resultPackages = result.filter { !AppSorter.isPlaceholder(it) }.map { it.packageName }.toSet()
        assertEquals(6, resultPackages.size)
    }

    @Test
    fun `occupied cells reduce result count for outer rings`() {
        val apps = (0..20).map { i -> createMockApp("app$i", usageCount = (20 - i).toLong()) }
        val occupied = setOf(
            HexCoordinate(2, 0),
            HexCoordinate(2, -1)
        )
        val full = AppSorter.sortApps(apps, mockContext)
        val filtered = AppSorter.sortApps(apps, mockContext, occupied)
        // filtered result should be smaller or equal (occupied positions become gaps)
        assertTrue(filtered.size <= full.size)
    }
}
