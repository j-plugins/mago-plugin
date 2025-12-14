package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.contents
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.jetbrains.php.PhpIcons
import com.jetbrains.php.PhpIndexImpl
import com.jetbrains.php.completion.PhpCompletionUtil
import org.toml.lang.psi.TomlLiteral

class PhpWildcardNamespaceReference(
    element: TomlLiteral,
) : PsiPolyVariantReferenceBase<PsiElement>(element) {
    override fun multiResolve(p0: Boolean): Array<out ResolveResult> {
        val element = element as TomlLiteral

        if (element.contents.startsWith('@')) return PsiElementResolveResult.EMPTY_ARRAY

        val phpIndex = PhpIndexImpl.getInstance(element.project)
        val text = element.contents.replace("\\\\", "\\").trimEnd('*', '\\').let { "\\$it" }
        val namespacesByName = phpIndex.getNamespacesByName(text)

        return namespacesByName
            .let { PsiElementResolveResult.createResults(it) }
    }

    override fun isSoft() = false

    override fun getVariants(): Array<out Any> {
        val element = element as TomlLiteral
        if (element.contents.startsWith('@')) return emptyArray()

        val phpIndex = PhpIndexImpl.getInstance(element.project)
        val text = element.contents
        val substringBefore = text.substringBefore("IntellijIdeaRulezzz ")
        val parentNamespace = buildString {
            append("\\")
            val lastIndex = substringBefore.lastIndexOf("\\\\")
            if (lastIndex >= 0) {
                append(substringBefore.substring(0, lastIndex))
            }
            append("\\")
        }.replace("\\\\", "\\")

        val namespaces = PhpCompletionUtil.getAllChildNamespaceNames(phpIndex, parentNamespace)

        return namespaces
            .map { it.replace("\\", "\\\\") }
            .map {
                LookupElementBuilder.create(it)
                    .withIcon(PhpIcons.NAMESPACE)
            }
            .toTypedArray()
    }
}