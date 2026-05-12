package dev.jplugins.qualitytools.core.scope

import dev.jplugins.qualitytools.core.tool.ToolTarget

/**
 * Decides whether a [ConfigProfile] applies to a target file and how
 * specific the match is. The most-specific scope wins (longest-prefix
 * for workspace-root, glob length for glob, etc.).
 */
public interface ConfigScope {
    public val typeId: String

    public fun matches(target: ToolTarget, ctx: MatchContext): Boolean

    /** Higher = more specific. 0 means catch-all. */
    public fun specificity(target: ToolTarget, ctx: MatchContext): Int
}

public interface MatchContext {
    public val projectId: String
    public val basePath: String?
    public val vcsBranch: String?
        get() = null

    public fun moduleIdOf(target: ToolTarget): String? = null

    public fun attribute(key: String): String? = null
}
