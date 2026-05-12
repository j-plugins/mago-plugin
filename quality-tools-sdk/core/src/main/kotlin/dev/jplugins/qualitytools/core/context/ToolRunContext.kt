package dev.jplugins.qualitytools.core.context

import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.profile.ConfigProfile
import dev.jplugins.qualitytools.core.scope.ResolvedScope
import dev.jplugins.qualitytools.core.tool.QualityTool

/**
 * Everything below the annotator sees this. No `Project`, no PSI; only
 * the seam values needed for `buildArgs`, `read`, `resolve`.
 *
 * Carries `displayPath` vs `actualPath` to support the stdin-via-tempfile
 * rename scenario: the tool sees `actualPath`, the user sees `displayPath`
 * in messages.
 */
public interface ToolRunContext {
    public val projectId: String
    public val basePath: String?

    public val tool: QualityTool
    public val profile: ConfigProfile
    public val scope: ResolvedScope
    public val options: OptionsBag

    /** The path shown to the user in messages. May equal [actualPath]. */
    public val displayPath: String?
        get() = null

    /** The path actually fed to the tool. May be a tempfile when stdin-fallback is used. */
    public val actualPath: String?
        get() = displayPath

    public val cancellation: CancellationToken
    public val logger: QtLogger
}
