package com.github.xepozz.mago.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class MagoTomlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(TomlLiteral::class.java)
                    .withParent(
                        PlatformPatterns.psiElement(TomlArray::class.java)
                            .withParent(
                                PlatformPatterns.psiElement(TomlKeyValue::class.java)
                                    .withFirstChild(
                                        PlatformPatterns.psiElement(TomlKey::class.java)
                                            .withText(
                                                PlatformPatterns.string().oneOf(
                                                    "paths",
                                                    "includes",
                                                    "excludes",
                                                )
                                            )
                                    )
                                    .withParent(
                                        PlatformPatterns.psiElement(TomlTable::class.java)
                                            .withFirstChild(
                                                PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                                    .withText(
                                                        PlatformPatterns.string()
                                                            .oneOf(
                                                                "[source]",
                                                                "[guard]"
                                                            )
                                                    )
                                            )
                                    )
                            )
                    ),
            ),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    if (element !is TomlLiteral) return PsiReference.EMPTY_ARRAY

//                    val keyValue = element.parent.parent as? TomlKeyValue ?: return PsiReference.EMPTY_ARRAY

//                    println("element: ${element.text}, key: ${keyValue.key.text}")

                    return object : FileReferenceSet(element) {
                        override fun isSoft() = false
                        override fun getReferenceCompletionFilter() = DIRECTORY_FILTER
                    }
                        .allReferences
                        .toList()
                        .toTypedArray()
                }
            }
        )
    }
}