package dev.jplugins.qualitytools.phpcsfixer

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
 * Declarative options for the PHP-CS-Fixer tool.
 *
 * Mirrors the three legacy fields from `PhpCSFixerOptionsConfiguration`
 * (`CODING_STANDARD`, `CUSTOM_RULESET_PATH`, `ALLOW_RISKY_RULES`) plus
 * a Mago-style `formatAfterFix` toggle.
 *
 * ### Gap notes
 *
 *  * [codingStandard] is rendered today as a free-form [StringSpec].
 *    The legacy plugin used a dynamic combobox populated by
 *    `php-cs-fixer list-sets --format=json`; the SDK needs an
 *    `OptionSpec.valuesProvider` (gap **G13**) before this can become
 *    a real enum-like choice control. Until then, the allowed values
 *    are documented in the source as a constant list, and end users
 *    type the string directly. Recognised values (per legacy
 *    `PhpCSFixerOptionsPanel` fallback list): `@PSR1`, `@PSR2`,
 *    `@PSR12`, `@Symfony`, `@DoctrineAnnotation`, `@PHP70Migration`,
 *    `@PHP71Migration`, plus any standard `list-sets` advertises.
 *    The special sentinel `Custom` selects [customConfig].
 *
 *  * [customConfig] is the path written when [codingStandard] equals
 *    the sentinel `"Custom"`. The SDK's visibility-rule support
 *    (gap **G14** — "sentinel toggles sibling controls") is not yet
 *    wired, so today the [customConfig] field is rendered
 *    unconditionally and only consumed by [PhpCsFixerBuildArgs] when
 *    the sentinel is active.
 *
 *  * [formatAfterFix] is a Mago-style hook: when the user runs the
 *    `dry-run` mode and a fix is offered, the SDK can be asked to
 *    immediately re-run `fix` so the file is rewritten in place. The
 *    actual wiring is gap **G22** ("InvokeMode rerun without
 *    `--dry-run`"); the option just stores the user's preference for
 *    that future handler.
 */
public class PhpCsFixerOptionsSchema : OptionsSchema {

    override val toolId: String = "php-cs-fixer"

    /**
     * Coding-standard sentinel (preset name) or the literal `Custom`
     * value (meaning: use [customConfig] instead).
     *
     * Default `@PSR12` (the modern community baseline; supersedes the
     * legacy `PSR2` default once `php-cs-fixer >= 2.16`).
     */
    public val codingStandard: StringSpec = string(
        key = "codingStandard",
        default = "@PSR12",
        displayName = "Coding standard",
        help = "Preset rule set or 'Custom' to use a custom config file.",
    )

    /**
     * Filesystem path to a `.php-cs-fixer.php` (or legacy
     * `.php_cs[.dist]`) config file. Only consulted when
     * [codingStandard] holds the sentinel `"Custom"`.
     */
    public val customConfig: PathSpec = path(
        key = "customConfig",
        default = "",
        displayName = "Custom config file",
        help = "Path to a custom `.php-cs-fixer.php` config file.",
        role = "config_file",
    )

    /**
     * When `true`, the CLI is invoked with `--allow-risky=yes`;
     * otherwise `--allow-risky=no`. Suppressed entirely when the
     * coding standard is `Custom`.
     */
    public val allowRiskyRules: BoolSpec = bool(
        key = "allowRiskyRules",
        default = false,
        displayName = "Allow risky rules",
        help = "Enables CS-Fixer's risky rule sets.",
    )

    /**
     * Mago-style post-fix hook (gap **G22**): when the dry-run mode
     * surfaces a fix and this is `true`, the SDK will rerun the
     * `format` mode immediately so the file is rewritten in place
     * rather than just annotated.
     */
    public val formatAfterFix: BoolSpec = bool(
        key = "formatAfterFix",
        default = false,
        displayName = "Run format after fix",
        help = "Rerun PHP-CS-Fixer without --dry-run after applying a fix.",
    )

    override val specs: List<OptionSpec<*>> = listOf(
        codingStandard,
        customConfig,
        allowRiskyRules,
        formatAfterFix,
    )

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        MODE_FORMAT to object : ModeSchema {
            override val specs: List<OptionSpec<*>> = emptyList()
        },
        MODE_DRY_RUN to object : ModeSchema {
            override val specs: List<OptionSpec<*>> = emptyList()
        },
    )

    public companion object {
        /** Sentinel value of [codingStandard] meaning "use [customConfig]". */
        public const val CUSTOM_STANDARD: String = "Custom"

        /** Known coding-standard preset names. Used by tests until gap G13 lands. */
        public val KNOWN_STANDARDS: List<String> = listOf(
            "@PSR1",
            "@PSR2",
            "@PSR12",
            "@Symfony",
            "@DoctrineAnnotation",
            "@PHP70Migration",
            "@PHP71Migration",
            CUSTOM_STANDARD,
        )

        public const val MODE_FORMAT: String = "format"
        public const val MODE_DRY_RUN: String = "dry-run"
    }
}
