package com.github.xepozz.mago.intentions.suppress

import com.github.xepozz.mago.MagoBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.Method

class MagoMethodSuppressAction(
    category: String,
    code: String,
    line: Int,
    problemStartOffset: Int? = null,
    useExpect: Boolean = false
) : MagoIgnoreAction(category, code, line, problemStartOffset, useExpect) {

    override fun getText() = scopeText(MagoBundle.message("intention.scope.method"))

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (!super.isAvailable(project, editor, file)) return false
        val method = findElement(file, editor.document, Method::class.java, editor) ?: return false
        if (getInsertionLine(editor.document, method) == line) return false
        return !isSuppressionAlreadyPresent(method)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        if (file == null) return
        invokeForElement(project, editor, file, Method::class.java)
    }
}
