package com.github.xepozz.mago.reference

import com.github.xepozz.mago.MagoIcons
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class ConfigCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement()
                .withParent(TomlKeySegment::class.java)
                .withSuperParent(2, PlatformPatterns.psiElement(TomlKey::class.java))
                .withSuperParent(3, PlatformPatterns.psiElement(TomlTableHeader::class.java))
                .withSuperParent(4, PlatformPatterns.psiElement(TomlTable::class.java)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    results: CompletionResultSet
                ) {
                    val parent = parameters.position.parent.parent as? TomlKey ?: return

                    val prefix = parent.text.substring(0, parameters.offset - parent.textRange.startOffset)
                    results.withPrefixMatcher(prefix)
                        .apply {
                            ConfigStructure
                                .STRUCTURE
                                .keys
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
    }
}