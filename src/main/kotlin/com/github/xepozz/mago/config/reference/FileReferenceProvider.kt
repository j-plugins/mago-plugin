package com.github.xepozz.mago.config.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlLiteral

object FileReferenceProvider : PsiReferenceProvider() {
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