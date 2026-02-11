package com.github.xepozz.mago.intentions.suppress

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.Method

class MagoFunctionSuppressAction(
    category: String,
    code: String,
    line: Int,
    problemStartOffset: Int? = null,
    useExpect: Boolean = false
) : MagoIgnoreAction(category, code, line, problemStartOffset, useExpect) {

    @Suppress("DialogTitleCapitalization")
    override fun getText() = scopeText(MagoBundle.message("intention.scope.function"))

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!super.isAvailable(project, editor, file)) return false
        val document = editor.document
        // Only show "for function" when we're in a standalone function; inside a method show only "for method"
        if (findElement(file, document, Method::class.java, editor) != null) return false
        val decl = findElement(file, document, Function::class.java, editor)
        val target = when {
            decl != null -> decl
            else -> findElementAtFunctionCall(file, document, editor)
        }
        if (target == null) return false
        if (getInsertionLine(document, target) == line) return false
        return !isSuppressionAlreadyPresent(target)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (file == null) return
        val decl = findElement(file, editor.document, Function::class.java, editor)
        val target = when {
            decl != null && decl !is Method -> decl
            else -> findElementAtFunctionCall(file, editor.document, editor)
        }
        if (target == null) return
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            applyInsertToElement(editor, target)
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                insertIgnoreAtElement(project, editor, file, target)
            }
        }
    }
}
