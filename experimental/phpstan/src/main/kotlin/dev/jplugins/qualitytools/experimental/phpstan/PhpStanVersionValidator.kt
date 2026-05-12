package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.php.validator.PhpToolVersionParser

/**
 * PHPStan-specific `--version` validator.
 *
 * Backed by `:php`'s [PhpToolVersionParser] with `toolName = "PHPStan"`.
 * The default regex pattern that the parser builds —
 * `PHPStan.*?(\d+\.\d+(?:\.\d+)?(?:[-+][.\w-]+)?)` — covers every
 * release-style output PHPStan has emitted:
 *
 *     PHPStan - PHP Static Analysis Tool 1.10.50      => 1.10.50
 *     PHPStan - 2.0.0-RC1                             => 2.0.0-RC1
 *     PHPStan 1.4                                      => 1.4
 *
 * PHPStan does not gate the IDE plugin on a minimum version (legacy
 * `PhpStanConfigurableForm` has no `minVersion` field — confirmed by
 * decompiling `com.jetbrains.php.tools.quality.phpstan.PhpStanConfigurableForm`),
 * so we leave `minVersion = null`.
 *
 * Port plan reference: `docs/phpstan/phpstan-port-plan.md` §4.1 (the
 * Tier-1 patch G1 surface). This file is the entire body of "PHPStan
 * version detection" sentenced in §7 step 3 of the port plan.
 *
 * Out of scope: surfacing the parsed `detectedVersion` into
 * `ResolvedBinary.detectedVersion` so [buildPhpStanArgs] can branch on
 * it (port plan gap 4.3). When the SDK wires that, no PHPStan code
 * needs to change because [PhpToolVersionParser] already returns it.
 */
public val PhpStanVersionValidator: BinaryValidator = PhpToolVersionParser(
    toolName = "PHPStan",
    minVersion = null,
)
