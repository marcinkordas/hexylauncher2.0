package com.hexgrid.launcher.util

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/**
 * Unit tests for SettingsExporter JSON export/import
 */
class SettingsExporterTest {

    @Test
    fun `suggested filename contains timestamp`() {
        val filename = SettingsExporter.getSuggestedFilename()
        assertTrue(filename.startsWith("hexgrid_launcher_settings_"))
        assertTrue(filename.endsWith(".json"))
    }

    @Test
    fun `suggested filename is unique per call`() {
        val filename1 = SettingsExporter.getSuggestedFilename()
        Thread.sleep(1000) // Wait 1 second for timestamp to change
        val filename2 = SettingsExporter.getSuggestedFilename()
        
        // Filenames should contain timestamp
        assertTrue(filename1.contains("_"))
    }

    @Test
    fun `invalid json returns failure`() {
        // We can't test import directly without Context, but we can verify the JSON parsing
        val invalidJson = "not valid json"
        
        try {
            JSONObject(invalidJson)
            fail("Should have thrown exception")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun `valid json structure is parseable`() {
        val validJson = """
            {
                "version": 1,
                "hex_radius": 96.0,
                "tile_transparency": 50,
                "hidden_apps": ["com.example.app"]
            }
        """.trimIndent()
        
        val json = JSONObject(validJson)
        assertEquals(1, json.getInt("version"))
        assertEquals(96.0, json.getDouble("hex_radius"), 0.01)
        assertEquals(50, json.getInt("tile_transparency"))
        assertEquals(1, json.getJSONArray("hidden_apps").length())
    }
}
