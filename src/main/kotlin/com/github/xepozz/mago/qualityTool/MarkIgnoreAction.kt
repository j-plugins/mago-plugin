package com.github.xepozz.mago.qualityTool

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class MarkIgnoreAction(val code: String, val line: Int) : IntentionAction {
    override fun getFamilyName() = "Mago"
    override fun getText() = "Mark @mago-ignore `${code}`"

    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        editor.document.insertString(
            editor.logicalPositionToOffset(LogicalPosition(line, 0)),
            "/** @mago-ignore analysis:${code} */\n"
        )
//        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
//        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun startInWriteAction() = true
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = true
}