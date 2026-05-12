package dev.jplugins.qualitytools.experimental.psalm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world output strings sourced from `psalm --version` releases.
 *
 * The strings are intentionally hard-pinned in this test so a regex
 * regression fails loudly (matches the style of
 * `PhpToolVersionParserTest`).
 */
class PsalmVersionValidatorTest {

    private val validator = PsalmVersionValidator.create()

    @Test
    fun `recognises Psalm 5_15_0 with git-hash suffix`() {
        val r = validator.validate("Psalm 5.15.0@a1b2c3")
        assertTrue("must parse a tagged Psalm 5 release", r.ok)
        assertEquals("5.15.0", r.detectedVersion)
        assertTrue("message contains tool name", "Psalm" in r.message)
        assertTrue("message indicates ok", "OK" in r.message)
    }

    @Test
    fun `recognises Psalm 4_30_0`() {
        val r = validator.validate("Psalm 4.30.0")
        assertTrue(r.ok)
        assertEquals("4.30.0", r.detectedVersion)
    }

    @Test
    fun `rejects output that does not contain the tool name`() {
        val r = validator.validate("Some unrelated text 1.2.3")
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
    fun `defaults to --version probe`() {
        assertEquals(listOf("--version"), validator.versionArgs)
    }

    @Test
    fun `dev-master variant falls back to cannot-determine-version`() {
        // The legacy plugin special-cased `dev-master` via a
        // separate branch. The SDK version parser currently treats
        // it as unparseable; this test pins that behaviour so a
        // future re-introduction of the carve-out (TODO.md) is
        // visible.
        val r = validator.validate("Psalm dev-master")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `tool name from validator object matches Psalm`() {
        assertEquals("Psalm", PsalmVersionValidator.TOOL_NAME)
    }

    @Test
    fun `version pattern captures only the leading semver`() {
        // Belt-and-braces — the regex must not greedily slurp into
        // the git-hash suffix.
        val r = validator.validate("Psalm 3.18.2@b3c4d5e6")
        assertTrue(r.ok)
        assertEquals("3.18.2", r.detectedVersion)
    }
}
