package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.contentRange
import com.github.xepozz.mago.contents
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTableHeader


class LayersReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(TomlLiteral::class.java)
                .withSuperParent(
                    2,
                    PlatformPatterns.psiElement(TomlKeyValue::class.java)
                        .withFirstChild(
                            PlatformPatterns.psiElement(TomlKey::class.java)
                                .withText("permit")
                        )
                        .withParent(
                            PlatformPatterns.psiElement(TomlHeaderOwner::class.java)
                                .withFirstChild(
                                    PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                        .withText("[[guard.perimeter.rules]]")
                                )
                        )
                ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val element = element as TomlLiteral

                    if (element.contents.startsWith(LAYER_PREFIX)) {
                        val contentRange = element.contentRange
                        return arrayOf(
                            LayerReference(
                                element,
                                TextRange(contentRange.startOffset + LAYER_PREFIX.length, contentRange.endOffset)
                            )
                        )
                    }

                    return arrayOf(LayersListReference(element))
                }
            }
        )
    }

    companion object {
        private const val ALL_PREFIX = "@all"
        private const val LAYER_PREFIX = "@layer:"
    }
}