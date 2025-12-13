package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.config.index.MagoLayersIndexUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.toml.lang.psi.TomlLiteral

class LayersListReference(
    element: TomlLiteral,
) : PsiPolyVariantReferenceBase<PsiElement>(element) {
    override fun multiResolve(p0: Boolean): Array<out ResolveResult> {
        val text = StringUtil.unquoteString(rangeInElement.substring(element.text))
        if (text != "@all") return PsiElementResolveResult.EMPTY_ARRAY

        return MagoLayersIndexUtil.getAll(element.project)
            .flatMap { it.value }
            .mapNotNull { it.file.findPsiFile(element.project)?.findElementAt(it.range.startOffset)?.parent }
            .let { PsiElementResolveResult.createResults(it) }
    }

    override fun isSoft() = false

    override fun getVariants(): Array<out Any?> {
        return arrayOf(
            Triple("@all", "All layers", AllIcons.Hierarchy.Supertypes),
            Triple("@layer", "Specific layer", AllIcons.Hierarchy.Subtypes),
        )
            .map {
                LookupElementBuilder.create(it.first)
                    .withIcon(it.third)
                    .withTypeText(it.second, true)
            }
            .toTypedArray()
    }
}