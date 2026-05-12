package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.core.options.BoolSpec
import dev.jplugins.qualitytools.core.options.ModeSchema
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsSchema
import dev.jplugins.qualitytools.core.options.StringSpec
import dev.jplugins.qualitytools.core.options.bool
import dev.jplugins.qualitytools.core.options.string

/**
 * Declarative options for the PHP Mess Detector tool.
 *
 * Mirrors the legacy `MessDetectorOptionsConfiguration` (five public
 * boolean fields `CODESIZE` / `CONTROVERSIAL` / `DESIGN` / `NAMING` /
 * `UNUSEDCODE` plus a `List<RulesetDescriptor> customRulesets`) — with
 * one free win: the `cleancode` ruleset is exposed as a sixth toggle
 * (see plan §4.4) so users no longer have to point a custom row at
 * `vendor/phpmd/phpmd/src/main/resources/rulesets/cleancode.xml`.
 *
 * The six built-in ruleset toggles preserve the legacy default
 * shape: everything but `cleancode` and `controversial` ships on. The
 * `cleancode` default stays `false` so existing inspection output
 * doesn't change after migration.
 *
 * ### Custom rulesets — gap **G27**
 *
 * The legacy plugin stored custom rulesets as a `List<RulesetDescriptor>`
 * where each entry carries `(name, originalPath)`. The full SDK
 * shape is `ListSpec<CompoundSpec>` — see the port plan §4.2 and gap
 * **G27** in this module's `TODO.md`. Until that spec kind lands we
 * model the custom-rulesets surface as a CSV `StringSpec`
 * ([customRulesetFiles]): comma-separated absolute paths to ruleset
 * XML files. `PhpMessDetectorBuildArgs` joins those paths to the
 * built-in toggles into the single positional CSV argument phpmd
 * expects.
 *
 * Multi-token path-aware rewriting (each path must be remote-mapped
 * through `PhpPathMapper` while the closed-set tokens like `codesize`
 * stay untouched) is gap **G28** — see `TODO.md`.
 */
public class PhpMessDetectorOptionsSchema : OptionsSchema {

    override val toolId: String = "phpmd"

    /**
     * `cleancode` ruleset toggle. Default `false` to preserve the
     * legacy UI's behaviour (the legacy panel never exposed this
     * toggle — see port plan §4.4).
     */
    public val cleancode: BoolSpec = bool(
        key = "cleancode",
        default = false,
        displayName = "cleancode",
        help = "Enable phpmd's cleancode ruleset.",
    )

    /** `codesize` ruleset toggle. Default `true` per legacy behaviour. */
    public val codesize: BoolSpec = bool(
        key = "codesize",
        default = true,
        displayName = "codesize",
        help = "Enable phpmd's codesize ruleset.",
    )

    /**
     * `controversial` ruleset toggle. Default `false` per legacy
     * behaviour — the controversial ruleset has historically been
     * surfaced as opt-in.
     */
    public val controversial: BoolSpec = bool(
        key = "controversial",
        default = false,
        displayName = "controversial",
        help = "Enable phpmd's controversial ruleset.",
    )

    /** `design` ruleset toggle. Default `true` per legacy behaviour. */
    public val design: BoolSpec = bool(
        key = "design",
        default = true,
        displayName = "design",
        help = "Enable phpmd's design ruleset.",
    )

    /** `naming` ruleset toggle. Default `true` per legacy behaviour. */
    public val naming: BoolSpec = bool(
        key = "naming",
        default = true,
        displayName = "naming",
        help = "Enable phpmd's naming ruleset.",
    )

    /** `unusedcode` ruleset toggle. Default `true` per legacy behaviour. */
    public val unusedcode: BoolSpec = bool(
        key = "unusedcode",
        default = true,
        displayName = "unusedcode",
        help = "Enable phpmd's unusedcode ruleset.",
    )

    /**
     * Custom-rulesets CSV. Comma-separated absolute paths to phpmd
     * ruleset XML files. Empty by default.
     *
     * Stand-in for the legacy `List<RulesetDescriptor>` shape until
     * `ListSpec<CompoundSpec>` lands (gap **G27** in `TODO.md`).
     * Paths are joined into the same comma-separated phpmd CLI
     * argument the built-in toggles produce.
     */
    public val customRulesetFiles: StringSpec = string(
        key = "customRulesetFiles",
        default = "",
        displayName = "Custom rulesets",
        help = "Comma-separated absolute paths to custom phpmd ruleset XML files.",
    )

    override val specs: List<OptionSpec<*>> = listOf(
        cleancode,
        codesize,
        controversial,
        design,
        naming,
        unusedcode,
        customRulesetFiles,
    )

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        MODE_ANALYZE to object : ModeSchema {
            override val specs: List<OptionSpec<*>> = emptyList()
        },
    )

    public companion object {
        public const val MODE_ANALYZE: String = "analyze"

        /** The closed set of built-in phpmd ruleset short-names. */
        public val BUILTIN_RULESETS: List<String> = listOf(
            "cleancode",
            "codesize",
            "controversial",
            "design",
            "naming",
            "unusedcode",
        )
    }
}
