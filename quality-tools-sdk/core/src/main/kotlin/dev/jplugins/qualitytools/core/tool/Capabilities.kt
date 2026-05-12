package dev.jplugins.qualitytools.core.tool

/**
 * Recognised string constants for [QualityTool.capabilities].
 *
 * The set is open (SDK rule 2): tools may add their own values
 * (e.g. `"mutation-test"`, `"baseline"`) without coordinating with
 * the SDK; the UI handles unknown values by ignoring them.
 */
public object Capabilities {
    public const val LINT: String = "lint"
    public const val ANALYZE: String = "analyze"
    public const val FIX: String = "fix"
    public const val FORMAT: String = "format"
    public const val BASELINE: String = "baseline"
    public const val BATCH: String = "batch"
    public const val INSPECT: String = "inspect"
}
