package com.github.xepozz.mago.intentions.apply

import com.github.xepozz.mago.model.MagoEdit
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

fun safetyLevel(safety: String): Int = when (safety) {
    "unsafe" -> 2
    "potentiallyunsafe" -> 1
    else -> 0
}

fun MagoEdit.maxSafetyLevel(): Int = replacements.maxOfOrNull { safetyLevel(it.safety) } ?: 0

/** Normalize the path for comparison so the edit path (e.g.: with ./) matches the IDE path. */
internal fun normalizePath(path: String): String = FileUtil.toCanonicalPath(path)

/** True if this edit applies to the given file (path or name match, paths normalized). */
internal fun editMatchesFile(edit: MagoEdit, filePath: String?, fileName: String): Boolean {
    if (filePath != null) {
        if (FileUtil.pathsEqual(normalizePath(filePath), normalizePath(edit.path))) return true
    }
    if (edit.name == fileName) return true
    val editLastName = Paths.get(edit.name).fileName?.toString()
    if (editLastName == fileName) return true
    if (edit.name.endsWith("/$fileName") || edit.name.endsWith("\\$fileName")) return true
    return false
}

/** Keep only replacements with exactly this safety level (safe=0, potentially unsafe=1, unsafe=2). */
fun filterEditsByExactSafety(edits: List<MagoEdit>, level: Int): List<MagoEdit> = edits
    .map { edit ->
        edit.copy(replacements = edit.replacements.filter { safetyLevel(it.safety) == level })
    }
    .filter { it.replacements.isNotEmpty() }
