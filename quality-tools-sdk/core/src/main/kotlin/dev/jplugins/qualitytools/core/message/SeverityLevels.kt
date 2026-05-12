package dev.jplugins.qualitytools.core.message

/**
 * Recognised string constants for `ToolMessage.severityLevel`.
 * Open by design (SDK rule 2 — strings, not enum). Unknown values
 * map to `WEAK_WARNING` in `:ui`'s `SeverityMapping` with a one-shot
 * log warning per (toolId, value).
 */
public object SeverityLevels {
    public const val ERROR: String = "error"
    public const val WARNING: String = "warning"
    public const val WEAK_WARNING: String = "weak_warning"
    public const val INFO: String = "info"
    public const val HINT: String = "hint"
    public const val INTERNAL_ERROR: String = "internal_error"
}
