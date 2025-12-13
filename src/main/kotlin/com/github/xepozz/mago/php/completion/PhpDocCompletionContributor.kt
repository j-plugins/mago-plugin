package com.github.xepozz.mago.php.completion

import com.github.xepozz.mago.MagoIcons
import com.github.xepozz.mago.formatter.Directives
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.PhpPsiElement

class PhpDocCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .withParent(PhpPsiElement::class.java)
                .withSuperParent(
                    2,
                    PlatformPatterns.psiElement(PhpDocTag::class.java)
                ),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val element = parameters.position
                    val parent = element.parent as? PhpPsiElement ?: return
                    val phpdoc = parent.parent as? PhpDocTag ?: return
                    println("phpdoc.name: ${phpdoc.name} eee")
                    if (!PHPDOCS.contains(phpdoc.name)) return

                    if (phpdoc.firstChild == element) return

                    val categoryElement = parent.firstChild
                    if (categoryElement == element) {
                        ENTRIES
                            .keys
                            .map {
                                LookupElementBuilder.create(it)
                                    .withIcon(MagoIcons.MAGO)
                                    .bold()
                            }
                            .apply { result.addAllElements(this) }
                        return
                    }

                    val text = categoryElement.text
                    val category = text.substringBefore(':')

                    println("new category: $category")

                    ENTRIES[category]
                        ?.let { directives ->
                            directives
                                .map {
                                    LookupElementBuilder.create(it.first)
                                        .withTailText(" ${it.second}")
                                        .withIcon(MagoIcons.MAGO)
                                        .bold()
                                }
                                .apply { result.addAllElements(this) }
                        }
                }
            }
        )
    }

    companion object {
        val ENTRIES = mapOf(
            "lint" to Directives.entries.map { it.directive to it.description },
//            "analysis" to listOf("linter"),
        )
        val PHPDOCS = listOf("@mago-expect", "@mago-ignore")
    }
}
























