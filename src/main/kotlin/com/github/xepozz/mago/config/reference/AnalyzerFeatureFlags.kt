package com.github.xepozz.mago.config.reference

object AnalyzerFeatureFlags {
    val FEATURES = listOf(
        "find-unused-expressions",
        "find-unused-definitions",
        "analyze-dead-code",
        "memoize-properties",
        "allow-possibly-undefined-array-keys",
        "check-throws",
        "perform-heuristic-checks",
        "strict-list-index-checks",
    )
}