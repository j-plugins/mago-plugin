package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.IntSpec
import dev.jplugins.qualitytools.core.options.ModeSchema
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsSchema
import dev.jplugins.qualitytools.core.options.PathSpec
import dev.jplugins.qualitytools.core.options.StringSpec
import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.int
import dev.jplugins.qualitytools.core.options.path
import dev.jplugins.qualitytools.core.options.string

/**
 * Declarative options for `PHP_CodeSniffer` (`phpcs` + `phpcbf`).
 *
 * Mirrors the eight legacy fields stored on
 * `PhpCSOptionsConfiguration` / `PhpCSValidationInspection`
 * (`IGNORE_WARNINGS`, `CODING_STANDARD`, `CUSTOM_RULESET_PATH`,
 * `WARNING_HIGHLIGHT_LEVEL_NAME`, `SHOW_SNIFF_NAMES`,
 * `USE_INSTALLED_PATHS`, `INSTALLED_PATHS`, `EXTENSIONS`) — minus the
 * fields gated on phase-06 / phase-07 SDK work (see [PhpCSTool] kdoc
 * and `TODO.md` for the cross-references).
 *
 * Gap G13 — [codingStandard] is modelled here as a plain `StringSpec`
 * with `default = "PSR12"`. The legacy plugin populated the combobox
 * by running `phpcs -i` against the active profile and parsing the
 * English-sentence response ("The installed coding standards are A,
 * B, …, and Z"). The SDK does not yet model "values come from a
 * resolved binary" — that is `DynamicChoiceSpec`, slated for phase
 * 04 + 07. Until then the spec accepts ANY string and is free-form;
 * the static well-known values are documented (not enumerated) and
 * the dynamic-discovery path stays out of scope.
 *
 * Gap G10 — [phpcbfPath] models the secondary `phpcbf` binary path
 * as an `OptionSpec` for now, because `ConfigSource` cannot yet
 * resolve multiple binaries keyed by role. Once the secondary-
 * binary descriptor lands (phase 02 + 05 + 07), this option goes
 * away and the path is read off the source instead.
 */
public class PhpCSOptionsSchema : OptionsSchema {
    override val toolId: String = "phpcs"

    /**
     * Coding standard, e.g. `"PSR12"`. Well-known values phpcs ships
     * built-in: `PSR1`, `PSR2`, `PSR12`, `PEAR`, `MySource`, `Squiz`,
     * `Zend`, plus the sentinel `"Custom"` (use [customRuleset] when
     * set to "Custom"). Composer-installed standards (e.g. `Drupal`,
     * `WordPress`) extend the set at runtime — those are NOT
     * enumerated here. TODO(gap G13): use `DynamicChoiceSpec` so the
     * combobox picks up `phpcs -i` output.
     */
    public val codingStandard: StringSpec =
        string("codingStandard", default = "PSR12", displayName = "Coding standard")

    /**
     * Path to a project-specific `phpcs.xml` ruleset. Read only when
     * [codingStandard] equals the sentinel `"Custom"`. The legacy
     * plugin pivoted the combobox onto a path field; here the two
     * options are independent and `buildArgs` decides.
     */
    public val customRuleset: PathSpec =
        path("customRuleset", displayName = "Custom ruleset")

    /** `phpcs -s` — when `true`, phpcs annotates messages with the
     *  triggering sniff name. The plugin's `MessageEnricher` then
     *  prepends the sniff to message titles. */
    public val showSniffNames: BoolSpec =
        bool("showSniffNames", default = false, displayName = "Show sniff names")

    /**
     * Path to the `phpcbf` executable. Stored as an option only
     * until the SDK gains role-keyed binaries on a `ConfigSource`
     * (gap G10). At that point this moves off `OptionsBag` and onto
     * the source's binary set.
     */
    public val phpcbfPath: StringSpec =
        string("phpcbfPath", default = "", displayName = "phpcbf path")

    /** Reporting severity threshold. phpcs accepts `1..10`; `5` is
     *  the documented default. */
    public val severity: IntSpec =
        int("severity", default = 5, displayName = "Severity", range = 1..10)

    override val specs: List<OptionSpec<*>> = listOf(
        codingStandard,
        customRuleset,
        showSniffNames,
        phpcbfPath,
        severity,
    )

    public val lintMode: PhpCSModeSchema = PhpCSModeSchema()
    public val fixMode: PhpCSModeSchema = PhpCSModeSchema()

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        "lint" to lintMode,
        "fix" to fixMode,
    )
}

/**
 * Per-mode schema: both `lint` and `fix` carry the same shape
 * (an enable toggle + free-form `additionalArgs` passed through).
 */
public class PhpCSModeSchema : ModeSchema {
    public val enabled: BoolSpec = bool("enabled", default = true, displayName = "Enabled")
    public val additionalArgs: StringSpec =
        string("additionalArgs", default = "", displayName = "Additional CLI arguments")

    override val specs: List<OptionSpec<*>> = listOf(enabled, additionalArgs)
}
