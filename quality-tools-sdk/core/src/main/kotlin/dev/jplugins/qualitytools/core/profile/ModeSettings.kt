package dev.jplugins.qualitytools.core.profile

public interface ModeSettings {
    public val enabled: Boolean
        get() = true
    public val additionalArgs: String
        get() = ""
    public val customConfigFile: String?
        get() = null
}

/** Plain data carrier. */
public data class SimpleModeSettings(
    override val enabled: Boolean = true,
    override val additionalArgs: String = "",
    override val customConfigFile: String? = null,
) : ModeSettings
