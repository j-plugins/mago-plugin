package dev.jplugins.qualitytools.phpcs

import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.OutputFormats
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.plainArg
import dev.jplugins.qualitytools.core.tool.qualityTool

/**
 * `PHP_CodeSniffer` quality-tool registration.
 *
 * Two modes:
 *
 *  - `lint` — phpcs runs the analyzer. `verb = ""` because phpcs takes
 *    the file path as its first positional argument with no subcommand.
 *    `defaultArgs` carry the checkstyle reporter and `--no-colors` so
 *    every invocation produces machine-readable output and never
 *    bleeds ANSI codes into the parser. `--standard` may be a path
 *    (gap-13 pending), so its key is listed in `pathArgKeys`.
 *    `supportsStdin = true` (phpcs accepts `-` with `--stdin-path`),
 *    `supportsFix = false`.
 *
 *  - `fix`  — semantically maps to `phpcbf`, a separate binary. The
 *    SDK does not yet model a secondary `BinaryDescriptor` on a
 *    `ConfigSource` (gap G10), so the mode is declared with all the
 *    metadata the future runner needs (executionStyle MANUAL,
 *    `supportsFix = true`) but the actual binary swap is a TODO.
 *
 * `inspectionShortNames = setOf("PhpCSValidationInspection")` mirrors
 * the legacy plugin's single-name short-name so existing IntelliJ
 * inspection profiles survive the migration to the new SDK bridge
 * (phase 10a.1).
 *
 * `capabilities = setOf("lint", "fix")` — declared even though the
 * `fix` capability cannot fully run until G10/G12 land.
 */
public object PhpCSTool {

    /** Inspection short-name the IntelliJ profile XML keys on.
     *  See SDK rule "inspection-shortname preservation" (phase 10a.1). */
    public const val INSPECTION_SHORT_NAME: String = "PhpCSValidationInspection"

    /** Tool id; shared with `OptionsSchema.toolId`. */
    public const val ID: String = "phpcs"

    public val schema: PhpCSOptionsSchema = PhpCSOptionsSchema()

    public val instance: QualityTool = qualityTool(ID) {
        displayName = "PHP_CodeSniffer"
        languages("PHP")
        resultReaderId = OutputFormats.CHECKSTYLE_XML
        optionsSchema = schema
        binaryValidator = PhpCSVersionValidator
        capabilities(Capabilities.LINT, Capabilities.FIX)
        inspectionShortNames.add(INSPECTION_SHORT_NAME)

        mode("lint") {
            verb = ""
            executionStyle = ExecutionStyles.ON_THE_FLY
            supportsStdin = true
            supportsFix = false
            defaultArgs.add(plainArg("--report=checkstyle"))
            defaultArgs.add(plainArg("--no-colors"))
            // `--standard` may be either a path or a name. When it's a
            // path the rewriter needs to remap it; declaring the key
            // here lets that happen at phase-05 dispatch.
            pathArgKeys.add("--standard")
        }

        mode("fix") {
            verb = ""
            // TODO(gap G10): the `fix` mode targets the `phpcbf`
            // binary, not `phpcs`. Until `ConfigSource` can carry a
            // secondary binary, this mode declares its metadata but
            // the runner cannot actually swap binaries from the
            // existing source. The plugin holds the phpcbf path in
            // an option (`PhpCSOptionsSchema.phpcbfPath`) as a
            // stop-gap.
            // TODO(gap G12): once a tool declares any `format` /
            // `fix` mode, the SDK should auto-register a "Reformat
            // with <tool>" action + an Alt-Enter intention. Not yet.
            executionStyle = ExecutionStyles.MANUAL
            supportsStdin = false
            supportsFix = true
            // phpcbf's default reporter is fine; no extra defaults.
        }

        buildArgs { ctx, mode, target -> PhpCSBuildArgs.build(ctx, mode, target) }
    }
}
