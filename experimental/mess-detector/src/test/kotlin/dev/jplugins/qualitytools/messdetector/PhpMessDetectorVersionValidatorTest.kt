package dev.jplugins.qualitytools.messdetector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real `phpmd --version` outputs taken from upstream tarballs:
 *
 *     PHPMD 2.14.1
 *     PHPMD 2.15.0
 *
 * The legacy plugin's `MessDetectorConfigurableForm` accepts anything
 * starting with `PHPMD`; the regex here is tighter (requires a
 * semver-shaped number after) so that "OK, PHPMD <version>" can be
 * shown to the user.
 */
class PhpMessDetectorVersionValidatorTest {

    private val validator = PhpMessDetectorVersionValidator

    @Test
    fun `PHPMD 2_14_1 output is recognised`() {
        val r = validator.validate("PHPMD 2.14.1")
        assertTrue(r.ok)
        assertEquals("2.14.1", r.detectedVersion)
        assertTrue(r.message.contains("OK"))
        assertTrue(r.message.contains("PHPMD"))
    }

    @Test
    fun `PHPMD 2_15_0 output is recognised`() {
        val r = validator.validate("PHPMD 2.15.0")
        assertTrue(r.ok)
        assertEquals("2.15.0", r.detectedVersion)
    }

    @Test
    fun `PHPMD output with trailing whitespace and newline is recognised`() {
        val r = validator.validate("PHPMD 2.14.1\n")
        assertTrue(r.ok)
        assertEquals("2.14.1", r.detectedVersion)
    }

    @Test
    fun `PHPMD two-segment version is accepted`() {
        // Defensive: some packagers / dev-builds drop the patch.
        val r = validator.validate("PHPMD 2.15")
        assertTrue(r.ok)
        assertEquals("2.15", r.detectedVersion)
    }

    @Test
    fun `non-phpmd output is rejected`() {
        val r = validator.validate("PHPStan - PHP Static Analysis Tool 1.10.50")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `empty output is rejected`() {
        val r = validator.validate("")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `whitespace-only output is rejected`() {
        val r = validator.validate("   \n\t  ")
        assertFalse(r.ok)
    }

    @Test
    fun `versionArgs default is --version`() {
        assertEquals(listOf("--version"), validator.versionArgs)
    }
}
