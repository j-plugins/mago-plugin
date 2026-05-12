package dev.jplugins.qualitytools.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeLegacyInspectionElementTest {

    @Test
    fun `field returns the seeded value`() {
        val e = FakeLegacyInspectionElement(mapOf("FULL_PROJECT" to "true", "level" to "8"))
        assertEquals("true", e.field("FULL_PROJECT"))
        assertEquals("8", e.field("level"))
    }

    @Test
    fun `field returns null for missing key`() {
        val e = FakeLegacyInspectionElement(emptyMap())
        assertNull(e.field("not-set"))
    }

    @Test
    fun `field is case-sensitive`() {
        val e = FakeLegacyInspectionElement(mapOf("FULL_PROJECT" to "true"))
        assertNull(e.field("full_project"))
        assertEquals("true", e.field("FULL_PROJECT"))
    }
}
