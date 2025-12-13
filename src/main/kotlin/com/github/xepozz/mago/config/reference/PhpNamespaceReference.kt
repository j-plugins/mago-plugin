package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.MagoIcons
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.php.PhpIndexImpl
import com.jetbrains.php.completion.PhpCompletionUtil
import org.toml.lang.psi.TomlLiteral

class PhpNamespaceReference(
    element: TomlLiteral,
) : PsiPolyVariantReferenceBase<PsiElement>(element) {
    override fun multiResolve(p0: Boolean): Array<out ResolveResult> {
        val phpIndex = PhpIndexImpl.getInstance(element.project)
        val text = StringUtil.unquoteString(element.text).replace("\\\\", "\\").let { "\\$it" }
        val namespacesByName = phpIndex.getNamespacesByName(text)
        println("resolve ${text} -> $namespacesByName")
        val aaaaaaa = namespacesByName.flatMap { it.directories.toList() }
        println("resolve2 $aaaaaaa")

        return namespacesByName
            .map { it }
            .let { PsiElementResolveResult.createResults(it) }
    }

    override fun isSoft() = false

    override fun getVariants(): Array<out Any?> {
        val phpIndex = PhpIndexImpl.getInstance(element.project)
        val text = StringUtil.unquoteString(element.text)
        val substringBefore = text.substringBefore("IntellijIdeaRulezzz ")
        val parentNamespace = buildString {
            append("\\")
            val lastIndex = substringBefore.lastIndexOf("\\\\")
            if (lastIndex >= 0) {
                append(substringBefore.substring(0, lastIndex))
            }
            append("\\")
        }.replace("\\\\", "\\")

        println("namespaces of ${text}, $parentNamespace:")
        val namespaces = PhpCompletionUtil.getAllChildNamespaceNames(phpIndex, parentNamespace)
        println("result $namespaces")

        return namespaces
            .map { it.replace("\\", "\\\\") }
            .map {
                LookupElementBuilder.create(it)
                    .withIcon(MagoIcons.MAGO)
            }
            .toTypedArray()
    }
}