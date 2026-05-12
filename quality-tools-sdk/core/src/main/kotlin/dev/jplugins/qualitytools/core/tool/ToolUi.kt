package dev.jplugins.qualitytools.core.tool

/**
 * Visibility hint for `AutoToolSettingsPanel`. `Hidden` removes the tool
 * from Settings; the tool still runs (useful for CI-only / programmatic).
 */
public interface ToolUi {
    public companion object {
        public val Default: ToolUi = object : ToolUi {}
        public val Hidden: ToolUi = object : ToolUi {}
    }
}
