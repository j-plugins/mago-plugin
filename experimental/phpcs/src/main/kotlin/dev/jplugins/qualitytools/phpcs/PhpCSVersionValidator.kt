package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.ValidationResult
import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Validates a `phpcs --version` invocation.
 *
 * Wraps the bundled [PhpToolVersionParser] with the phpcs-specific
 * regex and minimum-version policy:
 *
 *  - The output reads `PHP_CodeSniffer version <semver> by Squiz`
 *    on every supported phpcs build — the version literal sits
 *    after the word `version `.
 *  - phpcs < 1.5.0 cannot emit checkstyle XML in the shape the
 *    legacy `PhpCSXmlMessageProcessor` (and our future
 *    `CheckstyleXmlReader`) expect, so anything older is a hard
 *    error. This is the SDK's first tool with a minimum-version
 *    gate.
 */
public object PhpCSVersionValidator : BinaryValidator {

    private val delegate = PhpToolVersionParser(
        toolName = "PHP_CodeSniffer",
        versionPattern = """version\s+(\d+\.\d+(?:\.\d+)?)""",
        minVersion = "1.5.0",
    )

    override val versionArgs: List<String> = listOf("--version")

    override fun validate(versionOutput: String): ValidationResult =
        delegate.validate(versionOutput)
}
