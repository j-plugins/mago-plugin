package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Composer-discovery descriptor for Psalm.
 *
 * Replaces the legacy `PsalmComposerConfig` (~237 LOC at
 * `/tmp/decomp/com/jetbrains/php/tools/quality/psalm/PsalmComposerConfig.java`)
 * with a one-shot data declaration consumed by the future
 * `ComposerBinarySourceType` in `:php`.
 *
 *  - `packageName = "vimeo/psalm"` matches Composer's published name.
 *  - `binName = "psalm"` matches `vendor/bin/psalm`.
 *  - `configFileNames` lists the two-name fallback documented in
 *    `docs/psalm/psalm-port-plan.md` §3b: prefer `psalm.xml`, fall
 *    back to `psalm.xml.dist`.
 *  - `scriptKey = "psalm"` is the conventional Composer-script entry
 *    for the tool.
 *  - `scriptArgs` is **empty for now** — unlike PHPStan, Psalm does
 *    not use `--memory-limit` in `composer.json scripts` conventionally,
 *    and the legacy plugin's parser carries no flag→option mappings.
 *    We can expand this later (e.g. add `--config=` script-arg
 *    extraction) without a schema change.
 */
public object PsalmComposerToolDescriptor {

    public const val PACKAGE_NAME: String = "vimeo/psalm"
    public const val BIN_NAME: String = "psalm"
    public const val SCRIPT_KEY: String = "psalm"
    public const val CONFIG_FILE_PRIMARY: String = "psalm.xml"
    public const val CONFIG_FILE_DIST: String = "psalm.xml.dist"

    public val DESCRIPTOR: ComposerToolDescriptor = ComposerToolDescriptor(
        packageName = PACKAGE_NAME,
        binName = BIN_NAME,
        configFileNames = listOf(CONFIG_FILE_PRIMARY, CONFIG_FILE_DIST),
        scriptKey = SCRIPT_KEY,
        scriptArgs = emptyList(),
    )
}
