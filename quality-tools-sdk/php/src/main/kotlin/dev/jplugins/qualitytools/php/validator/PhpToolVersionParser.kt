package dev.jplugins.qualitytools.php.validator

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.SimpleValidationResult
import dev.jplugins.qualitytools.core.tool.ValidationResult

/**
 * Replaces ~600 LOC of duplicated `extractVersion` / `validateMessage`
 * glue across the six legacy PHP quality-tool plugins
 * (promotion-analysis.md §3.7).
 *
 * Usage:
 *
 *     val phpstanValidator = PhpToolVersionParser(
 *         toolName = "PHPStan",
 *         minVersion = null,
 *     )
 *     val psalmValidator = PhpToolVersionParser(
 *         toolName = "Psalm",
 *         versionPattern = """Psalm\s+(\d+\.\d+(?:\.\d+)?)""",
 *     )
 *     val phpcsValidator = PhpToolVersionParser(
 *         toolName = "PHP_CodeSniffer",
 *         versionPattern = """version\s+(\d+\.\d+(?:\.\d+)?)""",
 *         minVersion = "1.5.0",
 *     )
 *
 * The default [versionPattern] matches every tool whose `--version`
 * output is of the form `<toolName>.*<semver>`; concrete tools override
 * the pattern only when their output deviates.
 *
 * @property toolName Human-readable name; used in the "OK, <toolName>
 *   <version>" success message AND must appear in the tool's
 *   `--version` output. Case-sensitive.
 * @property versionPattern Regex with one capturing group for the
 *   version string. Default is `<toolName>.*?(\d+\.\d+(\.\d+)?)`.
 * @property minVersion Optional minimum acceptable semver string;
 *   format `"1.5.0"`. When set and parsed version is less, returns
 *   `ok=false`.
 */
public class PhpToolVersionParser(
    public val toolName: String,
    public val versionPattern: String =
        "${Regex.escape(toolName)}.*?(\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][.\\w-]+)?)",
    public val minVersion: String? = null,
) : BinaryValidator {

    private val regex: Regex = Regex(versionPattern)
    private val parsedMin: SemVer? = minVersion?.let(SemVer::parseOrNull)

    override fun validate(versionOutput: String): ValidationResult {
        val trimmed = versionOutput.trim()
        if (toolName !in trimmed) {
            return SimpleValidationResult(
                ok = false,
                message = "Cannot determine version: $trimmed",
            )
        }
        val match = regex.find(trimmed) ?: return SimpleValidationResult(
            ok = false,
            message = "Cannot determine version: $trimmed",
        )
        val version = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: return SimpleValidationResult(
                ok = false,
                message = "Cannot determine version: $trimmed",
            )
        if (parsedMin != null) {
            val parsed = SemVer.parseOrNull(version)
            if (parsed == null || parsed < parsedMin) {
                return SimpleValidationResult(
                    ok = false,
                    message = "$toolName $version is older than required $minVersion",
                    detectedVersion = version,
                )
            }
        }
        return SimpleValidationResult(
            ok = true,
            message = "OK, $toolName $version",
            detectedVersion = version,
        )
    }
}

/**
 * Minimal semver comparison for version-gating. Pre-release and build
 * metadata are accepted but ignored in comparison.
 *
 * Visible to the package so tests can construct values directly.
 */
internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        return patch - other.patch
    }

    companion object {
        private val RE = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
        fun parseOrNull(text: String): SemVer? {
            val m = RE.find(text) ?: return null
            val major = m.groupValues[1].toIntOrNull() ?: return null
            val minor = m.groupValues[2].toIntOrNull() ?: return null
            val patch = m.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
            return SemVer(major, minor, patch)
        }
    }
}
