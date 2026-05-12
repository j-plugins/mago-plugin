package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * Validates the output of `phpmd --version`.
 *
 * Real outputs look like:
 *
 *     PHPMD 2.14.1
 *     PHPMD 2.15.0
 *
 * The capture group pulls the leading `MAJOR.MINOR[.PATCH]` segment.
 *
 * No minimum-version gate is enforced — the legacy
 * `MessDetectorConfigurableForm` simply accepts anything starting
 * with `PHPMD` (port plan §1.1: "Validation regex: anything starting
 * with `PHPMD` is OK"). A future per-tool min-version override
 * (gap **G15** in the php-cs-fixer plan) could move the floor up
 * once phpmd's CI matrix is documented.
 */
public object PhpMessDetectorVersionValidator : PhpToolVersionParser(
    toolName = "PHPMD",
    versionPattern = """PHPMD\s+(\d+\.\d+(?:\.\d+)?)""",
)
