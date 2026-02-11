package com.github.xepozz.mago.intentions.apply

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class MagoApplyEditSubmenuAction(
    private val mainAction: MagoApplyEditAction,
    private val subActions: List<IntentionAction>
) : IntentionAction, IntentionActionWithOptions, PriorityAction, FileModifier {
    override fun getFamilyName() = mainAction.familyName
    override fun getText() = mainAction.text
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        mainAction.invoke(project, editor, file)
    }

    override fun getOptions(): List<IntentionAction> {
        return subActions.filter { it != mainAction }
    }

    override fun getCombiningPolicy(): IntentionActionWithOptions.CombiningPolicy {
        return IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly
    }

    override fun getPriority() = mainAction.priority
    override fun startInWriteAction() = mainAction.startInWriteAction()
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) =
        mainAction.isAvailable(project, editor, file)

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement =
        mainAction.getElementToMakeWritable(currentFile)

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        val mainCopy = mainAction.getFileModifierForPreview(target) as? MagoApplyEditAction ?: return null
        val subCopies = subActions.mapNotNull { (it as? FileModifier)?.getFileModifierForPreview(target) }
        if (subCopies.size != subActions.size) return null
        return MagoApplyEditSubmenuAction(
            mainAction = mainCopy,
            subActions = subCopies.filterIsInstance<IntentionAction>()
        )
    }
}
