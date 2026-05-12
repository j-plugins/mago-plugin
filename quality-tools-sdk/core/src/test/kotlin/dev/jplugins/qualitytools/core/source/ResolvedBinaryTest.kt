package dev.jplugins.qualitytools.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ResolvedBinaryTest {

    @Test
    fun `SimpleResolvedBinary defaults are conservative`() {
        val rb = SimpleResolvedBinary(command = listOf("phpstan"))
        assertEquals(listOf("phpstan"), rb.command)
        assertNull(rb.workingDir)
        assertEquals(emptyMap<String, String>(), rb.env)
        assertSame(PathMapper.Identity, rb.pathMapper)
        kotlin.test.assertTrue(rb.supportsStdin)
        assertNull(rb.detectedVersion)
    }

    @Test
    fun `SimpleResolvedBinary carries all fields when provided`() {
        val customMapper = object : PathMapper {}
        val rb = SimpleResolvedBinary(
            command = listOf("docker", "exec", "mago"),
            workingDir = "/proj",
            env = mapOf("XDEBUG_MODE" to "off"),
            pathMapper = customMapper,
            supportsStdin = false,
            detectedVersion = "1.2.3",
        )
        assertEquals(listOf("docker", "exec", "mago"), rb.command)
        assertEquals("/proj", rb.workingDir)
        assertEquals(mapOf("XDEBUG_MODE" to "off"), rb.env)
        assertSame(customMapper, rb.pathMapper)
        kotlin.test.assertFalse(rb.supportsStdin)
        assertEquals("1.2.3", rb.detectedVersion)
    }

    @Test
    fun `interface default for detectedVersion is null`() {
        val rb: ResolvedBinary = object : ResolvedBinary {
            override val command = listOf("x")
        }
        assertNull(rb.detectedVersion)
        assertNull(rb.workingDir)
        kotlin.test.assertTrue(rb.supportsStdin) // default true
        assertSame(PathMapper.Identity, rb.pathMapper)
    }
}
