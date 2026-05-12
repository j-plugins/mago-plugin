package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.php.composer.ComposerToolDescriptor

/**
 * Declarative descriptor consumed by `ComposerBinarySourceType` (future
 * phase 02 work) to auto-detect Pint installed via Composer.
 *
 * Pint ships under `laravel/pint`; the binary is `vendor/bin/pint` and
 * the config lives next to `composer.json` as `pint.json`. Pint's
 * `scripts.pint` composer entry doesn't carry CLI args we currently
 * surface in our options schema (`--preset` / `--config` / `--dirty`
 * are tracked in TODO.md for a follow-up alongside the preset combobox
 * port), so [ComposerToolDescriptor.scriptArgs] stays empty here.
 *
 * This is a top-level `val` rather than a class with `companion object`
 * to keep its shape identical to the other Composer descriptors in the
 * SDK (cf. examples in `ComposerToolDescriptorTest`).
 */
public val LaravelPintComposerToolDescriptor: ComposerToolDescriptor =
    ComposerToolDescriptor(
        packageName = "laravel/pint",
        binName = "pint",
        configFileNames = listOf("pint.json"),
        scriptKey = "pint",
        scriptArgs = emptyList(),
    )
