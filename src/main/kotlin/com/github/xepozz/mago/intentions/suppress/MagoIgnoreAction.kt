package com.github.xepozz.mago.intentions.suppress

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.PhpDocUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.Statement
import com.github.xepozz.mago.MagoBundle
import com.jetbrains.php.lang.psi.elements.PhpNamedElement
import com.jetbrains.php.lang.psi.PhpPsiElementFactory

sealed class MagoIgnoreAction(
    val category: String,
    val code: String,
    val line: Int,
    /** When set, use this offset to find the PSI element (underlined range start); otherwise use line. */
    private val problemStartOffset: Int? = null,
    /** When true, insert @mago-expect instead of @mago-ignore. */
    protected open val useExpect: Boolean = false
) : IntentionAction, PriorityAction, FileModifier {
    override fun getFamilyName() = MagoBundle.message("intention.familyName")

    /** Only primitives and Strings; safe to apply to a copy for intention preview. */
    override fun getFileModifierForPreview(target: PsiFile): FileModifier = this
    override fun startInWriteAction() = true

    /**
     * Explicit override so submenu options get a diff preview when hovered.
     * The platform may not use the default path for options;
     * applying `invoke()` on the supplied file (copy) and returning DIFF ensures preview works.
     */
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        invoke(project, editor, file)
        return IntentionPreviewInfo.DIFF
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        if (file.project != project) return false
        val document = editor.document
        val offset = getElementOffset(document, editor)
        if (offset >= document.textLength) return false
        val elementAt = file.findElementAt(offset) ?: return false

        // If the user already added the suppression right above the current statement/declaration,
        // hide all suppress actions immediately (even before the analyzer re-runs).
        if (isSuppressionAlreadyPresent(elementAt)) return false

        // Also hide if an enclosing function/method/class is suppressed (directive above its declaration line).
        // This covers: apply "for function" → move to another statement in the same function before re-analysis.
        if (isSuppressedInEnclosingDeclarations(elementAt)) return false

        return true
    }

    /** Quick fixes first (HIGH), then navigate (NORMAL), then suppress (LOW). */
    override fun getPriority() = PriorityAction.Priority.LOW

    /** Shared title for scope-specific actions: "Mago: Ignore/Expect `category:code` for <scope>". */
    protected fun scopeText(scopeName: String): String =
        if (useExpect) MagoBundle.message("intention.scopeText.expect", category, code, scopeName)
        else MagoBundle.message("intention.scopeText.ignore", category, code, scopeName)

    /** True if the PHPDoc right before [anchor] already contains this suppression (ignore or expect). */
    protected fun isSuppressionAlreadyPresent(anchor: PsiElement): Boolean {
        val doc = findPhpDocImmediatelyBefore(anchor) ?: return false
        return hasSuppressionInPhpDoc(doc)
    }

    private fun isSuppressedInEnclosingDeclarations(elementAt: PsiElement): Boolean {
        fun isDeclSuppressed(decl: PhpNamedElement): Boolean {
            val doc = decl.docComment ?: return false
            return hasSuppressionInPhpDoc(doc)
        }

        PsiTreeUtil.getParentOfType(elementAt, Method::class.java)?.let { if (isDeclSuppressed(it)) return true }
        PsiTreeUtil.getParentOfType(elementAt, Function::class.java)?.let { if (isDeclSuppressed(it)) return true }
        PsiTreeUtil.getParentOfType(elementAt, PhpClass::class.java)?.let { if (isDeclSuppressed(it)) return true }
        return false
    }

    private fun findPhpDocImmediatelyBefore(anchor: PsiElement): PhpDocComment? {
        var prev = anchor.prevSibling
        while (prev is PsiWhiteSpace) {
            prev = prev.prevSibling
        }
        return prev as? PhpDocComment
    }

    /** Tag value is only the first line; the following lines are description, not part of the tag. */
    private fun getTagValueFirstLine(tag: PhpDocTag): String =
        PhpDocUtil.getTagValue(tag, true)
            .trim()
            .substringBefore('\n')
            .trim()

    private fun hasSuppressionInPhpDoc(doc: PhpDocComment): Boolean {
        fun codesFrom(tag: PhpDocTag): Set<String> {
            val value = getTagValueFirstLine(tag)
            val cat = value.substringBefore(':', missingDelimiterValue = "")
            if (cat != category) return emptySet()
            val codesPart = value.substringAfter(':', missingDelimiterValue = "")
            if (codesPart.isEmpty()) return emptySet()
            return codesPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        val ignoreTags = doc.getTagElementsByName("@mago-ignore").asList()
        val expectTags = doc.getTagElementsByName("@mago-expect").asList()
        return ignoreTags.any { code in codesFrom(it) } || expectTags.any { code in codesFrom(it) }
    }

    /**
     * When we're at a function or method call (e.g. `substr(...)` or `$o->foo(...)`),
     * returns the call element (FunctionReference or MethodReference) so "Suppress for function"
     * can insert above that line. Uses PHP plugin PSI so only real calls are detected, not
     * keywords like `catch`. Handles offset landing on the call name or on an argument.
     */
    protected fun findElementAtFunctionCall(file: PsiFile, document: Document, editor: Editor? = null): PsiElement? {
        val offset = getElementOffset(document, editor)
        if (offset >= document.textLength) return null
        var elementAt = file.findElementAt(offset) ?: return null
        var currentOffset = offset
        while (elementAt is PsiWhiteSpace && currentOffset < document.textLength - 1) {
            currentOffset++
            val nextElement = file.findElementAt(currentOffset) ?: break
            if (nextElement != elementAt) {
                elementAt = nextElement
                if (elementAt !is PsiWhiteSpace) break
            }
        }
        val call = PsiTreeUtil.getParentOfType(elementAt, FunctionReference::class.java)
            ?: PsiTreeUtil.getParentOfType(elementAt, MethodReference::class.java)
        if (call != null) return call
        for (delta in 1..50) {
            val back = (offset - delta).coerceAtLeast(0)
            if (delta > 1 && document.getLineNumber(back) != document.getLineNumber(offset)) break
            val el = file.findElementAt(back) ?: continue
            if (el is PsiWhiteSpace) continue
            val callBack = PsiTreeUtil.getParentOfType(el, FunctionReference::class.java)
                ?: PsiTreeUtil.getParentOfType(el, MethodReference::class.java)
            if (callBack != null) return callBack
        }
        return null
    }

    protected fun <T : PsiElement> findElement(
        file: PsiFile,
        document: Document,
        clazz: Class<T>,
        editor: Editor? = null
    ): T? {
        val offset = getElementOffset(document, editor)
        if (offset >= document.textLength) return null

        var elementAt = file.findElementAt(offset)

        var currentOffset = offset
        while (elementAt is PsiWhiteSpace && currentOffset < document.textLength - 1) {
            currentOffset++
            val nextElement = file.findElementAt(currentOffset)
            if (nextElement != null && nextElement != elementAt) {
                elementAt = nextElement
                if (elementAt !is PsiWhiteSpace) break
            }
        }

        // When looking for Function/Method, the offset may land on the first argument;
        // scan backward to find the function name
        if (elementAt != null && (clazz == Function::class.java || clazz == Method::class.java)) {
            for (delta in 0..50) {
                val back = (offset - delta).coerceAtLeast(0)
                if (delta > 0 && document.getLineNumber(back) != document.getLineNumber(offset)) break
                val el = file.findElementAt(back) ?: continue
                if (el is PsiWhiteSpace) continue
                val parent = PsiTreeUtil.getParentOfType(el, clazz)
                if (parent != null) {
                    @Suppress("UNCHECKED_CAST")
                    return parent as T
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val parent = if (clazz == PsiElement::class.java) {
            elementAt as T?
        } else {
            PsiTreeUtil.getParentOfType(elementAt, clazz)
        }
        return parent
    }

    /** Document-only insert (used for intention preview; no write action, no commit/restart). */
    protected fun applyInsertToElement(editor: Editor, element: PsiElement) {
        // Pass editor.document so preview works when the preview file isn't in PsiDocumentManager.
        val edit = computeSuppressEdit(element.project, element, editor.document) ?: return
        applySuppressEdit(editor.document, edit)
    }

    protected fun insertIgnoreAtElement(
        project: Project,
        editor: Editor,
        file: PsiFile,
        element: PsiElement
    ) {
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            applyInsertToElement(editor, element)
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val edit = computeSuppressEdit(project, element) ?: return@runWriteCommandAction
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runWriteCommandAction
            applySuppressEdit(document, edit)
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            psiDocumentManager.commitDocument(document)
            val endOffset = edit.startOffset + edit.newText.length
            CodeStyleManager.getInstance(project).reformatText(file, edit.startOffset, endOffset)
        }
        finalizeChange(project, editor, file)
    }

    protected fun <T : PsiElement> invokeForElement(
        project: Project,
        editor: Editor,
        file: PsiFile,
        clazz: Class<T>,
        predicate: (T) -> Boolean = { true }
    ) {
        val element = findElement(file, editor.document, clazz, editor) ?: return
        if (!predicate(element)) return
        if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            applyInsertToElement(editor, element)
        } else {
            WriteCommandAction.runWriteCommandAction(project) {
                insertIgnoreAtElement(project, editor, file, element)
            }
        }
    }

    protected fun finalizeChange(project: Project, editor: Editor, file: PsiFile) {
        val document = editor.document

        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitDocument(document)
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

        DaemonCodeAnalyzer.getInstance(project).restart(file)
    }

    /** Mago reports 1-based line numbers; Document uses 0-based line index. */
    protected fun lineToIndex(document: Document): Int =
        (line - 1).coerceIn(0, document.lineCount - 1)

    /** 1-based line number where a comment would be inserted for the given element (same as "for statement" uses). */
    protected fun getInsertionLine(document: Document, element: PsiElement): Int =
        document.getLineNumber(element.textRange.startOffset) + 1

    /**
     * Offset at which to find the element.
     * When [editor] is provided, uses the caret offset so insert/merge use the current document
     * position (fixes grouping when the file changed after the annotation was created, e.g.: during re-analysis).
     */
    protected fun getElementOffset(document: Document, editor: Editor? = null): Int {
        if (editor != null) {
            return editor.caretModel.offset.coerceIn(0, (document.textLength - 1).coerceAtLeast(0))
        }
        return problemStartOffset?.coerceIn(0, (document.textLength - 1).coerceAtLeast(0))
            ?: document.getLineStartOffset(lineToIndex(document))
    }

    /** Single document edit used for both preview and apply so they always match. */
    private data class SuppressEdit(val startOffset: Int, val endOffset: Int, val newText: String)

    /**
     * @param documentFallback used when the file has no document in PsiDocumentManager (e.g.: intention preview copy).
     */
    private fun computeSuppressEdit(
        project: Project,
        element: PsiElement,
        documentFallback: Document? = null
    ): SuppressEdit? {
        val file = element.containingFile ?: return null
        val document = documentFallback ?: PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
        val tagName = if (useExpect) "@mago-expect" else "@mago-ignore"
        val anchor = insertionAnchor(element)
        val doc = findPhpDocImmediatelyBefore(anchor)

        if (doc != null) {
            val existing = findMagoTagForCategory(doc, tagName, category)
            val newText = if (existing != null) {
                val merged = mergeCodesInTag(existing, tagName, category, code) ?: return null
                val existingFirstLineEnd = existing.text.indexOf('\n').takeIf { it >= 0 } ?: existing.text.length
                val mergedLine = merged.text.substringBefore('\n').ifEmpty { merged.text }
                val tagStartInDoc = existing.textRange.startOffset - doc.textRange.startOffset
                doc.text.replaceRange(tagStartInDoc, tagStartInDoc + existingFirstLineEnd, mergedLine)
            } else {
                val addBlankLine = PhpDocUtil.getDescription(doc, true).isNotBlank() &&
                        doc.getTagElementsByName("@mago-ignore").isEmpty() &&
                        doc.getTagElementsByName("@mago-expect").isEmpty()

                insertMagoTagIntoPhpDocText(
                    doc.text,
                    tagText = "$tagName $category:$code",
                    addBlankLineBefore = addBlankLine
                ) ?: return null
            }
            return SuppressEdit(doc.textRange.startOffset, doc.textRange.endOffset, newText)
        }

        val lineStart = document.getLineStartOffset(document.getLineNumber(anchor.textRange.startOffset))
        return SuppressEdit(
            startOffset = lineStart,
            endOffset = lineStart,
            newText = "/** $tagName $category:$code */\n"
        )
    }

    private fun applySuppressEdit(document: Document, edit: SuppressEdit) {
        if (edit.startOffset == edit.endOffset) {
            document.insertString(edit.startOffset, edit.newText)
        } else {
            document.replaceString(edit.startOffset, edit.endOffset, edit.newText)
        }
    }

    private fun insertionAnchor(element: PsiElement): PsiElement {
        return when (element) {
            is Method, is Function, is PhpClass -> element
            else -> PsiTreeUtil.getParentOfType(element, Statement::class.java) ?: element
        }
    }

    private fun findMagoTagForCategory(doc: PhpDocComment, tagName: String, category: String): PhpDocTag? {
        val tags = doc.getTagElementsByName(tagName)
        for (t in tags) {
            val value = getTagValueFirstLine(t)
            val cat = value.substringBefore(':', missingDelimiterValue = "")
            if (cat == category) return t
        }
        return null
    }

    private fun mergeCodesInTag(existing: PhpDocTag, tagName: String, category: String, code: String): PhpDocTag? {
        val value = getTagValueFirstLine(existing)
        val cat = value.substringBefore(':', missingDelimiterValue = "")
        if (cat != category) return null
        val codesPart = value.substringAfter(':', missingDelimiterValue = "")
        val codes = codesPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        if (!codes.add(code)) return null
        val mergedText = "$tagName $category:${codes.sorted().joinToString(",")}"
        return createDocTag(project = existing.project, tagText = mergedText)
    }

    private fun normalizeSingleLinePhpDocToMultiline(docText: String): String {
        if (docText.contains('\n')) return docText
        val start = docText.indexOf("/**")
        val end = docText.lastIndexOf("*/")
        if (start < 0 || end < 0 || end <= start + 3) return docText
        val inner = docText.substring(start + 3, end).trim()
        val linePrefix = " "  // formatter will apply project indent
        return if (inner.isEmpty()) {
            "/**\n$linePrefix*/"
        } else {
            "/**\n$linePrefix* $inner\n$linePrefix*/"
        }
    }

    private fun insertMagoTagIntoPhpDocText(
        docText: String,
        tagText: String,
        addBlankLineBefore: Boolean
    ): String? {
        val normalized = normalizeSingleLinePhpDocToMultiline(docText)
        val end = normalized.lastIndexOf("*/")
        if (end < 0) return null
        val insertionPoint = normalized.lastIndexOf('\n', end).takeIf { it >= 0 } ?: end
        val linePrefix = " "  // formatter will apply project indent
        val sb = StringBuilder()
        if (addBlankLineBefore) {
            sb.append("\n").append(linePrefix).append("*")
        }
        sb.append("\n").append(linePrefix).append("* ").append(tagText)
        return normalized.substring(0, insertionPoint) + sb.toString() + normalized.substring(insertionPoint)
    }

    private fun createDocTag(project: Project, tagText: String): PhpDocTag {
        // Match JetBrains' own suppression fix template to get a correctly parsed PhpDocTag.
        val template = "/**\n* $tagText*/\nfunction a() {}"
        return PhpPsiElementFactory.createPhpPsiFromText(project, PhpDocTag::class.java, template)
    }

    /**
     * True if the problem offset is on the same line as a containing Method, Function, or PhpClass
     * declaration (so "for statement" should not be shown).
     */
    protected fun isOnDeclarationLine(file: PsiFile, document: Document, editor: Editor? = null): Boolean {
        val offset = getElementOffset(document, editor)
        if (offset >= document.textLength) return false
        val elementAt = file.findElementAt(offset) ?: return false
        val currentLine = document.getLineNumber(offset)
        for (clazz in listOf(Method::class.java, Function::class.java, PhpClass::class.java)) {
            val container = PsiTreeUtil.getParentOfType(elementAt, clazz)
            if (container != null && document.getLineNumber(container.textRange.startOffset) == currentLine) {
                return true
            }
        }

        return false
    }
}
