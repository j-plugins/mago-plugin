package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.context.ThreadingPolicy
import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.options.OptionsSchema

/**
 * Root contract for one quality tool. Registered via the
 * `dev.jplugins.qualityTools.tool` extension point in `:ui`'s master
 * `quality-tools-eps.xml` (phase 07).
 *
 * Tier-1 patches carried here:
 *  - G1 — [binaryValidator] surfaces the "Validate" button
 *  - implicit relation with G8 — `ResolvedBinary.detectedVersion`
 *    populated when the validator returns one, readable by [buildArgs]
 *  - G9 — see `ToolMode.resultReaderId`
 *
 * Identity discipline: tools are equal if `id`s are equal.
 */
public interface QualityTool {
    public val id: String
    public val displayName: String
    public val supportedLanguageIds: Set<String>
    public val modes: List<ToolMode>
    public val capabilities: Set<String>
        get() = emptySet()
    public val acceptedSourceTypeIds: Set<String>
        get() = setOf("*")

    /** Default reader id; per-mode override via `ToolMode.resultReaderId`
     *  takes precedence (Tier-1 patch G9). */
    public val resultReaderId: String

    public val optionsSchema: OptionsSchema

    /**
     * Tier-1 patch G1.
     *
     * Optional validator wired to the "Validate" button in Settings.
     * Returned `ValidationResult.detectedVersion`, when present,
     * populates `ResolvedBinary.detectedVersion` (Tier-1 patch G8) so
     * [buildArgs] can branch on tool version.
     */
    public val binaryValidator: BinaryValidator?
        get() = null

    /**
     * Inspection short-names this tool publishes. Used by the legacy
     * `QualityToolType` bridge in phase 10a.1 to preserve existing
     * user inspection profiles after migration.
     *
     * Default: `setOf("${id}.lint", "${id}.batch")`. Tools whose legacy
     * short-names are not regular (Pint's `Laravel_Pint_validation_tool`,
     * phpmd's single-name `MessDetectorValidation`) override.
     */
    public val inspectionShortNames: Set<String>
        get() = setOf("${id}.lint", "${id}.batch")

    public val ui: ToolUi
        get() = ToolUi.Default

    /**
     * Construct command-line arguments for one run. Pure function of
     * context, mode, and target. May read `ctx.options[...]`,
     * `ctx.scope`, `ctx.profile.source` (e.g. for `detectedVersion`-aware
     * branching).
     */
    @ThreadingPolicy("background")
    public fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg>
}
