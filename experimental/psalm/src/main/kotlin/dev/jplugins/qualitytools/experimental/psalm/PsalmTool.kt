package dev.jplugins.qualitytools.experimental.psalm

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.OutputFormats
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.plainArg
import dev.jplugins.qualitytools.core.tool.qualityTool

/**
 * Psalm — a static analyser for PHP.
 *
 * Maps the legacy `com.jetbrains.php.tools.quality.psalm` plugin
 * onto the `quality-tools-sdk`. See `docs/psalm/psalm-port-plan.md`
 * for the full mapping rationale.
 *
 * Key facts:
 *  - Single mode `analyze` — Psalm has no `--analyze` vs
 *    `--analyze --pro` distinction like PHPStan.
 *  - **No verb** — `psalm` does not take an analyse subcommand the way
 *    `phpstan analyse` does. The output format flag
 *    (`--output-format=checkstyle`) is baked into `defaultArgs`
 *    instead.
 *  - `pathArgKeys` lists every key whose value is a path so the
 *    path-aware rewriter (phase 05) can remap local↔remote when a
 *    PHP interpreter binary source is in use; we register both `-c`
 *    and the long-form alias `--config-file` even though the legacy
 *    plugin only emits `-c` (defensive — a user-supplied additional
 *    arg may use the long form).
 *  - `inspectionShortNames = ["PsalmGlobal", "PsalmValidation"]`
 *    matches the legacy XML so existing user inspection profiles
 *    keep working post-migration (phase 10a.1 promise).
 *  - `resultReaderId = "checkstyle-xml"` — Psalm emits the same
 *    checkstyle XML envelope as PHPStan when invoked with
 *    `--output-format=checkstyle`, so the bundled `CheckstyleXmlReader`
 *    (phase 06) handles its output with no per-tool reader code.
 */
public object PsalmTool {

    public const val ID: String = "psalm"
    public const val DISPLAY_NAME: String = "Psalm"

    public val OPTIONS_SCHEMA: PsalmOptionsSchema = PsalmOptionsSchema()

    public val INSTANCE: QualityTool = qualityTool(ID) {
        displayName = DISPLAY_NAME
        languages("PHP")
        capabilities(Capabilities.ANALYZE)
        resultReaderId = OutputFormats.CHECKSTYLE_XML
        optionsSchema = OPTIONS_SCHEMA
        binaryValidator = PsalmVersionValidator.create()
        inspectionShortNames.addAll(listOf("PsalmGlobal", "PsalmValidation"))

        mode(PsalmOptionsSchema.MODE_ANALYZE) {
            displayName = "Analyze"
            // No verb: Psalm doesn't use a subcommand like `phpstan analyse`.
            // Bake the output-format flag into defaultArgs so the runner
            // always emits it, matching legacy PsalmGlobalInspection.
            defaultArgs.add(plainArg("--output-format=checkstyle"))
            defaultArgs.add(plainArg("--no-progress"))
            defaultArgs.add(plainArg("--no-cache"))
            pathArgKeys.add("-c")
            pathArgKeys.add("--config-file")
        }

        buildArgs { ctx, mode, target ->
            PsalmBuildArgs.build(ctx, mode, target, OPTIONS_SCHEMA)
        }
    }
}
