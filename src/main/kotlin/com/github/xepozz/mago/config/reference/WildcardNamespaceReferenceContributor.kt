package com.github.xepozz.mago.config.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTableHeader

class WildcardNamespaceReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(TomlLiteral::class.java)
                .withParent(TomlArray::class.java)
                .withSuperParent(
                    2,
                    PlatformPatterns.psiElement(TomlKeyValue::class.java)
                        .withParent(
                            PlatformPatterns.psiElement(TomlHeaderOwner::class.java)
                                .withFirstChild(
                                    PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                        .withText("[guard.perimeter.layers]")
                                )
                        )
                ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val element = element as TomlLiteral

                    return arrayOf(PhpWildcardNamespaceReference(element))
                }
            }
        )
    }
}