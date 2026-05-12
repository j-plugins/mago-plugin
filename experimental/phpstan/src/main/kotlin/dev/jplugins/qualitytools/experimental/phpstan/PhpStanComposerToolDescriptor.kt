package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Composer auto-detection descriptor for PHPStan.
 *
 * Mirrors what the legacy `PhpStanComposerConfig` does today:
 *
 *  - matches `phpstan/phpstan` in `require` / `require-dev`
 *    (`vendor/bin/phpstan` then becomes the suggested binary path —
 *    handled by the future `ComposerBinarySourceType`, port plan gap
 *    4.5);
 *  - picks `phpstan.neon` (preferred) or `phpstan.neon.dist` next to
 *    the `composer.json` and writes it into the `config` option;
 *  - parses `scripts.phpstan` for `--memory-limit` and `--level`
 *    and writes them into the matching specs (legacy
 *    `PhpStanComposerConfig.applyInspectionSettingsFromComposer` lines
 *    ~190-240).
 *
 * Constructed as a function from a [PhpStanOptionsSchema] instance so
 * the call sites in the test and in `:php`'s future
 * `ComposerBinarySourceType.onDetected` hook see the *same* spec
 * objects as the live schema attached to [PhpStanTool] — passing
 * default-equal-but-not-identical specs would no-op the bag writes.
 *
 * Port plan reference: §6 ("Required") and §4.5
 * (`onDetected(bag)` hook gap). The hook itself is not yet implemented
 * here because it depends on phase 02; see
 * `experimental/phpstan/TODO.md` gap 4.5.
 */
public fun phpStanComposerToolDescriptor(
    schema: PhpStanOptionsSchema = PhpStanOptionsSchema(),
): ComposerToolDescriptor = ComposerToolDescriptor(
    packageName = "phpstan/phpstan",
    binName = "phpstan",
    configFileNames = listOf("phpstan.neon", "phpstan.neon.dist"),
    scriptKey = "phpstan",
    scriptArgs = listOf(
        ComposerToolDescriptor.FlagToOption(
            flag = "--memory-limit",
            spec = schema.memoryLimit,
        ),
        ComposerToolDescriptor.FlagToOption(
            flag = "--level",
            spec = schema.level,
        ),
    ),
)
