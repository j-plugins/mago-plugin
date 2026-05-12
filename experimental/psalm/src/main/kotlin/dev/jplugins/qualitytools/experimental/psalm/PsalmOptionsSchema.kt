package dev.jplugins.qualitytools.experimental.psalm

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
 * Declarative options for the Psalm static analyser. Mirrors the legacy
 * `PsalmOptionsConfiguration` (decompiled at
 * `/tmp/decomp/com/jetbrains/php/tools/quality/psalm/PsalmOptionsConfiguration.java`)
 * which exposes exactly **four** fields:
 *
 *  - `config` — path to `psalm.xml` / `psalm.xml.dist`.
 *  - `showInfo` — toggles `--show-info=true`.
 *  - `findUnusedCode` — toggles `--find-unused-code`.
 *  - `findUnusedSuppress` — toggles `--find-unused-psalm-suppress`.
 *
 * Note: Psalm has **no** level spinner, **no** memory-limit, **no**
 * autoload, and **no** full-project flag — the schema here is
 * deliberately smaller than PHPStan's (see `docs/psalm/psalm-port-plan.md`
 * §1.1 and §2.1).
 *
 * The single mode schema attaches the conventional `enabled` +
 * `additionalArgs` overlay used by every SDK tool for the `analyze`
 * mode.
 */
public class PsalmOptionsSchema : OptionsSchema {

    public val config: PathSpec = path(
        key = "config",
        default = "",
        displayName = "Configuration file",
        help = "Path to a Psalm config file (psalm.xml or psalm.xml.dist).",
    )

    public val showInfo: BoolSpec = bool(
        key = "showInfo",
        default = false,
        displayName = "Show info-level issues",
        help = "Adds --show-info=true to the command line.",
    )

    public val findUnusedCode: BoolSpec = bool(
        key = "findUnusedCode",
        default = false,
        displayName = "Find unused code",
        help = "Adds --find-unused-code.",
    )

    public val findUnusedPsalmSuppress: BoolSpec = bool(
        key = "findUnusedPsalmSuppress",
        default = false,
        displayName = "Find unused @psalm-suppress",
        help = "Adds --find-unused-psalm-suppress.",
    )

    override val toolId: String = "psalm"

    override val specs: List<OptionSpec<*>> = listOf(
        config,
        showInfo,
        findUnusedCode,
        findUnusedPsalmSuppress,
    )

    public val enabled: BoolSpec = bool(
        key = "enabled",
        default = true,
        displayName = "Enabled",
    )

    public val additionalArgs: StringSpec = string(
        key = "additionalArgs",
        default = "",
        displayName = "Additional arguments",
    )

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        "analyze" to object : ModeSchema {
            override val specs: List<OptionSpec<*>> = listOf(enabled, additionalArgs)
        },
    )

    public companion object {
        public const val MODE_ANALYZE: String = "analyze"
    }
}
