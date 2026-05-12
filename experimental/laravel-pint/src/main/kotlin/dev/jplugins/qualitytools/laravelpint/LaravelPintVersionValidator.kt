package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.ValidationResult
import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Binary validator for the "Validate" button next to the Pint tool-path
 * field.
 *
 * Real `pint --version` outputs we have observed:
 *   "Laravel Pint v1.13.6"
 *   "Laravel Pint  1.10.0"
 *
 * Pattern requires `Laravel Pint` followed by optional `v`, then a
 * `major.minor(.patch)?` triple — slightly tighter than the legacy
 * plugin's `Pint.* ([\d.]*)` regex so we don't false-positive on
 * unrelated strings containing "Pint".
 */
public class LaravelPintVersionValidator : BinaryValidator {

    private val parser: PhpToolVersionParser = PhpToolVersionParser(
        toolName = TOOL_NAME,
        versionPattern = VERSION_PATTERN,
    )

    override val versionArgs: List<String> get() = parser.versionArgs

    override fun validate(versionOutput: String): ValidationResult =
        parser.validate(versionOutput)

    public companion object {
        public const val TOOL_NAME: String = "Laravel Pint"
        public const val VERSION_PATTERN: String =
            """Laravel Pint\s+v?(\d+\.\d+(?:\.\d+)?)"""
    }
}
