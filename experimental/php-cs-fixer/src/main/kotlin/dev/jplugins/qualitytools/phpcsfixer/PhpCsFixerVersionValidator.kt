package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.ValidationResult
import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Validates the output of `php-cs-fixer --version`.
 *
 * Legacy outputs look like:
 *
 *     PHP CS Fixer 3.50.0 by Fabien Potencier and contributors.
 *     PHP CS Fixer 2.16.4 by Fabien Potencier and contributors. (PHP 7.4.0)
 *
 * The capture group pulls the leading `MAJOR.MINOR[.PATCH]` segment.
 *
 * No minimum-version gate is enforced here — historically the legacy
 * plugin hard-coded a 2.8.0 floor, but we let the schema decide that
 * once gap **G15** (per-tool min-version override) lands. For now the
 * parser just reports the detected version and `ok=true` when the
 * `PHP CS Fixer` magic string is present.
 *
 * Wraps a configured [PhpToolVersionParser]. Singleton pattern is
 * preferred over `object : PhpToolVersionParser(...)` because the
 * underlying class is not `open`.
 */
public object PhpCsFixerVersionValidator : BinaryValidator {

    private val parser: PhpToolVersionParser = PhpToolVersionParser(
        toolName = "PHP CS Fixer",
        versionPattern = """PHP CS Fixer.*?(\d+\.\d+(?:\.\d+)?)""",
    )

    override val versionArgs: List<String> = parser.versionArgs

    override fun validate(versionOutput: String): ValidationResult =
        parser.validate(versionOutput)
}
