package dev.jplugins.qualitytools.messdetector

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget

/**
 * PHP Mess Detector as a [QualityTool]. Second adopter of the
 * `:quality-tools-sdk` after PHPStan.
 *
 * Phpmd ships with one mode — `analyze` — driving both on-the-fly and
 * batch (`Code → Inspect Code…`) flows; the legacy plugin does NOT
 * use the lint+batch split PHPStan does (see plan §2.1, §4.8). The
 * inspection short-name set therefore has exactly one entry,
 * `MessDetectorValidationInspection`, preserving migration parity
 * with existing user inspection profiles.
 *
 * `resultReaderId = "phpmd-xml"` references the future
 * `PhpmdXmlReader` in `:php` (port plan §4.1) — phpmd's XML output is
 * **not** checkstyle. The reader is not yet bundled; see `TODO.md`.
 *
 * `capabilities = setOf("analyze")` marks phpmd as an analyser
 * (project-wide inspection target); it is not a fixer/formatter.
 */
public class PhpMessDetectorTool(
    public val schema: PhpMessDetectorOptionsSchema = PhpMessDetectorOptionsSchema(),
) : QualityTool {

    override val id: String = "phpmd"
    override val displayName: String = "PHP Mess Detector"
    override val supportedLanguageIds: Set<String> = setOf("PHP")
    override val capabilities: Set<String> = setOf(Capabilities.ANALYZE)

    /**
     * Tool-level reader id. Will be served by `PhpmdXmlReader` in
     * `:php` once phase 06 lands a tool-specific reader EP; see
     * `TODO.md` (gap **G**: `PhpmdXmlReader`).
     */
    override val resultReaderId: String = "phpmd-xml"

    /**
     * Legacy phpmd plugin shipped exactly one inspection — its
     * short name is preserved verbatim so post-migration user
     * profile XML keeps referring to the same class. The set has
     * a single element on purpose (plan §4.8: "single-entry
     * short-name acceptance"). NOT the lint+batch pair PHPStan
     * uses.
     */
    override val inspectionShortNames: Set<String> = setOf(
        "MessDetectorValidationInspection",
    )

    override val optionsSchema: PhpMessDetectorOptionsSchema = schema

    override val binaryValidator: BinaryValidator = PhpMessDetectorVersionValidator

    override val modes: List<ToolMode> = listOf(AnalyzeMode)

    override fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg> = PhpMessDetectorBuildArgs.build(ctx, mode, target, schema)

    /**
     * The one phpmd mode — `analyze`. `verb` is empty because phpmd
     * takes only positional arguments (no subcommand verb). The
     * positional `<target> <format> <rulesets>` shape is constructed
     * dynamically in [PhpMessDetectorBuildArgs]; no static
     * `defaultArgs` are pre-baked here.
     */
    public object AnalyzeMode : ToolMode {
        override val id: String = PhpMessDetectorOptionsSchema.MODE_ANALYZE
        override val displayName: String = "Analyze"

        /** No verb — phpmd's CLI is purely positional. */
        override val verb: String = ""

        override val executionStyle: String = ExecutionStyles.ON_THE_FLY

        /**
         * Phpmd accepts `-` as a stdin marker for the target file
         * (the legacy `MessDetectorAnnotator.runOnTempFiles` switch
         * is gated by the `php.cs.fixer.temp.file` registry key).
         * Kept conservative for now: `false` until the
         * stdin-fallback story is end-to-end tested.
         */
        override val supportsStdin: Boolean = false

        override val supportsFix: Boolean = false

        /** Empty — args are built dynamically in [PhpMessDetectorBuildArgs]. */
        override val defaultArgs: List<ToolArg> = emptyList()
    }
}
