package com.github.xepozz.mago.config.reference

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

class NamespaceReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(TomlLiteral::class.java)
                    .withParent(
                        PlatformPatterns.psiElement(TomlKeyValue::class.java)
                            .withFirstChild(
                                PlatformPatterns.psiElement(TomlKey::class.java)
                                    .withText("namespace")
                            )
                            .withParent(
                                PlatformPatterns.psiElement(TomlHeaderOwner::class.java)
                                    .withFirstChild(
                                        PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                            .withText("[[guard.perimeter.rules]]")
                                    )
                            )
                    ),
                PlatformPatterns.psiElement(TomlLiteral::class.java)
                    .withSuperParent(
                        2,
                        PlatformPatterns.psiElement(TomlKeyValue::class.java)
                            .withFirstChild(
                                PlatformPatterns.psiElement(TomlKey::class.java)
                                    .withText("layering")
                            )
                            .withParent(
                                PlatformPatterns.psiElement(TomlHeaderOwner::class.java)
                                    .withFirstChild(
                                        PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                            .withText("[guard.perimeter]")
                                    )
                            )
                    )
            ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (element !is TomlLiteral) return PsiReference.EMPTY_ARRAY

                    return arrayOf(PhpNamespaceReference(element))
                }
            }
        )
    }
}