package com.github.xepozz.mago.config.reference

object ConfigStructure {
    /** Section names that use TOML array-of-tables syntax `[[section]]` and can be defined multiple times. */
    val SECTIONS_WITH_DOUBLE_BRACKETS = setOf("guard.perimeter.rules", "guard.structural.rules")

    /**
     * Section and key names aligned with mago config docs (configuration.md, tool config references).
     * We keep this in addition to `mago config --schema` because:
     * - The schema drives rich completions (descriptions, examples) via JSON Schema when available.
     * - This list is used for section/key completion and references when the schema is not yet loaded,
     *   and to insert proper TOML section headers (`[section]` or `[[section]]`) when completing at top level.
     */
    val STRUCTURE = mapOf(
        "source" to listOf(
            "paths",
            "includes",
            "excludes",
            "extensions",
        ),
        "parser" to listOf(
            "enable-short-tags",
        ),
        "formatter" to listOf(
            "preset",
            "excludes",
            "print-width",
            "tab-width",
            "use-tabs",
            "end-of-line",
            "single-quote",
            "trailing-comma",
            "indent-heredoc",
            "remove-trailing-close-tag",
        ),
        "analyzer" to listOf(
            "excludes",
            "ignore",
            "baseline",
            "baseline-variant",
            *AnalyzerOptions.OPTIONS.toTypedArray(),
            *AnalyzerFeatureFlags.FEATURES.toTypedArray(),
        ),
        "linter" to listOf(
            "excludes",
            "integrations",
            "baseline",
            "baseline-variant",
        ),
        "linter.rules" to listOf(
            "ambiguous-function-call",
            "literal-named-argument",
            "halstead",
            "prefer-static-closure",
            "no-else-clause",
            "cyclomatic-complexity",
        ),
        "guard" to listOf(
            "mode",
            "excludes",
            "baseline",
            "baseline-variant",
        ),
        "guard.perimeter" to listOf(
            "layering",
        ),
        "guard.perimeter.layers" to listOf(),
        "guard.perimeter.rules" to listOf(
            "namespace",
            "permit",
        ),
        "guard.structural.rules" to listOf(
            "on",
            "not-on",
            "target",
            "must-be",
            "must-be-named",
            "must-be-final",
            "must-be-abstract",
            "must-be-readonly",
            "must-implement",
            "must-extend",
            "must-use-trait",
            "must-use-attribute",
            "reason",
        ),
    )
}

