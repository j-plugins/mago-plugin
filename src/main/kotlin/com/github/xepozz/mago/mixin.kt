package com.github.xepozz.mago

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import org.toml.lang.psi.TomlLiteral

val TomlLiteral.contents
    get() = text.removeSurrounding("\"")

val TomlLiteral.contentRange
    get() = TextRange(1, textLength - 1)

fun String.normalizePath() =
    removePrefix("\\\\?\\")
        .let { FileUtil.toSystemIndependentName(it) }