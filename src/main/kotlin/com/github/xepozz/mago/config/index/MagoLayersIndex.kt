package com.github.xepozz.mago.config.index

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

class MagoLayersIndex : FileBasedIndexExtension<String, LayerIndexValue>() {
    companion object {
        val KEY = ID.create<String, LayerIndexValue>("MAGO.LAYERS")
    }

    override fun getName(): ID<String, LayerIndexValue> = KEY

    override fun getIndexer(): DataIndexer<String, LayerIndexValue, FileContent> = DataIndexer { input ->
        val result = hashMapOf<String, LayerIndexValue>()
        val psi = input.psiFile
        if (psi !is TomlFile) return@DataIndexer result

        val tables = psi.children.filterIsInstance<TomlTable>()
        val targetTables = tables.filter { table -> table.header.text == "[guard.perimeter.layers]" }

        for (table in targetTables) {
            for (kv in table.entries) {
                val keyName = kv.key.text ?: continue
                val value = kv.value
                val array = value as? TomlArray ?: continue
                val parts = array.elements.filterIsInstance<TomlLiteral>().map { StringUtil.unquoteString(it.text) }
                val range = kv.key.textRange

                result[keyName] = LayerIndexValue(parts, range.startOffset, range.length)
            }
        }
        result
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<LayerIndexValue> = LayerIndexValueExternalizer

    override fun getInputFilter() = FileBasedIndex.InputFilter { it.name.equals("mago.toml", ignoreCase = true) }

    override fun dependsOnFileContent() = true

    override fun getVersion() = 1
}
