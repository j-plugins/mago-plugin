package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Composer auto-detect descriptor for PHP Mess Detector.
 *
 * The package is `phpmd/phpmd`; the vendored binary is
 * `vendor/bin/phpmd` (handled generically by the SDK's
 * `ComposerBinarySourceType`).
 *
 * Config-file discovery prefers `phpmd.xml` over `phpmd.xml.dist`
 * (a project's local file wins over the committed dist fallback) —
 * legacy parity with `MessDetectorComposerConfig.applyRulesetFromRoot`.
 *
 * The script key is `phpmd`, matching the convention popularised by
 * the package's own `composer.json`. No script-arg extraction is
 * configured today: the legacy plugin parses `scripts.phpmd` for the
 * positional ruleset CSV (mapping known short-names to checkboxes
 * and unknown ones to custom-ruleset rows), but that logic is
 * heterogeneous (CSV-of-mixed-tokens routed onto N different option
 * specs) and doesn't fit `ComposerToolDescriptor.FlagToOption`. The
 * future `PhpMessDetectorComposerOnDetectedHook` (port plan §3
 * `MessDetectorComposerConfig` row) will own that mapping; see
 * `TODO.md`.
 */
public object PhpMessDetectorComposerToolDescriptor : ComposerToolDescriptor(
    packageName = "phpmd/phpmd",
    binName = "phpmd",
    configFileNames = listOf("phpmd.xml", "phpmd.xml.dist"),
    scriptKey = "phpmd",
    scriptArgs = emptyList(),
)
