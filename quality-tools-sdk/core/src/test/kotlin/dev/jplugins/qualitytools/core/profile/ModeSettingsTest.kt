package dev.jplugins.qualitytools.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModeSettingsTest {

    @Test
    fun `interface defaults are enabled true and empty additionalArgs`() {
        val ms: ModeSettings = object : ModeSettings {}
        kotlin.test.assertTrue(ms.enabled)
        assertEquals("", ms.additionalArgs)
        assertNull(ms.customConfigFile)
    }

    @Test
    fun `SimpleModeSettings carries all fields`() {
        val ms = SimpleModeSettings(
            enabled = false,
            additionalArgs = "--no-progress --xdebug",
            customConfigFile = "/proj/custom.neon",
        )
        kotlin.test.assertFalse(ms.enabled)
        assertEquals("--no-progress --xdebug", ms.additionalArgs)
        assertEquals("/proj/custom.neon", ms.customConfigFile)
    }

    @Test
    fun `SimpleModeSettings defaults`() {
        val ms = SimpleModeSettings()
        kotlin.test.assertTrue(ms.enabled)
        assertEquals("", ms.additionalArgs)
        assertNull(ms.customConfigFile)
    }

    @Test
    fun `data class equality and copy semantics`() {
        val a = SimpleModeSettings(additionalArgs = "x")
        val b = SimpleModeSettings(additionalArgs = "x")
        val c = a.copy(additionalArgs = "y")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }
}
