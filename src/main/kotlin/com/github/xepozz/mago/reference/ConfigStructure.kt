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
            "find-unused-definitions",
            "find-unused-expressions",
            "analyze-dead-code",
            "check-throws",
            "allow-possibly-undefined-array-keys",
            "perform-heuristic-checks",
        ),
        "linter" to listOf(
            "integrations"
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
        "guard.perimeter.rules" to listOf(
            "namespace",
            "permit",
        ),
    )
}

