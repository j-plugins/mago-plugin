package dev.jplugins.qualitytools.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier-1 patch G1: validator contract. We don't have a real binary in
 * unit tests; we exercise the SAM directly.
 */
class BinaryValidatorTest {

    private val phpstanLike = object : BinaryValidator {
        private val versionRegex = Regex("""PHPStan.*?(\d+\.\d+(?:\.\d+)?)""")

        override fun validate(versionOutput: String): ValidationResult {
            val v = versionRegex.find(versionOutput)?.groupValues?.get(1)
            return if (v != null) {
                SimpleValidationResult(ok = true, message = "OK, PHPStan $v", detectedVersion = v)
            } else {
                SimpleValidationResult(ok = false, message = "Cannot determine version: $versionOutput")
            }
        }
    }

    @Test
    fun `valid version output is recognised`() {
        val r = phpstanLike.validate("PHPStan 1.10.50")
        assertTrue(r.ok)
        assertEquals("1.10.50", r.detectedVersion)
    }

    @Test
    fun `unrecognised output is rejected`() {
        val r = phpstanLike.validate("Some random text")
        assertFalse(r.ok)
        assertEquals(null, r.detectedVersion)
    }

    @Test
    fun `default versionArgs is --version`() {
        assertEquals(listOf("--version"), phpstanLike.versionArgs)
    }
}
