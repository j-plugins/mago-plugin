package dev.jplugins.qualitytools.core.options

/**
 * Per-tool declarative options. Cycle-1 ruling: plain interface, no
 * abstract class. `ModeSchema` is separate so mode-level option groups
 * don't pretend to be full schemas.
 */
public interface OptionsSchema {
    public val toolId: String
    public val specs: List<OptionSpec<*>>
    public val modeSchemas: Map<String, ModeSchema>
        get() = emptyMap()
}

public interface ModeSchema {
    public val specs: List<OptionSpec<*>>
}
