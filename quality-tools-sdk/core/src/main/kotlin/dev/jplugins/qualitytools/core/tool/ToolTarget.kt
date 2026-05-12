package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.scope.ResolvedScope

/**
 * What a single tool invocation operates on. Open interface — bundled
 * kinds in [ToolTargets].
 */
public interface ToolTarget {
    /** Path with forward slashes, absolute. */
    public val normalizedPath: String

    /** The cli token representing this target. Plugins build args from it. */
    public fun toCliArg(scope: ResolvedScope): ToolArg
}
