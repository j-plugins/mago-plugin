package dev.jplugins.qualitytools.laravelpint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hard-pins exact `pint --version` strings we have observed in the
 * wild (and a couple of pathological cases). Any regression in parsing
 * fails loudly here.
 */
class LaravelPintVersionValidatorTest {

    private val validator = LaravelPintVersionValidator()

    @Test
    fun `recognises plain Laravel Pint v1_13_6 output`() {
        val r = validator.validate("Laravel Pint v1.13.6")
        assertTrue(r.ok)
        assertEquals("1.13.6", r.detectedVersion)
        assertTrue("success message names the tool", "Laravel Pint" in r.message)
        assertTrue("success message starts with OK", r.message.startsWith("OK"))
    }

    @Test
    fun `recognises version without leading v`() {
        val r = validator.validate("Laravel Pint  1.10.0")
        assertTrue(r.ok)
        assertEquals("1.10.0", r.detectedVersion)
    }

    @Test
    fun `recognises a major-minor only version`() {
        val r = validator.validate("Laravel Pint v1.13")
        assertTrue(r.ok)
        assertEquals("1.13", r.detectedVersion)
    }

    @Test
    fun `recognises the full banner shown by Pint 1_x`() {
        val r = validator.validate(
            "Laravel Pint v1.13.6 by Nuno Maduro and Laravel.",
        )
        assertTrue(r.ok)
        assertEquals("1.13.6", r.detectedVersion)
    }

    @Test
    fun `rejects output that lacks the Laravel Pint marker`() {
        val r = validator.validate("Pint 1.13.6")
        // Our tighter regex requires the full "Laravel Pint" prefix
        // (the legacy plugin matched just "Pint.*"; we deliberately
        // tightened so that random text with "Pint" in it doesn't
        // false-positive).
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `rejects empty output`() {
        val r = validator.validate("")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `rejects whitespace-only output`() {
        val r = validator.validate("   \n\t  ")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `default versionArgs is --version`() {
        assertEquals(listOf("--version"), validator.versionArgs)
    }

    @Test
    fun `pattern constant matches the spec text verbatim`() {
        // Pinning this string guards against accidental edits — the
        // pattern is documented in the port plan and reused by
        // anything that wants to re-parse Pint version output.
        assertEquals(
            """Laravel Pint\s+v?(\d+\.\d+(?:\.\d+)?)""",
            LaravelPintVersionValidator.VERSION_PATTERN,
        )
        assertEquals("Laravel Pint", LaravelPintVersionValidator.TOOL_NAME)
    }
}
