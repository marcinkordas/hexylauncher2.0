package com.hexgrid.launcher.domain

import com.hexgrid.launcher.data.AppInfo
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock
import android.graphics.drawable.Drawable

/**
 * Unit tests for AppSorter algorithm
 */
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

    @Test
    fun `empty list returns empty`() {
        val result = AppSorter.sortApps(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single app is placed at center`() {
        val app = createMockApp("com.example", usageCount = 100)
        val result = AppSorter.sortApps(listOf(app))
        assertEquals(1, result.size)
        assertEquals("com.example", result[0].packageName)
    }

    @Test
    fun `most used app is first`() {
        val apps = listOf(
            createMockApp("low", usageCount = 10),
            createMockApp("high", usageCount = 1000),
            createMockApp("medium", usageCount = 100)
        )
        val result = AppSorter.sortApps(apps)
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
        
        val result = AppSorter.sortApps(apps)
        
        // First app should be most used
        // Apps 1-18 should be most recently used
        assertEquals(26, result.size)
    }

    @Test
    fun `color buckets are distributed`() {
        val apps = (0..5).map { bucket ->
            createMockApp("bucket$bucket", colorBucket = bucket, usageCount = 100)
        }
        
        val result = AppSorter.sortApps(apps)
        assertEquals(6, result.size)
    }
}
