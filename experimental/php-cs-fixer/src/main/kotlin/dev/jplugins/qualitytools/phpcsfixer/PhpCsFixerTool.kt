package dev.jplugins.qualitytools.phpcsfixer

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.FormattingOutputModes
import dev.jplugins.qualitytools.core.tool.OutputFormats
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.plainArg

/**
 * PHP-CS-Fixer as a [QualityTool]. First fixer-shaped adopter of the
 * `:quality-tools-sdk` — its primary purpose is mutating the file,
 * with on-the-fly diagnostics derived from a `--dry-run` invocation.
 *
 * Two modes:
 *
 *  * [PhpCsFixerOptionsSchema.MODE_FORMAT] — `executionStyle =
 *    [ExecutionStyles.FORMAT]`, `formattingOutputMode =
 *    [FormattingOutputModes.IN_PLACE]`. CS-Fixer rewrites the file on
 *    disk; the SDK's `AsyncFormattingServiceAdapter` (phase 07) reads
 *    the new bytes back. Default args:
 *    `["fix", "--no-interaction", "--no-ansi"]`. `supportsStdin` is
 *    true (CS-Fixer accepts `-` as the target).
 *
 *  * [PhpCsFixerOptionsSchema.MODE_DRY_RUN] — `executionStyle =
 *    [ExecutionStyles.ON_THE_FLY]`. Identical CLI plus
 *    `--dry-run --format=json`. The result reader id is set to
 *    [OutputFormats.UDIFF] — the bundled JSON envelope + udiff body
 *    reader is gap **G21** (see [PhpCsFixerOptionsSchema] kdoc).
 *
 * Inspection short-names preserve the legacy
 * `PhpCSFixerValidationInspection` entry so migrated user profiles
 * keep working.
 *
 * Capabilities `{ "format", "fix" }` mark PHP-CS-Fixer as both a
 * formatter (Ctrl-Alt-L target) and a fixer (quick-fix on annotator
 * messages).
 */
public class PhpCsFixerTool(
    public val schema: PhpCsFixerOptionsSchema = PhpCsFixerOptionsSchema(),
) : QualityTool {

    override val id: String = "php-cs-fixer"
    override val displayName: String = "PHP CS Fixer"
    override val supportedLanguageIds: Set<String> = setOf("PHP")
    override val capabilities: Set<String> = setOf(Capabilities.FORMAT, Capabilities.FIX)

    /**
     * Tool-level default reader id. The `format` mode produces no
     * meaningful stdout (the file is rewritten in place), so the
     * tool-level default lines up with the `dry-run` mode's reader.
     * `format` overrides this implicitly via `formattingOutputMode`.
     */
    override val resultReaderId: String = OutputFormats.UDIFF

    override val inspectionShortNames: Set<String> = setOf(
        "PhpCSFixerValidationInspection",
    )

    override val optionsSchema: PhpCsFixerOptionsSchema = schema

    override val binaryValidator: BinaryValidator = PhpCsFixerVersionValidator

    override val modes: List<ToolMode> = listOf(
        FormatMode,
        DryRunMode,
    )

    override fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg> = PhpCsFixerBuildArgs.build(ctx, mode, target, schema)

    /** `format` mode — CS-Fixer rewrites the file in place. */
    public object FormatMode : ToolMode {
        override val id: String = PhpCsFixerOptionsSchema.MODE_FORMAT
        override val displayName: String = "Format"
        override val verb: String = "fix"
        override val executionStyle: String = ExecutionStyles.FORMAT
        override val formattingOutputMode: String = FormattingOutputModes.IN_PLACE
        override val supportsStdin: Boolean = true

        /** `supportsFix` is "n/a" — this *is* the fix path. */
        override val supportsFix: Boolean = false

        override val defaultArgs: List<ToolArg> = listOf(
            plainArg("fix"),
            plainArg("--no-interaction"),
            plainArg("--no-ansi"),
        )

        override val pathArgKeys: Set<String> = setOf("--config")
    }

    /**
     * `dry-run` mode — same CLI plus `--dry-run --format=json`.
     * Drives the on-the-fly annotator path.
     */
    public object DryRunMode : ToolMode {
        override val id: String = PhpCsFixerOptionsSchema.MODE_DRY_RUN
        override val displayName: String = "Dry run"
        override val verb: String = "fix"
        override val executionStyle: String = ExecutionStyles.ON_THE_FLY
        override val supportsStdin: Boolean = true
        override val supportsFix: Boolean = false

        /**
         * The dry-run mode emits a JSON envelope wrapping a udiff
         * body. Until the bundled JSON-wrapped udiff reader lands
         * (gap **G21**, phase 06), this id will fall back to whatever
         * the SDK registers for [OutputFormats.UDIFF].
         */
        override val resultReaderId: String = OutputFormats.UDIFF

        override val defaultArgs: List<ToolArg> = listOf(
            plainArg("fix"),
            plainArg("--no-interaction"),
            plainArg("--no-ansi"),
        )

        override val pathArgKeys: Set<String> = setOf("--config")
    }
}
