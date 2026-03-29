package com.github.xepozz.mago.config.reference

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class FileSetReferenceContributor : PsiReferenceContributor() {
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
                                                        PlatformPatterns.string().oneOf(
                                                            "[source]",
                                                            "[guard]",
                                                            "[linter]",
                                                            "[analyzer]",
                                                            "[formatter]",
                                                        )
                                                    )
                                            )
                                    )
                            )
                    ),
            ),
            FileReferenceProvider
        )
        registrar.registerReferenceProvider(
            PlatformPatterns.or(
                PlatformPatterns.psiElement(TomlLiteral::class.java)
                    .withParent(
                        PlatformPatterns.psiElement(TomlKeyValue::class.java)
                            .withFirstChild(
                                PlatformPatterns.psiElement(TomlKey::class.java)
                                    .withText(
                                        PlatformPatterns.string().oneOf(
                                            "baseline",
                                        )
                                    )
                            )
                            .withParent(
                                PlatformPatterns.psiElement(TomlTable::class.java)
                                    .withFirstChild(
                                        PlatformPatterns.psiElement(TomlTableHeader::class.java)
                                            .withText(
                                                PlatformPatterns.string().oneOf(
                                                    "[analyzer]",
                                                    "[linter]",
                                                    "[guard]",
                                                )
                                            )
                                    )
                            )
                    ),
            ),
            FileReferenceProvider,
        )
    }
}

