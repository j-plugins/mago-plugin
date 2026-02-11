package com.github.xepozz.mago.intentions.suppress

import com.github.xepozz.mago.MagoBundle
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class MagoIgnoreSubmenuAction(
    private val category: String,
    private val code: String,
    private val mainAction: IntentionAction,
    private val actions: List<IntentionAction>
) : IntentionAction, IntentionActionWithOptions, PriorityAction, FileModifier {

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement =
        (mainAction as? FileModifier)?.getElementToMakeWritable(currentFile) ?: currentFile

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        val mainCopy =
            (mainAction as? FileModifier)?.getFileModifierForPreview(target) as? IntentionAction ?: return null
        val actionCopies =
            actions.mapNotNull { (it as? FileModifier)?.getFileModifierForPreview(target) as? IntentionAction }
        if (actionCopies.size != actions.size) return null
        return MagoIgnoreSubmenuAction(category, code, mainCopy, actionCopies)
    }

    override fun getFamilyName() = MagoBundle.message("intention.familyName")
    override fun getText() = MagoBundle.message("intention.suppress.withCode", category, code)
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        mainAction.invoke(project, editor, file)
    }

    override fun getOptions(): List<IntentionAction> = actions

    override fun getCombiningPolicy(): IntentionActionWithOptions.CombiningPolicy {
        return IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly
    }

    override fun getPriority() = PriorityAction.Priority.LOW
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        return actions.any { it.isAvailable(project, editor, file) }
    }

    override fun startInWriteAction() = mainAction.startInWriteAction()
}

/**
 * Builds the 8 context-specific suppress actions in display order
 * (Ignore then Expect; statement, function, method, class).
 *
 * @todo Order kinda doesn't really work, doesn't matter that much
 */
fun createSuppressActions(category: String, code: String, line: Int, problemStartOffset: Int?): List<IntentionAction> =
    listOf(
        MagoStatementSuppressAction(category, code, line, problemStartOffset, useExpect = false),
        MagoFunctionSuppressAction(category, code, line, problemStartOffset, useExpect = false),
        MagoMethodSuppressAction(category, code, line, problemStartOffset, useExpect = false),
        MagoClassSuppressAction(category, code, line, problemStartOffset, useExpect = false),
        MagoStatementSuppressAction(category, code, line, problemStartOffset, useExpect = true),
        MagoFunctionSuppressAction(category, code, line, problemStartOffset, useExpect = true),
        MagoMethodSuppressAction(category, code, line, problemStartOffset, useExpect = true),
        MagoClassSuppressAction(category, code, line, problemStartOffset, useExpect = true),
    )

/**
 * Creates a single "Mago: Suppress `category:code`" fix with a submenu of context-specific options
 * in order: Ignore/Expect for statement, function, method, class (each only shown when applicable).
 */
fun createSuppressSubmenuAction(category: String, code: String, line: Int, problemStartOffset: Int?): IntentionAction {
    val actions = createSuppressActions(category, code, line, problemStartOffset)
    return MagoIgnoreSubmenuAction(category, code, MagoIgnoreFirstAvailableAction(actions), actions)
}
