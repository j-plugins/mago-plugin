package com.github.xepozz.mago.intentions.suppress

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class MagoStatementSuppressAction(
    category: String,
    code: String,
    line: Int,
    problemStartOffset: Int? = null,
    useExpect: Boolean = false
) : MagoIgnoreAction(category, code, line, problemStartOffset, useExpect) {

    override fun getText() = scopeText(MagoBundle.message("intention.scope.statement"))

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!super.isAvailable(project, editor, file)) return false
        if (isOnDeclarationLine(file, editor.document, editor)) return false
        val element = findElement(file, editor.document, PsiElement::class.java, editor) ?: return true
        return !isSuppressionAlreadyPresent(element)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (file == null) return
        val element = findElement(file, editor.document, PsiElement::class.java, editor) ?: return
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            applyInsertToElement(editor, element)
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                insertIgnoreAtElement(project, editor, file, element)
            }
        }
    }
}
