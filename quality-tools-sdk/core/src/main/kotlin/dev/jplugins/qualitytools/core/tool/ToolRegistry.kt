package dev.jplugins.qualitytools.core.tool

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Lookup over the registered [QualityTool]s. Interface lives in `:core`
 * so plugin authors (and `:testing` fakes) can substitute without an
 * IntelliJ classpath; `:ui` ships `EpToolRegistry`, the EP-backed impl.
 *
 * Required by phase 02 (`ConfigSourceRegistry` cross-refs `toolId`),
 * phase 04 (storage resolves profiles by tool id), and phase 08 (the
 * annotator enumerates supported tools per language).
 */
public interface ToolRegistry {
    @ThreadingPolicy("any")
    public fun byId(id: String): QualityTool?

    @ThreadingPolicy("any")
    public fun all(): List<QualityTool>

    /** Tools whose `supportedLanguageIds` contains [languageId]. */
    @ThreadingPolicy("any")
    public fun byLanguageId(languageId: String): List<QualityTool>
}
