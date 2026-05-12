package dev.jplugins.qualitytools.experimental.phpstan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-output-string assertions for the PHPStan `--version` parser.
 *
 * Strings are sourced from PHPStan releases verified manually:
 *
 *  - 1.10.50 — current stable as of the legacy port snapshot.
 *  - 2.0.0-RC1 — first release-candidate of the 2.x line; the
 *    `SemVer`/regex must capture the `-RC1` suffix.
 *
 * The validator must reject any output that doesn't contain the
 * literal "PHPStan" token — this is what distinguishes a real PHPStan
 * binary from e.g. a misplaced `phpcs` symlink.
 */
class PhpStanVersionValidatorTest {

    @Test
    fun `phpstan 1_10_50 output is accepted with detected version`() {
        val r = PhpStanVersionValidator.validate("PHPStan 1.10.50")
        assertTrue("ok", r.ok)
        assertEquals("1.10.50", r.detectedVersion)
        assertTrue(
            "success message names the tool and version",
            r.message.contains("PHPStan") && r.message.contains("1.10.50"),
        )
    }

    @Test
    fun `phpstan 2_0_0-RC1 output is accepted and pre-release suffix is captured`() {
        val r = PhpStanVersionValidator.validate("PHPStan 2.0.0-RC1")
        assertTrue("ok", r.ok)
        assertEquals("2.0.0-RC1", r.detectedVersion)
    }

    @Test
    fun `garbage output is rejected`() {
        val r = PhpStanVersionValidator.validate("Some random text")
        assertFalse("must not pass validation", r.ok)
        assertNull(r.detectedVersion)
    }

    @Test
    fun `versionArgs default is --version`() {
        assertEquals(listOf("--version"), PhpStanVersionValidator.versionArgs)
    }

    @Test
    fun `validator accepts the full release banner shape`() {
        // The actual --version output from a vendor/bin/phpstan binary.
        val r = PhpStanVersionValidator.validate(
            "PHPStan - PHP Static Analysis Tool 1.10.50\n\n" +
                "Copyright (c) PHPStan contributors\n",
        )
        assertTrue(r.ok)
        assertEquals("1.10.50", r.detectedVersion)
    }

    @Test
    fun `empty output is rejected`() {
        val r = PhpStanVersionValidator.validate("")
        assertFalse(r.ok)
        assertNull(r.detectedVersion)
    }
}
