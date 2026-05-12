package dev.jplugins.qualitytools.core.tool

/**
 * One mode of a tool — `analyze`, `lint`, `format`, `fix`, `guard`, etc.
 * A tool may have several. A profile chooses which modes are enabled.
 *
 * Tier-1 patches G9 + (companion to G1):
 *  - [resultReaderId] — per-mode override of the tool-level reader id.
 *    Resolution order: `mode.resultReaderId ?: tool.resultReaderId`.
 *  - [formattingOutputMode] — for `executionStyle = "format"`, which
 *    discriminates `stdout` rewrite vs `in_place` re-read in
 *    `AsyncFormattingServiceAdapter` (phase 07).
 */
public interface ToolMode {
    public val id: String
    public val displayName: String
        get() = id
    public val verb: String
        get() = ""

    /**
     * Execution style. Free string so future modes are extensible.
     * Bundled values in [ExecutionStyles].
     */
    public val executionStyle: String
        get() = ExecutionStyles.ON_THE_FLY

    public val defaultArgs: List<ToolArg>
        get() = emptyList()

    public val supportsStdin: Boolean
        get() = false

    public val supportsFix: Boolean
        get() = false

    /** Tier-1 patch G9 — per-mode override of `QualityTool.resultReaderId`. */
    public val resultReaderId: String?
        get() = null

    /**
     * Tier-1 patch (companion to G12/format-mode story).
     *
     * Only meaningful when `executionStyle == "format"`. Either
     * `"stdout"` (adapter reads stdout as new doc text) or `"in_place"`
     * (adapter re-reads the temp file).
     *
     * Default `"stdout"`.
     */
    public val formattingOutputMode: String
        get() = FormattingOutputModes.STDOUT

    /**
     * Keys that introduce a path value in [defaultArgs], so the
     * path-aware rewriter knows what to remap. E.g. `setOf("--config")`
     * for `--config=/abs/path`.
     */
    public val pathArgKeys: Set<String>
        get() = emptySet()
}

/** Recognised constants for [ToolMode.executionStyle]. */
public object ExecutionStyles {
    public const val ON_THE_FLY: String = "on_the_fly"
    public const val ON_SAVE: String = "on_save"
    public const val MANUAL: String = "manual"
    public const val BATCH: String = "batch"
    public const val FORMAT: String = "format"
}

/** Recognised constants for [ToolMode.formattingOutputMode]. */
public object FormattingOutputModes {
    public const val STDOUT: String = "stdout"
    public const val IN_PLACE: String = "in_place"
}
