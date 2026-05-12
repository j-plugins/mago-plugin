package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.ModeSchema
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsSchema
import dev.jplugins.qualitytools.core.options.PathSpec
import dev.jplugins.qualitytools.core.options.StringSpec
import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.path
import dev.jplugins.qualitytools.core.options.string

/**
 * Options for the Laravel Pint port (experimental).
 *
 * Mirrors the slice of the legacy `LaravelPintOptionsConfiguration`
 * we actually surface in this first cut:
 *
 *  - [customConfig]: path to a `pint.json` (note: Pint uses JSON, not
 *    the PHP-CS-Fixer `.php` config file form).
 *  - [verbose]: toggles the `--verbose` flag.
 *
 * The legacy plugin additionally exposed a 4-item `preset` combobox
 * (`laravel` / `symfony` / `psr12` / `defined in pint.json`) and a
 * `reformatOnlyUncommittedFiles` toggle. Those are deliberately
 * deferred — see TODO.md — they belong to follow-up phases (commit
 * handler, preset wiring).
 *
 * Mode schemas: a single `format` mode (the only one this tool has,
 * per `LaravelPintTool.modes`), populated with the SDK's standard
 * "enabled + additionalArgs" pair so the autosettings panel renders.
 */
public class LaravelPintOptionsSchema : OptionsSchema {

    public val customConfig: PathSpec = path(
        key = CUSTOM_CONFIG_KEY,
        displayName = "Path to pint.json",
        help = "Relative path (from the project root) to a pint.json " +
            "config file. Forwarded as `--config=<path>`.",
        role = "config_file",
    )

    public val verbose: BoolSpec = bool(
        key = VERBOSE_KEY,
        default = false,
        displayName = "Verbose output",
        help = "Adds `--verbose` to the Pint command line.",
    )

    /** Per-mode `enabled` toggle (rendered by `AutoToolSettingsPanel`). */
    public val formatEnabled: BoolSpec = bool(
        key = MODE_FORMAT_ENABLED_KEY,
        default = true,
        displayName = "Enable format mode",
    )

    /** Per-mode "extra CLI args" textbox. */
    public val formatAdditionalArgs: StringSpec = string(
        key = MODE_FORMAT_ADDITIONAL_ARGS_KEY,
        default = "",
        displayName = "Additional arguments",
        help = "Free-form CLI tokens appended to every Pint invocation " +
            "after the SDK-built args.",
    )

    override val toolId: String = TOOL_ID

    override val specs: List<OptionSpec<*>> = listOf(customConfig, verbose)

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        MODE_FORMAT to FormatModeSchema(formatEnabled, formatAdditionalArgs),
    )

    private class FormatModeSchema(
        enabled: BoolSpec,
        additionalArgs: StringSpec,
    ) : ModeSchema {
        override val specs: List<OptionSpec<*>> = listOf(enabled, additionalArgs)
    }

    public companion object {
        /** Same as [LaravelPintTool.ID]; duplicated here so the schema
         *  has no compile-time dependency on the tool class. */
        public const val TOOL_ID: String = "laravel-pint"

        /** Sole mode id this tool currently exposes. */
        public const val MODE_FORMAT: String = "format"

        public const val CUSTOM_CONFIG_KEY: String = "customConfig"
        public const val VERBOSE_KEY: String = "verbose"
        public const val MODE_FORMAT_ENABLED_KEY: String = "format.enabled"
        public const val MODE_FORMAT_ADDITIONAL_ARGS_KEY: String =
            "format.additionalArgs"
    }
}
