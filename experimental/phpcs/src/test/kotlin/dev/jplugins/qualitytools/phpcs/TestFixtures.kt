package dev.jplugins.qualitytools.phpcs

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
 * Minimal fake `ToolRunContext` for unit tests.
 *
 * `profile` is deliberately `error(...)` because `buildArgs` doesn't
 * read it in this scope; if a test grows to need it, override here.
 */
internal class FakeRunContext(
    override val tool: QualityTool,
    override val options: OptionsBag,
    override val scope: ResolvedScope = NoopScope,
) : ToolRunContext {
    override val projectId: String = "p"
    override val basePath: String? = null
    override val profile: ConfigProfile
        get() = error("profile not modelled in this test")
    override val cancellation: CancellationToken = CancellationToken.Never
    override val logger: QtLogger = QtLogger.NoOp
}

internal object NoopScope : ResolvedScope {
    override val workspaceDir: String = "/"
    override fun relativize(absolute: String): String = absolute
}

internal class FakeTarget(private val path: String = "/proj/src/Foo.php") : ToolTarget {
    override val normalizedPath: String = path
    override fun toCliArg(scope: ResolvedScope): ToolArg = pathArg(path)
}
