package dev.jplugins.qualitytools.php.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-output-string assertions sourced from the legacy plugins'
 * `validateMessage` test fixtures. We hard-pin the exact strings the
 * tools print, so a regression in parsing fails here loudly.
 */
class PhpToolVersionParserTest {

    @Test
    fun `phpstan version output is recognised`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        val r = parser.validate("PHPStan - PHP Static Analysis Tool 1.10.50\n\nblah")
        assertTrue(r.ok)
        assertEquals("1.10.50", r.detectedVersion)
        assertTrue(r.message.contains("OK"))
        assertTrue(r.message.contains("PHPStan"))
    }

    @Test
    fun `phpstan pre-release version is captured`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        val r = parser.validate("PHPStan - 2.0.0-RC1")
        assertTrue(r.ok)
        assertEquals("2.0.0-RC1", r.detectedVersion)
    }

    @Test
    fun `psalm version output is recognised with custom pattern`() {
        val parser = PhpToolVersionParser(
            toolName = "Psalm",
            versionPattern = """Psalm.*?(\d+\.\d+(?:\.\d+)?)""",
        )
        val r = parser.validate("Psalm 5.15.0@a1b2c3")
        assertTrue(r.ok)
        assertEquals("5.15.0", r.detectedVersion)
    }

    @Test
    fun `phpcs requires minimum version 1_5_0`() {
        val parser = PhpToolVersionParser(
            toolName = "PHP_CodeSniffer",
            versionPattern = """version\s+(\d+\.\d+(?:\.\d+)?)""",
            minVersion = "1.5.0",
        )
        val r = parser.validate("PHP_CodeSniffer version 1.4.9 by Squiz")
        assertFalse(r.ok)
        assertEquals("1.4.9", r.detectedVersion)
        assertTrue("error names required min", "1.5.0" in r.message)
    }

    @Test
    fun `phpcs accepts an exact min-version match`() {
        val parser = PhpToolVersionParser(
            toolName = "PHP_CodeSniffer",
            versionPattern = """version\s+(\d+\.\d+(?:\.\d+)?)""",
            minVersion = "1.5.0",
        )
        val r = parser.validate("PHP_CodeSniffer version 1.5.0 by Squiz")
        assertTrue(r.ok)
        assertEquals("1.5.0", r.detectedVersion)
    }

    @Test
    fun `phpcs accepts a newer version`() {
        val parser = PhpToolVersionParser(
            toolName = "PHP_CodeSniffer",
            versionPattern = """version\s+(\d+\.\d+(?:\.\d+)?)""",
            minVersion = "1.5.0",
        )
        val r = parser.validate("PHP_CodeSniffer version 3.7.2 by Squiz")
        assertTrue(r.ok)
        assertEquals("3.7.2", r.detectedVersion)
    }

    @Test
    fun `tool name mismatch is rejected`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        val r = parser.validate("Some random text that just has 1.2.3 in it")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
        assertTrue("error includes the offending text", "random text" in r.message)
    }

    @Test
    fun `empty output is rejected`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        val r = parser.validate("")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `whitespace-only output is rejected`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        val r = parser.validate("   \n\t  ")
        assertFalse(r.ok)
    }

    @Test
    fun `versionArgs default is --version`() {
        val parser = PhpToolVersionParser(toolName = "PHPStan")
        assertEquals(listOf("--version"), parser.versionArgs)
    }

    @Test
    fun `SemVer comparison covers major minor patch ordering`() {
        val a = SemVer.parseOrNull("1.10.5")!!
        val b = SemVer.parseOrNull("1.10.6")!!
        val c = SemVer.parseOrNull("1.11.0")!!
        val d = SemVer.parseOrNull("2.0.0")!!
        assertTrue(a < b)
        assertTrue(b < c)
        assertTrue(c < d)
    }

    @Test
    fun `SemVer parses two-segment version`() {
        val v = SemVer.parseOrNull("1.5")!!
        assertEquals(1, v.major); assertEquals(5, v.minor); assertEquals(0, v.patch)
    }

    @Test
    fun `SemVer parseOrNull returns null on garbage`() {
        assertNull(SemVer.parseOrNull("not.a.version"))
    }
}
