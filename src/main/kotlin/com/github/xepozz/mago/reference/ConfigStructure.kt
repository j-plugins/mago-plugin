package com.github.xepozz.mago.reference

object ConfigStructure {
    val STRUCTURE = mapOf(
        "source" to listOf(
            "path",
            "includes",
            "excludes",
        ),
        "formatter" to listOf(
            "print-width",
            "tab-width",
            "use-tabs",
        ),
        "analyzer" to listOf(
            "excludes",
            "baseline",
            "ignore",
            *AnalyzerOptions.OPTIONS.toTypedArray(),
            *AnalyzerFeatureFlags.FEATURES.toTypedArray(),
        ),
        "linter" to listOf(
            "excludes",
            "integrations",
            "baseline",
        ),
        "linter.rules" to listOf(
            "ambiguous-function-call",
            "literal-named-argument",
            "halstead",
        ),
        "guard" to listOf(
            "excludes",
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

