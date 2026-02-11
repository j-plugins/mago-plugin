package com.github.xepozz.mago.intentions.suppress

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Invokes the first available action from the list when the user chooses the main "Ignore" entry.
 * Implements FileModifier so the parent submenu can provide an intention preview (hover / Ctrl+Q).
 */
internal class MagoIgnoreFirstAvailableAction(
    private val actions: List<IntentionAction>,
) : IntentionAction, FileModifier {
    override fun getFamilyName() = MagoBundle.message("intention.familyName")
    override fun getText() =
        (actions.firstOrNull() as? MagoIgnoreAction)?.let {
            MagoBundle.message("intention.ignore.withCode", it.category, it.code)
        } ?: MagoBundle.message("intention.ignore.generic")

    override fun startInWriteAction(): Boolean = actions.firstOrNull()?.startInWriteAction() ?: true
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        actions.any { it.isAvailable(project, editor, file) }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        actions.firstOrNull { it.isAvailable(project, editor, file) }?.invoke(project, editor, file)
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement =
        (actions.firstOrNull() as? FileModifier)?.getElementToMakeWritable(currentFile) ?: currentFile

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        val copies = actions.mapNotNull { (it as? FileModifier)?.getFileModifierForPreview(target) as? IntentionAction }
        if (copies.size != actions.size) return null
        return MagoIgnoreFirstAvailableAction(copies)
    }
}
