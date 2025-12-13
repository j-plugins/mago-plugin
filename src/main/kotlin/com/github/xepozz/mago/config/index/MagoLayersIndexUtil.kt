package com.github.xepozz.mago.config.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

data class LayerDefinition(
    val file: VirtualFile,
    val values: List<String>,
    val range: TextRange,
)

object MagoLayersIndexUtil {
    fun getAll(project: Project): Map<String, List<LayerDefinition>> {
        val scope = GlobalSearchScope.projectScope(project)
        val index = FileBasedIndex.getInstance()
        val result = linkedMapOf<String, MutableList<LayerDefinition>>()

        for (key in index.getAllKeys(MagoLayersIndex.KEY, project)) {
            index.processValues(
                MagoLayersIndex.KEY,
                key,
                null,
                { file, value ->
                    result.getOrPut(key) { mutableListOf() }
                        .add(LayerDefinition(file, value.values, TextRange(value.offset, value.offset + value.length)))
                    true
                },
                scope
            )
        }
        return result
    }
}
