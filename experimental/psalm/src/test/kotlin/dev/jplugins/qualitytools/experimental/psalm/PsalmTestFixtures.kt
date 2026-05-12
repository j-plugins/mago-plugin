package dev.jplugins.qualitytools.experimental.psalm

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

/**
 * Minimal in-test fixtures so each test file doesn't reimplement
 * a fake [ToolRunContext] / [ResolvedScope] / [ToolTarget].
 */
internal class FakeRunContext(
    override val tool: QualityTool,
    override val options: OptionsBag,
    override val scope: ResolvedScope = FakeScope,
) : ToolRunContext {
    override val projectId: String = "test-project"
    override val basePath: String? = "/proj"
    override val profile: ConfigProfile
        get() = error("ConfigProfile not used by PsalmBuildArgs")
    override val cancellation: CancellationToken = CancellationToken.Never
    override val logger: QtLogger = QtLogger.NoOp
}

internal object FakeScope : ResolvedScope {
    override val workspaceDir: String = "/proj"
    override fun relativize(absolute: String): String = absolute
}

internal class FakeTarget(private val path: String) : ToolTarget {
    override val normalizedPath: String = path
    override fun toCliArg(scope: ResolvedScope): ToolArg = pathArg(path)
}
