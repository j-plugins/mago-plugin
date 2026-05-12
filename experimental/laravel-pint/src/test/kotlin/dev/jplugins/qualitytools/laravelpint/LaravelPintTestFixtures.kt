package dev.jplugins.qualitytools.laravelpint

import dev.jplugins.qualitytools.core.context.CancellationToken
import dev.jplugins.qualitytools.core.context.QtLogger
import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.profile.ConfigProfile
import dev.jplugins.qualitytools.core.scope.ResolvedScope
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.pathArg
import dev.jplugins.qualitytools.testing.MapOptionsBag

/**
 * Test-only fixtures shared by every Laravel-Pint test in this module.
 * Kept package-private (internal) to the test source set.
 *
 * Mirrors the inline fixtures used by `QualityToolBuilderTest` in
 * `:quality-tools-sdk:core`.
 */
internal object NoopScope : ResolvedScope {
    override val workspaceDir: String = "/proj"
    override fun relativize(absolute: String): String =
        absolute.removePrefix("$workspaceDir/")
}

internal class FakeTarget(
    override val normalizedPath: String,
) : ToolTarget {
    override fun toCliArg(scope: ResolvedScope): ToolArg = pathArg(normalizedPath)
}

/**
 * Minimal `ToolRunContext` impl: backed by a `MapOptionsBag` for
 * options, with the rest of the seam-values stubbed out. Tests that
 * need `profile` must override `profileOverride` (default throws,
 * mirroring the SDK's own fixture).
 */
internal class FakeRunContext(
    override val tool: QualityTool,
    override val options: OptionsBag = MapOptionsBag(),
    private val profileOverride: ConfigProfile? = null,
) : ToolRunContext {
    override val projectId: String = "test-project"
    override val basePath: String? = "/proj"
    override val profile: ConfigProfile
        get() = profileOverride
            ?: error("FakeRunContext: profile not configured for this test")
    override val scope: ResolvedScope = NoopScope
    override val cancellation: CancellationToken = CancellationToken.Never
    override val logger: QtLogger = QtLogger.NoOp
}
