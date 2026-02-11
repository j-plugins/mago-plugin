package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.MagoIcons
import com.github.xepozz.mago.config.MagoConfigSchemaUtil
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.findParentOfType
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class ConfigCompletionContributor : CompletionContributor() {
    init {
        // Inside [ ] or [[ ]] — complete section name only (brackets already present)
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withParent(TomlKeySegment::class.java)
                .withSuperParent(2, PlatformPatterns.psiElement(TomlKey::class.java))
                .withSuperParent(3, PlatformPatterns.psiElement(TomlTableHeader::class.java))
                .withSuperParent(4, PlatformPatterns.psiElement(TomlHeaderOwner::class.java)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    results: CompletionResultSet
                ) {
                    val header = parameters.position.findParentOfType<TomlTableHeader>() ?: return
                    val doubleBracketMode = header.text.startsWith("[[")
                    val ctx = getSectionCompletionContext(parameters, doubleBracketMode) ?: return
                    results.withPrefixMatcher(ctx.prefix)
                        .apply {
                            ctx.sectionNames
                                .map {
                                    LookupElementBuilder.create(it)
                                        .withIcon(MagoIcons.MAGO)
                                        .bold()
                                }
                                .apply { addAllElements(this) }
                        }
                }
            }
        )
        // At the top level (key not inside any [section]) — complete section name and insert as [section] or [[section]]
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withParent(TomlKeySegment::class.java)
                .withSuperParent(2, PlatformPatterns.psiElement(TomlKey::class.java))
                .withSuperParent(3, PlatformPatterns.psiElement(TomlKeyValue::class.java)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    results: CompletionResultSet
                ) {
                    val ctx = getSectionCompletionContext(parameters, null) ?: return
                    val project = parameters.editor.project ?: parameters.position.project
                    val descriptions = MagoConfigSchemaUtil.getSectionDescriptions(project)
                    results.withPrefixMatcher(ctx.prefix)
                        .apply {
                            ctx.sectionNames
                                .map { sectionName ->
                                    val desc = descriptions[sectionName]?.let { truncateForTail(it) }
                                    LookupElementBuilder.create(sectionName)
                                        .withIcon(MagoIcons.MAGO)
                                        .bold()
                                        .withInsertHandler(SectionHeaderInsertHandler(sectionName))
                                        .let { if (desc != null) it.withTailText(" $desc", true) else it }
                                }
                                .apply { addAllElements(this) }
                        }
                    val allSectionNames = ConfigStructure.STRUCTURE.keys
                    val existingTopLevelKeys = getExistingTopLevelKeys(parameters)
                    results.runRemainingContributors(parameters) { result ->
                        val name = result.lookupElement.lookupString
                        if (name in allSectionNames || name in existingTopLevelKeys) return@runRemainingContributors
                        results.addElement(result.lookupElement)
                    }
                }
            }
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withParent(TomlKeySegment::class.java)
                .withSuperParent(2, PlatformPatterns.psiElement(TomlKey::class.java))
                .withSuperParent(3, PlatformPatterns.psiElement(TomlKeyValue::class.java))
                .withSuperParent(
                    4,
                    PlatformPatterns.psiElement(TomlHeaderOwner::class.java)
                ),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    results: CompletionResultSet
                ) {
                    val element = parameters.position.parent as? TomlKeySegment ?: return
                    val table = element.findParentOfType<TomlHeaderOwner>() ?: return
                    val key = table.header.key?.text ?: return
                    val existingKeys = getExistingKeysInTable(table)
                    val suggestedKeys = ConfigStructure.STRUCTURE[key]?.filter { it !in existingKeys }.orEmpty()

                    suggestedKeys
                        .map {
                            LookupElementBuilder.create(it)
                                .withIcon(MagoIcons.MAGO)
                                .bold()
                        }
                        .apply { results.addAllElements(this) }
                }
            }
        )
    }
}

private data class SectionCompletionContext(
    val parent: TomlKey,
    val prefix: String,
    val sectionNames: List<String>,
)

private fun getSectionCompletionContext(
    parameters: CompletionParameters,
    doubleBracketMode: Boolean?,
): SectionCompletionContext? {
    val parent = parameters.position.parent.parent as? TomlKey ?: return null
    val prefix = parent.text.substring(0, parameters.offset - parent.textRange.startOffset)
    val existingSections = getExistingSectionNames(parameters)
    val sectionNames = when (doubleBracketMode) {
        true -> ConfigStructure.STRUCTURE.keys
            .filter { it in ConfigStructure.SECTIONS_WITH_DOUBLE_BRACKETS && it !in existingSections }

        false -> ConfigStructure.STRUCTURE.keys
            .filter { it !in ConfigStructure.SECTIONS_WITH_DOUBLE_BRACKETS && it !in existingSections }

        null -> ConfigStructure.STRUCTURE.keys.filter { it !in existingSections }
    }
    return SectionCompletionContext(parent, prefix, sectionNames)
}

private fun getExistingSectionNames(parameters: CompletionParameters): Set<String> {
    val file = parameters.position.containingFile as? TomlFile ?: return emptySet()
    return file.children
        .filterIsInstance<TomlTable>()
        .mapNotNull { table ->
            table.header.text?.let { t ->
                when {
                    t.startsWith("[[") && t.endsWith("]]") -> t.removeSurrounding("[[", "]]")
                    else -> t.removeSurrounding("[", "]")
                }
            }
        }
        .toSet()
}

private fun getExistingTopLevelKeys(parameters: CompletionParameters): Set<String> {
    val file = parameters.position.containingFile as? TomlFile ?: return emptySet()
    return file.children
        .filterIsInstance<TomlKeyValue>()
        .mapNotNull { it.key.text }
        .toSet()
}

private fun getExistingKeysInTable(table: TomlHeaderOwner): Set<String> {
    val tomlTable = table as? TomlTable ?: return emptySet()
    return tomlTable.entries
        .mapNotNull { it.key.text }
        .toSet()
}

private fun truncateForTail(description: String, maxLen: Int = 80): String {
    val trimmed = description.trim()
    return if (trimmed.length <= maxLen) trimmed else trimmed.take(maxLen).trimEnd() + "..."
}

private class SectionHeaderInsertHandler(private val sectionName: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val brackets =
            if (sectionName in ConfigStructure.SECTIONS_WITH_DOUBLE_BRACKETS) "[[$sectionName]]" else "[$sectionName]"
        context.document.replaceString(context.startOffset, context.tailOffset, brackets)
    }
}
