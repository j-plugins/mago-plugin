package dev.jplugins.qualitytools.phpcs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCSVersionValidatorTest {

    @Test
    fun `versionArgs defaults to --version`() {
        assertEquals(listOf("--version"), PhpCSVersionValidator.versionArgs)
    }

    @Test
    fun `phpcs 1_4_9 is rejected as below the minimum 1_5_0`() {
        val r = PhpCSVersionValidator.validate("PHP_CodeSniffer version 1.4.9 by Squiz")
        assertFalse("1.4.9 is below 1.5.0", r.ok)
        assertEquals("1.4.9", r.detectedVersion)
        assertTrue("error mentions the required min", "1.5.0" in r.message)
    }

    @Test
    fun `phpcs 1_5_0 exact match is accepted`() {
        val r = PhpCSVersionValidator.validate("PHP_CodeSniffer version 1.5.0 by Squiz")
        assertTrue("1.5.0 is the floor; must be ok", r.ok)
        assertEquals("1.5.0", r.detectedVersion)
    }

    @Test
    fun `phpcs 3_7_2 newer is accepted`() {
        val r = PhpCSVersionValidator.validate("PHP_CodeSniffer version 3.7.2 by Squiz")
        assertTrue(r.ok)
        assertEquals("3.7.2", r.detectedVersion)
    }

    @Test
    fun `output missing the PHP_CodeSniffer marker is rejected`() {
        val r = PhpCSVersionValidator.validate("Some unrelated CLI 1.2.3")
        assertFalse(r.ok)
    }
}
