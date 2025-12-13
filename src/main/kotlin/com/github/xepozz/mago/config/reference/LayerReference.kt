package com.github.xepozz.mago.config.reference

import com.github.xepozz.mago.MagoIcons
import com.github.xepozz.mago.config.index.MagoLayersIndexUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.toml.lang.psi.TomlLiteral

class LayerReference(
    element: TomlLiteral,
    textRange: TextRange,
) : PsiPolyVariantReferenceBase<PsiElement>(element, textRange) {
    override fun multiResolve(p0: Boolean): Array<out ResolveResult> {
        val text = StringUtil.unquoteString(rangeInElement.substring(element.text))

        return MagoLayersIndexUtil.getAll(element.project)
            .filter { it.key == text }
            .flatMap { it.value }
            .mapNotNull { it.file.findPsiFile(element.project)?.findElementAt(it.range.startOffset)?.parent }
            .let { PsiElementResolveResult.createResults(it) }
    }

    override fun isSoft() = false

    override fun getVariants(): Array<out Any?> {
        return MagoLayersIndexUtil.getAll(element.project)
            .map {
                val namespaces = it.value.flatMap { it.values }.joinToString { "," }
                LookupElementBuilder.create(it.key)
                    .withIcon(MagoIcons.MAGO)
                    .withTypeText(namespaces, true)
            }
            .toTypedArray()
    }
}