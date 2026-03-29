package com.github.xepozz.mago.intentions

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Quick fix for [lint:no-redundant-file]: removes the current file when it has no executable code or declarations.
 */
class MagoRemoveRedundantFileAction : IntentionAction, PriorityAction {
    override fun getText(): String = MagoBundle.message("intention.removeFile")
    override fun getFamilyName(): String = MagoBundle.message("intention.familyName")

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = file?.virtualFile != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val virtualFile = file?.virtualFile ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            FileEditorManager.getInstance(project).closeFile(virtualFile)
            virtualFile.delete(project)
        }
    }
}
