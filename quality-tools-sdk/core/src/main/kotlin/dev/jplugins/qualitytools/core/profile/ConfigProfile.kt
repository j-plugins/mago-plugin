package dev.jplugins.qualitytools.core.profile

import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.scope.ConfigScope
import dev.jplugins.qualitytools.core.source.ConfigSource

/**
 * One row in the user's "Quality Tools / <Tool>" list. Equality on [id]
 * (SDK rule 11).
 */
public interface ConfigProfile {
    public val id: String
    public val displayName: String
    public val toolId: String
    public val source: ConfigSource
    public val scope: ConfigScope
    public val options: OptionsBag
    public val modes: Map<String, ModeSettings>

    /** Runs longer than this kill the process. Storage seeds it from
     *  `ConfigSourceType.defaultTimeoutMs` (Tier-1 patch G2) on profile
     *  creation. */
    public val timeoutMs: Long
        get() = 30_000L
}
