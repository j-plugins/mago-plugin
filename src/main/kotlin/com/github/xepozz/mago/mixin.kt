package com.github.xepozz.mago

import com.intellij.openapi.util.TextRange
import org.toml.lang.psi.TomlLiteral

val TomlLiteral.contents
    get() = text.removeSurrounding("\"")

val TomlLiteral.contentRange
    get() = TextRange(1, textLength - 1)