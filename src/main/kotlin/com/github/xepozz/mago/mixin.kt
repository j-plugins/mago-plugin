package com.github.xepozz.mago

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.toml.lang.psi.TomlLiteral

val TomlLiteral.contents
    get() = text.removeSurrounding("\"")

val TomlLiteral.contentRange
    get() = TextRange(1, textLength - 1)

/**
 * Normalizes a path for comparison: long path prefix, system-independent slashes,
 * and WSL UNC variants. Both `//wsl$/` and `//wsl.localhost/` refer to the same
 * WSL filesystem; canonicalize to one form so file-vs-config and workspace mapping
 * comparisons work regardless of which form the IDE or user supplies.
 */
fun String.normalizePath(): String {
    val withSlashes = removePrefix("\\\\?\\").let { FileUtil.toSystemIndependentName(it) }
    return if (withSlashes.startsWith("//wsl$/")) {
        "//wsl.localhost/" + withSlashes.removePrefix("//wsl$/")
    } else {
        withSlashes
    }
}

/**
 * Converts a path to the form used when passing to Mago (or the PHP path mapper).
 * WSL paths in `//wsl.localhost/` form are rewritten to `//wsl$/` so they are
 * found correctly; the `//wsl$/` form is what works for both local Mago and
 * remote path mapping on Windows.
 */
fun String.toPathForExecution(): String {
    val withSlashes = removePrefix("\\\\?\\").let { FileUtil.toSystemIndependentName(it) }
    return if (withSlashes.startsWith("//wsl.localhost/")) {
        "//wsl$/" + withSlashes.removePrefix("//wsl.localhost/")
    } else {
        withSlashes
    }
}

fun String.findVirtualFile(): VirtualFile? =
    LocalFileSystem.getInstance().findFileByPath(this)
