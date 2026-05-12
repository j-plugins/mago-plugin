package dev.jplugins.qualitytools.experimental.phpstan

import dev.jplugins.qualitytools.core.context.CancellationToken
import dev.jplugins.qualitytools.core.context.QtLogger
import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag
import dev.jplugins.qualitytools.core.profile.ConfigProfile
import dev.jplugins.qualitytools.core.scope.ResolvedScope
import dev.jplugins.qualitytools.core.tool.QualityTool
import dev.jplugins.qualitytools.core.tool.ToolArg
import dev.jplugins.qualitytools.core.tool.ToolTarget
import dev.jplugins.qualitytools.core.tool.pathArg

/**
 * Test-only fixtures used across the PhpStan port tests. Hand-rolled
 * to keep the experimental tests independent of the `:testing` module
 * (the experimental tree might compile before `:testing` is wired).
 *
 * Every public symbol here is `internal` so it does not bleed into
 * downstream consumers if this package is ever published.
 */
internal class MapOptionsBag : OptionsBag {
    private val data = linkedMapOf<String, String>()
    private val modeBags = linkedMapOf<String, MapOptionsBag>()

    override fun <T : Any> get(spec: OptionSpec<T>): T {
        val raw = data[spec.key] ?: return spec.default
        return spec.decode(raw) ?: spec.default
    }

    override fun <T : Any> set(spec: OptionSpec<T>, value: T) {
        data[spec.key] = spec.encode(value)
    }

    override fun snapshot(): Map<String, String> = data.toMap()

    override fun mode(modeId: String): OptionsBag =
        modeBags.getOrPut(modeId) { MapOptionsBag() }

    override fun commit(): Unit = Unit
}

internal class FakeTarget(override val normalizedPath: String) : ToolTarget {
    override fun toCliArg(scope: ResolvedScope): ToolArg = pathArg(normalizedPath)
}

internal object FixedScope : ResolvedScope {
    override val workspaceDir: String = "/project"
    override val configFile: String? = null
    override fun relativize(absolute: String): String =
        absolute.removePrefix("$workspaceDir/")
}

internal class FakeRunContext(
    override val tool: QualityTool,
    override val options: OptionsBag,
) : ToolRunContext {
    override val projectId: String = "p"
    override val basePath: String? = "/project"
    override val profile: ConfigProfile
        get() = error("ConfigProfile is not needed in pure buildArgs tests")
    override val scope: ResolvedScope = FixedScope
    override val cancellation: CancellationToken = CancellationToken.Never
    override val logger: QtLogger = QtLogger.NoOp
}
