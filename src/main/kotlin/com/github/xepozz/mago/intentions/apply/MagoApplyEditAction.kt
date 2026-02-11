package com.github.xepozz.mago.intentions.apply

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.model.MagoEdit
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import java.nio.charset.StandardCharsets

class MagoApplyEditAction(
    private val edits: List<MagoEdit>,
    private val isApplyAll: Boolean = false,
    private val applyAllScope: ApplyAllScope? = null,
    private val fixDescription: String? = null
) : IntentionAction, PriorityAction, FileModifier {

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

    override fun getFileModifierForPreview(target: PsiFile): FileModifier {
        return MagoApplyEditAction(edits, isApplyAll, applyAllScope, fixDescription)
    }

    override fun getFamilyName() = MagoBundle.message("intention.familyName")

    override fun getPriority(): PriorityAction.Priority {
        return if (isApplyAll) PriorityAction.Priority.LOW else PriorityAction.Priority.HIGH
    }

    override fun getText(): String {
        if (applyAllScope != null) {
            return MagoBundle.message("intention.apply.allWithScope", applyAllScope.label)
        }
        val maxSafetyValue = edits.flatMap { it.replacements }.maxOfOrNull { safetyLevel(it.safety) } ?: 0
        val safetySuffix = when (maxSafetyValue) {
            2 -> MagoBundle.message("intention.apply.suffixUnsafe")
            1 -> MagoBundle.message("intention.apply.suffixPotentiallyUnsafe")
            else -> ""
        }
        return when {
            !fixDescription.isNullOrBlank() -> MagoBundle.message(
                "intention.apply.withDescription",
                fixDescription.trim()
            ) + safetySuffix

            isApplyAll -> MagoBundle.message("intention.apply.all") + safetySuffix
            else -> MagoBundle.message("intention.apply.one") + safetySuffix
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val filePath = file.virtualFile?.path?.let { normalizePath(it) }
        val fileName = file.name
        val currentFileEdits = edits.filter { editMatchesFile(it, filePath, fileName) }
        if (currentFileEdits.isEmpty()) return
        val fileText = file.text
        val doc = editor.document
        val allReplacements = currentFileEdits.flatMap { edit ->
            edit.replacements.map { r ->
                val startChar = byteOffsetToCharOffset(fileText, r.start)
                val endChar = byteOffsetToCharOffset(fileText, r.end)
                Triple(startChar, endChar, r.newText)
            }
        }.sortedByDescending { it.first }
        val inPreview = IntentionPreviewUtils.isIntentionPreviewActive()
        if (inPreview) {
            for ((startChar, endChar, newText) in allReplacements) {
                if (startChar in 0..endChar && endChar <= doc.textLength) {
                    doc.replaceString(startChar, endChar, newText)
                }
            }
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                for ((startChar, endChar, newText) in allReplacements) {
                    if (startChar in 0..endChar && endChar <= doc.textLength) {
                        doc.replaceString(startChar, endChar, newText)
                    }
                }
                PsiDocumentManager.getInstance(project).commitDocument(doc)
                FileDocumentManager.getInstance().saveDocument(doc)

                val settings = project.getService(MagoProjectConfiguration::class.java)
                if (settings.formatAfterFix && settings.formatterEnabled) {
                    CodeStyleManager.getInstance(project)
                        .reformatText(file, 0, doc.textLength)
                }
                DaemonCodeAnalyzer.getInstance(project).restart(file)
            }
        }
    }

    private fun byteOffsetToCharOffset(text: String, byteOffset: Int): Int {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (byteOffset <= 0) return 0
        if (byteOffset >= bytes.size) return text.length
        return String(bytes.copyOf(byteOffset), StandardCharsets.UTF_8).length
    }

    override fun startInWriteAction() = true
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = edits.isNotEmpty()
}
