package dev.jplugins.qualitytools.phpcsfixer

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

/** Bare-bones run context for [PhpCsFixerBuildArgs] tests. */
internal class TestRunContext(
    override val tool: QualityTool,
    override val options: OptionsBag,
    override val scope: ResolvedScope = TestScope,
) : ToolRunContext {
    override val projectId: String = "test"
    override val basePath: String? = "/proj"
    override val profile: ConfigProfile
        get() = error("not needed for buildArgs tests")
    override val cancellation: CancellationToken = CancellationToken.Never
    override val logger: QtLogger = QtLogger.NoOp
}

internal object TestScope : ResolvedScope {
    override val workspaceDir: String = "/proj"
    override fun relativize(absolute: String): String =
        if (absolute.startsWith("/proj/")) absolute.removePrefix("/proj/") else absolute
}

internal class TestTarget(private val path: String = "/proj/src/Foo.php") : ToolTarget {
    override val normalizedPath: String = path
    override fun toCliArg(scope: ResolvedScope): ToolArg = pathArg(path)
}

/** Convenience: empty bag for the schema's defaults. */
internal fun emptyBag(): OptionsBag = MapOptionsBag()
