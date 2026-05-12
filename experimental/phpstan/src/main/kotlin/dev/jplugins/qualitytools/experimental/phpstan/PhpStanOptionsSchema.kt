package dev.jplugins.qualitytools.experimental.phpstan

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
 * Declarative options for PHPStan.
 *
 * Maps the five legacy public fields on
 * `com.jetbrains.php.tools.quality.phpstan.PhpStanGlobalInspection`
 * (`FULL_PROJECT`, `memoryLimit`, `level`, `config`, `autoload`) plus a
 * mode-scoped overlay for `analyze` (matches the work plan in
 * `docs/work-plans/coverage-and-tools-plan.md` §C.1 and the spec in
 * `docs/phpstan/phpstan-port-plan.md` §1.1).
 *
 * Field-by-field mapping (port plan §1.1, decompiled
 * `PhpStanGlobalInspection.java:64-71`):
 *
 *  - `bool("fullProject")`        — `FULL_PROJECT = false`
 *  - `string("memoryLimit")`      — `memoryLimit = "2G"`
 *  - `int("level", range = 0..10)` — `level = 4` (legacy panel spinner
 *    is 0..8; we accept up to 10 because PHPStan 2.x added higher
 *    levels)
 *  - `path("config")`             — `config = ""`
 *  - `path("autoload")`           — `autoload = ""`
 *
 * The `analyze` mode-schema carries the two execution-style knobs that
 * are common to every tool but live per-mode in the SDK: an
 * `enabled` bool (so the user can flip a mode off without removing
 * the profile) and `additionalArgs` for free-form CLI passthrough.
 *
 * Gap reference: this corresponds to phase 04's options-schema
 * deliverable (see `docs/phases/phase-04-options-schema.md`); it does
 * not yet bridge to persistent storage because storage is also a
 * phase 04 deliverable that isn't wired in this experimental port —
 * see `experimental/phpstan/TODO.md` gap 4.8.
 */
public class PhpStanOptionsSchema : OptionsSchema {
    override val toolId: String = "phpstan"

    /** Toggle for "run against the whole project" vs the current file. */
    public val fullProject: BoolSpec = bool(
        key = "fullProject",
        default = false,
        displayName = "Inspect whole project",
        help = "When true, --analyze receives the project root; otherwise " +
            "the current file is passed.",
    )

    /** Mirrors `--memory-limit=<X>` (`PhpStanGlobalInspection.memoryLimit`,
     *  legacy default `"2G"`). */
    public val memoryLimit: StringSpec = string(
        key = "memoryLimit",
        default = "2G",
        displayName = "Memory limit",
        help = "Value passed to PHPStan's --memory-limit flag (e.g. 1G, 2G, 512M).",
    )

    /** PHPStan rule level (0..10). Legacy default is `4`. */
    public val level: IntSpec = int(
        key = "level",
        default = 4,
        displayName = "Analysis level",
        help = "Rule level passed via --level when no configuration file is set.",
        range = 0..10,
    )

    /** Path to a PHPStan config file (`phpstan.neon` / `.dist`). When
     *  present, wins over [level] (legacy parity, see
     *  `PhpStanGlobalInspection.getCommandLineOptions` lines 120-125). */
    public val config: PathSpec = path(
        key = "config",
        default = "",
        displayName = "Configuration file",
        help = "Path to phpstan.neon or phpstan.neon.dist; if set, overrides --level.",
    )

    /** Optional `-a <file>` autoload script. */
    public val autoload: PathSpec = path(
        key = "autoload",
        default = "",
        displayName = "Autoload file",
        help = "Path passed via -a so PHPStan can resolve project autoload before analysing.",
    )

    override val specs: List<OptionSpec<*>> = listOf(
        fullProject,
        memoryLimit,
        level,
        config,
        autoload,
    )

    /** `analyze` mode-scoped overlay. Keys live in the mode-bag, not
     *  the top-level bag, so the user can disable analyze without losing
     *  the global `level`/`memoryLimit`/etc. */
    public val analyzeMode: AnalyzeMode = AnalyzeMode()

    override val modeSchemas: Map<String, ModeSchema> = mapOf(
        ANALYZE_MODE_ID to analyzeMode,
    )

    /**
     * Mode-schema for the single PHPStan mode (`analyze`). Carries the
     * two cross-cutting knobs every tool needs:
     *
     *  - `enabled` (default `true`) — whether the annotator should
     *    actually invoke PHPStan for this profile;
     *  - `additionalArgs` (default `""`) — free-form CLI suffix joined
     *    to the built args (split on whitespace at run time, not here).
     */
    public class AnalyzeMode : ModeSchema {
        public val enabled: BoolSpec = bool(
            key = "enabled",
            default = true,
            displayName = "Enabled",
            help = "When false, this profile is configured but does not run.",
        )

        public val additionalArgs: StringSpec = string(
            key = "additionalArgs",
            default = "",
            displayName = "Additional CLI arguments",
            help = "Free-form text appended to the built command line.",
        )

        override val specs: List<OptionSpec<*>> = listOf(enabled, additionalArgs)
    }

    public companion object {
        /** Id of the single PHPStan mode (see [PhpStanTool]). */
        public const val ANALYZE_MODE_ID: String = "analyze"
    }
}
