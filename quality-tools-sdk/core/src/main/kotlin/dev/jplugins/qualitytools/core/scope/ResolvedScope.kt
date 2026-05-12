package dev.jplugins.qualitytools.core.scope

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Output of `ScopeResolver`: the working directory and optional config
 * file used for the current run, plus a helper for path normalisation.
 */
public interface ResolvedScope {
    public val workspaceDir: String
    public val configFile: String?
        get() = null

    @ThreadingPolicy("any")
    public fun relativize(absolute: String): String
}
