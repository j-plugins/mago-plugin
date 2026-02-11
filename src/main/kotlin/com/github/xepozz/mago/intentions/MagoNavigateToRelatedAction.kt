package com.github.xepozz.mago.intentions

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile

class MagoNavigateToRelatedAction(
    private val message: String,
    private val targetOffset: Int,
    private val line: Int,
    private val targetFilePath: String? = null,
) : IntentionAction, PriorityAction {

    override fun getText(): String = MagoBundle.message("intention.navigateToCause", message, line)
    override fun getFamilyName(): String = MagoBundle.message("intention.familyName")

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun startInWriteAction(): Boolean = false
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val isOtherFile = targetFilePath != null && file?.virtualFile?.path != targetFilePath

        if (isOtherFile) {
            val vf = LocalFileSystem.getInstance().findFileByPath(targetFilePath)
            if (vf != null) {
                val opened = FileEditorManager.getInstance(project).openFile(vf, true)
                if (opened.isNotEmpty()) {
                    val targetEditor = FileEditorManager.getInstance(project).selectedTextEditor
                    if (targetEditor != null) {
                        val doc = targetEditor.document
                        val lineStart = if (line < doc.lineCount) doc.getLineStartOffset(line) else 0
                        targetEditor.caretModel.moveToOffset(lineStart)
                        targetEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    }
                }
            }
        } else {
            editor ?: return
            editor.caretModel.moveToOffset(targetOffset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }
}
