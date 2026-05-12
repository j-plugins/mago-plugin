package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.ValidationResult
import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Validates the output of `pint --version`.
 *
 * Real outputs we have observed:
 *
 *     Laravel Pint v1.13.6
 *     Laravel Pint v1.13.6 by Nuno Maduro and Laravel.
 *     Laravel Pint  1.10.0
 *
 * The capture group pulls the `MAJOR.MINOR[.PATCH]` segment after an
 * optional `v` prefix.
 *
 * Pattern is slightly tighter than the legacy plugin's `Pint.* ([\d.]*)`:
 * we require the full `Laravel Pint` marker to avoid false-positives
 * on unrelated output that happens to contain the word "Pint".
 *
 * Singleton: the wrapped [PhpToolVersionParser] is not `open` so we
 * compose rather than subclass — same pattern as
 * `PhpCsFixerVersionValidator`.
 */
public object LaravelPintVersionValidator : BinaryValidator {

    public const val TOOL_NAME: String = "Laravel Pint"
    public const val VERSION_PATTERN: String =
        """Laravel Pint\s+v?(\d+\.\d+(?:\.\d+)?)"""

    private val parser: PhpToolVersionParser = PhpToolVersionParser(
        toolName = TOOL_NAME,
        versionPattern = VERSION_PATTERN,
    )

    override val versionArgs: List<String> = parser.versionArgs

    override fun validate(versionOutput: String): ValidationResult =
        parser.validate(versionOutput)
}
