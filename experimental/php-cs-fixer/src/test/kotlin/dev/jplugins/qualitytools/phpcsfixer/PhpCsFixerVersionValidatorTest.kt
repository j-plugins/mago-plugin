package dev.jplugins.qualitytools.phpcsfixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhpCsFixerVersionValidatorTest {

    private val validator = PhpCsFixerVersionValidator

    @Test
    fun `legacy 3_50_0 string is recognised`() {
        val r = validator.validate(
            "PHP CS Fixer 3.50.0 by Fabien Potencier and contributors.",
        )
        assertTrue(r.ok)
        assertEquals("3.50.0", r.detectedVersion)
        assertTrue(r.message.contains("OK"))
        assertTrue(r.message.contains("PHP CS Fixer"))
    }

    @Test
    fun `legacy 2_x string is recognised`() {
        val r = validator.validate(
            "PHP CS Fixer 2.16.4 by Fabien Potencier and contributors. (PHP 7.4.0)",
        )
        assertTrue(r.ok)
        assertEquals("2.16.4", r.detectedVersion)
    }

    @Test
    fun `two-segment version is accepted`() {
        // Some packagers strip the patch segment.
        val r = validator.validate("PHP CS Fixer 3.50 by Fabien Potencier")
        assertTrue(r.ok)
        assertEquals("3.50", r.detectedVersion)
    }

    @Test
    fun `output missing the magic string is rejected`() {
        val r = validator.validate("Some other tool 3.50.0")
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
    fun `versionArgs default is --version`() {
        assertEquals(listOf("--version"), validator.versionArgs)
    }
}
