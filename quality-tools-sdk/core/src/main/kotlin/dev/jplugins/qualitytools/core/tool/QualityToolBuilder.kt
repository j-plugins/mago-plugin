@file:JvmName("QualityToolDsl")

package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.context.ThreadingPolicy
import dev.jplugins.qualitytools.core.context.ToolRunContext
import dev.jplugins.qualitytools.core.options.OptionsSchema

/**
 * Sugar so the smallest-tool case fits in 4 LOC.
 *
 *     val pest: QualityTool = qualityTool("pest") {
 *         displayName = "Pest"
 *         languages("PHP")
 *         resultReaderId = "pest-junit-xml"
 *         optionsSchema = MyPestOptionsSchema()
 *         mode("test") { verb = "test" }
 *         buildArgs { ctx, mode, target -> mode.defaultArgs + target.toCliArg(ctx.scope) }
 *     }
 *
 * Without the DSL, the user-facing surface of `QualityTool` requires
 * overriding ≥ 6 vals + buildArgs; with it, only the named fields and
 * `buildArgs { }` block are required.
 */
@ThreadingPolicy("any")
public fun qualityTool(id: String, build: QualityToolBuilder.() -> Unit): QualityTool {
    val b = QualityToolBuilder(id).apply(build)
    return b.build()
}

/**
 * Mutable builder consumed by [qualityTool]. Intentionally NOT a
 * `QualityTool` itself — the produced object is immutable. Open class
 * so plugin authors can subclass with extra defaults.
 */
public open class QualityToolBuilder(public val id: String) {
    public var displayName: String = id
    public var supportedLanguageIds: MutableSet<String> = linkedSetOf()
    public var capabilities: MutableSet<String> = linkedSetOf()
    public var acceptedSourceTypeIds: MutableSet<String> = mutableSetOf("*")
    public var resultReaderId: String? = null
    public var optionsSchema: OptionsSchema? = null
    public var binaryValidator: BinaryValidator? = null
    public var inspectionShortNames: MutableSet<String> = linkedSetOf()
    public var ui: ToolUi = ToolUi.Default
    private val modes: MutableList<ToolMode> = mutableListOf()
    private var buildArgsFn:
        (ToolRunContext, ToolMode, ToolTarget) -> List<ToolArg> =
            { _, mode, _ -> mode.defaultArgs }

    public fun languages(vararg ids: String) {
        supportedLanguageIds.addAll(ids)
    }

    public fun capabilities(vararg caps: String) {
        capabilities.addAll(caps)
    }

    public fun acceptedSources(vararg typeIds: String) {
        acceptedSourceTypeIds.clear()
        acceptedSourceTypeIds.addAll(typeIds)
    }

    /** Define one mode. Subsequent calls append; order is preserved. */
    public fun mode(id: String, build: ToolModeBuilder.() -> Unit = {}) {
        modes += ToolModeBuilder(id).apply(build).build()
    }

    /** Set the `buildArgs` lambda. Called once during `build()`. */
    public fun buildArgs(
        fn: (ToolRunContext, ToolMode, ToolTarget) -> List<ToolArg>,
    ) {
        buildArgsFn = fn
    }

    public open fun build(): QualityTool {
        val schema = optionsSchema ?: EmptyOptionsSchema(id)
        val reader = resultReaderId
            ?: error("qualityTool(\"$id\") { ... } requires resultReaderId to be set")
        val shortNames =
            if (inspectionShortNames.isNotEmpty()) inspectionShortNames.toSet()
            else setOf(
                "${id.replaceFirstChar { it.uppercase() }}Lint",
                "${id.replaceFirstChar { it.uppercase() }}Batch",
            )
        return BuiltQualityTool(
            id = id,
            displayName = displayName,
            supportedLanguageIds = supportedLanguageIds.toSet(),
            modes = modes.toList(),
            capabilities = capabilities.toSet(),
            acceptedSourceTypeIds = acceptedSourceTypeIds.toSet(),
            resultReaderId = reader,
            optionsSchema = schema,
            binaryValidator = binaryValidator,
            inspectionShortNames = shortNames,
            ui = ui,
            buildArgsFn = buildArgsFn,
        )
    }
}

public open class ToolModeBuilder(public val id: String) {
    public var displayName: String = id
    public var verb: String = ""
    public var executionStyle: String = ExecutionStyles.ON_THE_FLY
    public var defaultArgs: MutableList<ToolArg> = mutableListOf()
    public var supportsStdin: Boolean = false
    public var supportsFix: Boolean = false
    public var resultReaderId: String? = null
    public var formattingOutputMode: String = FormattingOutputModes.STDOUT
    public var pathArgKeys: MutableSet<String> = linkedSetOf()

    public open fun build(): ToolMode = BuiltToolMode(
        id = id,
        displayName = displayName,
        verb = verb,
        executionStyle = executionStyle,
        defaultArgs = defaultArgs.toList(),
        supportsStdin = supportsStdin,
        supportsFix = supportsFix,
        resultReaderId = resultReaderId,
        formattingOutputMode = formattingOutputMode,
        pathArgKeys = pathArgKeys.toSet(),
    )
}

private class BuiltQualityTool(
    override val id: String,
    override val displayName: String,
    override val supportedLanguageIds: Set<String>,
    override val modes: List<ToolMode>,
    override val capabilities: Set<String>,
    override val acceptedSourceTypeIds: Set<String>,
    override val resultReaderId: String,
    override val optionsSchema: OptionsSchema,
    override val binaryValidator: BinaryValidator?,
    override val inspectionShortNames: Set<String>,
    override val ui: ToolUi,
    private val buildArgsFn: (ToolRunContext, ToolMode, ToolTarget) -> List<ToolArg>,
) : QualityTool {
    override fun buildArgs(
        ctx: ToolRunContext,
        mode: ToolMode,
        target: ToolTarget,
    ): List<ToolArg> = buildArgsFn(ctx, mode, target)

    override fun equals(other: Any?): Boolean = other is QualityTool && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "QualityTool($id)"
}

private class BuiltToolMode(
    override val id: String,
    override val displayName: String,
    override val verb: String,
    override val executionStyle: String,
    override val defaultArgs: List<ToolArg>,
    override val supportsStdin: Boolean,
    override val supportsFix: Boolean,
    override val resultReaderId: String?,
    override val formattingOutputMode: String,
    override val pathArgKeys: Set<String>,
) : ToolMode

/** No-op schema used when a tool author hasn't supplied one. */
private class EmptyOptionsSchema(override val toolId: String) : OptionsSchema {
    override val specs: List<dev.jplugins.qualitytools.core.options.OptionSpec<*>> = emptyList()
}
