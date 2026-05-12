package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.tool.BinaryValidator
import dev.jplugins.qualitytools.core.tool.Capabilities
import dev.jplugins.qualitytools.core.tool.ExecutionStyles
import dev.jplugins.qualitytools.core.tool.FormattingOutputModes
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolMode
import dev.jplugins.qualitytools.core.tool.ToolTarget

/**
 * SDK-native port of the Laravel Pint integration.
 *
 * Pint is a thin Laravel-flavoured wrapper around PHP-CS-Fixer; this
 * first cut exposes just the **format** mode (in-place rewrite),
 * mirroring `LaravelPintReformatFile` in the legacy plugin.
 *
 * Out-of-scope (see TODO.md):
 *   - `analyze` / on-the-fly mode (needs `LaravelPintXmlMessageProcessor`
 *     port — depends on the CS-Fixer diff-XML `ResultReader` from
 *     phase 06).
 *   - Format-on-commit check-in handler (G24, shared with CS-Fixer).
 *   - `LaravelPintMigration` reading the legacy `<LaravelPint>` XML
 *     state component.
 *
 * Identity discipline (SDK rule 11): equality on [id]. Inherited from
 * `QualityTool`'s default contract — we don't override `equals` here.
 */
public class LaravelPintTool(
    public val schema: LaravelPintOptionsSchema = LaravelPintOptionsSchema(),
) : QualityTool {

    override val id: String = ID
    override val displayName: String = "Laravel Pint"
    override val supportedLanguageIds: Set<String> = setOf("PHP")

    override val capabilities: Set<String> = setOf(Capabilities.FORMAT)

    override val resultReaderId: String = RESULT_READER_ID

    override val optionsSchema: LaravelPintOptionsSchema = schema

    override val binaryValidator: BinaryValidator = LaravelPintVersionValidator

    /**
     * Preserved verbatim from the legacy plugin's
     * `LaravelPintValidationInspection` (short-name
     * `Laravel_Pint_validation_tool`). The snake_case-with-capitals
     * spelling is **not** a bug — fixing it would orphan every user's
     * existing inspection profile. SDK rule 33 (non-PascalCase
     * tolerated) covers this.
     */
    override val inspectionShortNames: Set<String> = setOf(LEGACY_INSPECTION_SHORT_NAME)

    override val modes: List<ToolMode> = listOf(FormatMode)

    override fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg> = LaravelPintBuildArgs.build(ctx, mode, target, schema)

    /**
     * The only mode this port currently exposes — Pint's "rewrite the
     * file in place" path.
     *
     *  - `executionStyle = "format"` → routed through phase 07's
     *    `AsyncFormattingServiceAdapter`.
     *  - `formattingOutputMode = "in_place"` → the adapter re-reads
     *    the file from disk after Pint finishes (Pint does not emit
     *    fixed content to stdout when invoked without `--test`).
     *  - `defaultArgs` is empty: Pint's bare invocation already
     *    rewrites the file when given a path arg.
     *  - `supportsStdin = false`: the legacy Pint does not accept
     *    `php://stdin` reliably; same default as `ToolMode`'s
     *    interface contract, kept explicit for documentation.
     */
    public object FormatMode : ToolMode {
        override val id: String = LaravelPintOptionsSchema.MODE_FORMAT
        override val displayName: String = "Format"
        override val verb: String = "format"
        override val executionStyle: String = ExecutionStyles.FORMAT
        override val formattingOutputMode: String = FormattingOutputModes.IN_PLACE
        override val defaultArgs: List<ToolArg> = emptyList()
        override val supportsStdin: Boolean = false

        /** `supportsFix` is N/A — this *is* the fix path. Matches the
         *  CS-Fixer port's `FormatMode.supportsFix = false` convention. */
        override val supportsFix: Boolean = false

        override val pathArgKeys: Set<String> = setOf("--config=")
    }

    public companion object {
        public const val ID: String = "laravel-pint"

        /** Mirror of [LaravelPintOptionsSchema.MODE_FORMAT] so callers
         *  outside this module needn't import the schema class just to
         *  refer to the mode id. */
        public const val MODE_FORMAT: String = LaravelPintOptionsSchema.MODE_FORMAT

        /** Reader id of the CS-Fixer-style diff-XML reader Pint reuses
         *  once the on-the-fly mode lands. Hardcoded here so all the
         *  port files share one constant; the reader itself ships with
         *  the PHP-CS-Fixer port (phase 06). */
        public const val RESULT_READER_ID: String = "phpcsfixer-diff-xml"

        /** The legacy plugin's snake-case-with-capitals inspection
         *  short name. **Do not "fix" the casing.** */
        public const val LEGACY_INSPECTION_SHORT_NAME: String =
            "Laravel_Pint_validation_tool"
    }
}
