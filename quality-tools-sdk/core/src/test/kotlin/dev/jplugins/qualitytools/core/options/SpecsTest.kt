package dev.jplugins.qualitytools.core.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecsTest {

    @Test
    fun `BoolSpec encodes and decodes lenient strings`() {
        val spec = bool("debug", default = false)
        assertEquals("true", spec.encode(true))
        assertEquals(true, spec.decode("true"))
        assertEquals(true, spec.decode("yes"))
        assertEquals(true, spec.decode("1"))
        assertEquals(false, spec.decode("no"))
        assertNull(spec.decode("maybe"))
        assertFalse(spec.isPath)
    }

    @Test
    fun `IntSpec respects range`() {
        val spec = int("level", default = 0, range = 0..8)
        assertEquals(5, spec.decode("5"))
        assertNull(spec.decode("9"))
        assertNull(spec.decode("nope"))
    }

    @Test
    fun `StringSpec is straight-through`() {
        val spec = string("config")
        assertEquals("phpstan.neon", spec.encode("phpstan.neon"))
        assertEquals("phpstan.neon", spec.decode("phpstan.neon"))
    }

    @Test
    fun `PathSpec marks itself as path`() {
        val spec = path("config")
        assertTrue(spec.isPath)
    }
}
