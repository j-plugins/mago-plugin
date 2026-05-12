package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.OutputFormats
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.qualityTool

/**
 * PHPStan tool registration on top of `:quality-tools-sdk`.
 *
 * Built via the `qualityTool { }` DSL per `docs/work-plans/coverage-and-tools-plan.md`
 * §C.1.1 and the port plan `docs/phpstan/phpstan-port-plan.md` §6
 * ("Required" deliverables).
 *
 * Single mode `"analyze"`: PHPStan has exactly one verb the IDE
 * invokes (legacy `PhpStanGlobalInspection.getCommandLineOptions`
 * always passes `analyze` as the first arg). The legacy plugin also
 * exposes the same arg surface for on-the-fly and batch runs;
 * `executionStyle = ON_THE_FLY` is used here because the experimental
 * port does not yet implement batch caching (port plan gap 4.9).
 *
 * `inspectionShortNames` are pinned to the legacy values so existing
 * user inspection profiles keep their severity / scope settings after
 * migration (per phase 10a.1 of `docs/phases/README.md`).
 *
 * `resultReaderId` is set tool-wide to checkstyle XML — PHPStan is
 * invoked with `--error-format=checkstyle` (see [buildPhpStanArgs]).
 * The mode declares `resultReaderId = null` so the tool-level value
 * wins per phase 01 Tier-1 patch G9.
 *
 * Capabilities advertise `"analyze"` only; PHPStan does not emit fixes,
 * does not format, does not lint in the linter sense.
 *
 * `binaryValidator` is wired to [PhpStanVersionValidator]; see that
 * file for the `--version` parsing contract.
 *
 * Out of scope (see `experimental/phpstan/TODO.md`):
 *  - phase 06 checkstyle reader registration;
 *  - phase 08 annotator wiring;
 *  - phase 07 settings UI panel;
 *  - phase 04 settings-storage migration.
 */
public val PhpStanTool: QualityTool = qualityTool("phpstan") {
    displayName = "PHPStan"
    languages("PHP")
    capabilities(Capabilities.ANALYZE)
    resultReaderId = OutputFormats.CHECKSTYLE_XML
    optionsSchema = PhpStanOptionsSchema()
    binaryValidator = PhpStanVersionValidator

    // Preserve the two legacy inspection short-names so user inspection
    // profiles keep working after migration. Order matches the legacy
    // EP order in com.jetbrains.php.tools.quality.phpstan:
    //   - PhpStanGlobal (batch / "Inspect Code")
    //   - PhpStanValidation (on-the-fly annotator)
    inspectionShortNames.addAll(listOf("PhpStanGlobal", "PhpStanValidation"))

    mode(PhpStanOptionsSchema.ANALYZE_MODE_ID) {
        displayName = "Analyze"
        verb = "analyze"
        executionStyle = ExecutionStyles.ON_THE_FLY
        supportsStdin = false
        supportsFix = false
        // Tool-level reader wins (phase 01 Tier-1 patch G9 — leaving
        // mode.resultReaderId null inherits "checkstyle-xml").
        resultReaderId = null
        // Path-aware keys for the rewriter: the bare flags the legacy
        // plugin emits as `<flag> <path>` pairs plus their long-form
        // aliases that PHPStan also accepts.
        pathArgKeys.addAll(listOf("-c", "--configuration", "-a", "--autoload-file"))
    }

    buildArgs { ctx, mode, target -> buildPhpStanArgs(ctx, mode, target) }
}

/**
 * Type-safe accessor for the [PhpStanTool]'s schema. Many call sites
 * read `tool.optionsSchema` then cast; this getter centralises the
 * cast and gives downstream code a single point to fail if the schema
 * type is ever swapped.
 */
public val QualityTool.phpStanSchema: PhpStanOptionsSchema
    get() {
        check(id == "phpstan") {
            "phpStanSchema accessed on tool '$id'; only valid for phpstan."
        }
        return optionsSchema as PhpStanOptionsSchema
    }

/** Documentation aid: the single validator instance used by [PhpStanTool]. */
public val PhpStanBinaryValidator: BinaryValidator
    get() = PhpStanVersionValidator
