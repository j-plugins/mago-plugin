package com.github.xepozz.mago.annotator

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.analysis.MagoProblemsCollector
import com.github.xepozz.mago.annotator.MagoExternalAnnotator.AnnotationResult
import com.github.xepozz.mago.annotator.MagoExternalAnnotator.CollectedInfo
import com.github.xepozz.mago.intentions.suppress.createSuppressSubmenuAction
import com.github.xepozz.mago.intentions.apply.ApplyAllScope
import com.github.xepozz.mago.intentions.apply.MagoApplyEditAction
import com.github.xepozz.mago.intentions.apply.MagoApplyEditSubmenuAction
import com.github.xepozz.mago.intentions.apply.filterEditsByExactSafety
import com.github.xepozz.mago.intentions.apply.maxSafetyLevel
import com.github.xepozz.mago.intentions.MagoNavigateToRelatedAction
import com.github.xepozz.mago.intentions.MagoRemoveRedundantFileAction
import com.github.xepozz.mago.model.MagoEdit
import com.github.xepozz.mago.model.MagoProblemDescription
import com.github.xepozz.mago.model.MagoSeverity
import com.github.xepozz.mago.utils.NotificationsUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Function

class MagoExternalAnnotator :
    ExternalAnnotator<CollectedInfo, AnnotationResult>() {

    data class CollectedInfo(val file: PsiFile, val editorText: String)
    data class AnnotationResult(
        val problems: List<MagoProblemDescription>,
        val errorOutput: String,
        val analyzedContent: String
    )

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CollectedInfo? {
        // hasErrors is true for semantic errors too (e.g.: abstract instantiation).
        // Only skip when the PSI tree has actual syntax/parse errors.
        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) return null
        return collectInformation(file)
    }

    override fun collectInformation(file: PsiFile): CollectedInfo? {
        if (file.language.id != "PHP") return null
        return CollectedInfo(file, file.text)
    }

    override fun doAnnotate(collectedInfo: CollectedInfo): AnnotationResult {
        val empty = AnnotationResult(emptyList(), "", "")
        val virtualFile = collectedInfo.file.virtualFile ?: return empty
        val filePath = virtualFile.path
        val editorText = collectedInfo.editorText
        val parentDir = virtualFile.parent?.path

        return try {
            val collector = MagoProblemsCollector()
            val result = if (parentDir != null) {
                collector.collectForFile(
                    collectedInfo.file.project,
                    filePath,
                    filePath,
                    stdinContent = editorText,
                    tempFileParentDir = parentDir
                )
            } else {
                val tempFile = java.io.File.createTempFile(".mago-", ".php")
                tempFile.deleteOnExit()
                try {
                    tempFile.writeText(editorText, Charsets.UTF_8)
                    val r = collector.collectForFile(
                        collectedInfo.file.project,
                        filePath = tempFile.absolutePath,
                        originalPath = filePath
                    )
                    r.copy(tempFileNameUsed = tempFile.name)
                } finally {
                    tempFile.delete()
                }
            }

            val problems = result.tempFileNameUsed?.let { tempName ->
                val originalName = virtualFile.name
                result.problems.map { p ->
                    p.copy(
                        myFile = p.myFile.replace(tempName, originalName),
                        edits = p.edits.map { e ->
                            e.copy(
                                name = e.name.replace(tempName, originalName),
                                path = e.path.replace(tempName, originalName)
                            )
                        },
                        secondaryAnnotations = p.secondaryAnnotations.map { s ->
                            s.copy(filePath = s.filePath.replace(tempName, originalName))
                        }
                    )
                }
            } ?: result.problems

            val allProblems = problems.distinctBy { "${it.startChar}-${it.endChar}-${it.code}-${it.myMessage}" }
            AnnotationResult(allProblems, result.errorOutput, editorText)
        } catch (_: Exception) {
            empty
        }
    }

    override fun apply(file: PsiFile, annotationResult: AnnotationResult, holder: AnnotationHolder) {
        if (annotationResult.analyzedContent.isEmpty()) return
        if (annotationResult.analyzedContent != file.text) return

        val problems = annotationResult.problems

        if (problems.isEmpty()) {
            if (annotationResult.errorOutput.isNotBlank()) {
                NotificationsUtil.error(
                    file.project,
                    title = MagoBundle.message("annotator.failed.title"),
                    content = annotationResult.errorOutput
                )
            }
            return
        }

        val fileText = file.text
        val fileLength = fileText.length
        val currentFilePath = file.virtualFile?.path ?: return

        val groupedProblems = problems.groupBy { problem ->
            ReadAction.compute<TextRange, Throwable> {
                resolveHighlightRange(problem, file, fileText, fileLength)
            }
        }

        val allFileEdits = problems.flatMap { it.edits }
        val applyAllByLevel = buildApplyAllActions(allFileEdits)
        val applyAllCountByLevel = buildApplyAllCounts(allFileEdits)

        for ((textRange, rangeProblems) in groupedProblems) {
            val highestSeverityProblem = rangeProblems.maxByOrNull { it.severity.ordinal } ?: continue

            val severity = when (highestSeverityProblem.severity) {
                MagoSeverity.ERROR -> HighlightSeverity.ERROR
                MagoSeverity.WARNING -> HighlightSeverity.WARNING
                MagoSeverity.INFO -> HighlightSeverity.WEAK_WARNING
            }

            val gutterMessage = if (rangeProblems.size == 1) {
                val problem = rangeProblems.first()
                MagoBundle.message("annotator.gutterMessage", problem.myMessage, problem.category, problem.code)
            } else {
                MagoBundle.message("annotator.multipleIssues")
            }

            val builder = holder.newAnnotation(severity, gutterMessage)
                .range(textRange)
                .tooltip(formatGroupedHtmlMessage(rangeProblems))

            attachEditFixes(builder, rangeProblems, applyAllByLevel, applyAllCountByLevel)
            attachRemoveRedundantFileFix(builder, rangeProblems)
            attachSuppressFixes(builder, rangeProblems, fileText)
            attachRelatedNavigation(builder, rangeProblems, file, fileText, fileLength, currentFilePath)

            builder.create()
        }
    }

    /**
     * Determines the highlight range for a problem.
     * Per-rule overrides go here to keep the logic clean and extensible.
     */
    private fun resolveHighlightRange(
        problem: MagoProblemDescription,
        file: PsiFile,
        fileText: String,
        fileLength: Int,
    ): TextRange {
        val rawRange = byteRangeToCharTextRange(fileText, problem.startChar, problem.endChar)

        val range = when (problem.code) {
            "missing-return-type" -> resolveFunctionNameRange(file, rawRange, fileLength)
            "unused-pragma" -> resolveFromSecondary(problem, fileText, messagePrefix = "...for this code") ?: rawRange
            else -> rawRange
        }

        return finalizeRange(range, file, fileText, fileLength)
    }

    private fun resolveFunctionNameRange(file: PsiFile, rawRange: TextRange, fileLength: Int): TextRange {
        val start = rawRange.startOffset.coerceIn(0, fileLength)
        val element = file.findElementAt(start)
        val function = PsiTreeUtil.getParentOfType(element, Function::class.java) ?: return rawRange
        val nameIdentifier = function.nameIdentifier ?: return rawRange
        return nameIdentifier.textRange
    }

    private fun resolveFromSecondary(
        problem: MagoProblemDescription,
        fileText: String,
        @Suppress("SameParameterValue") messagePrefix: String
    ): TextRange? {
        val secondary = problem.secondaryAnnotations.firstOrNull { it.message.startsWith(messagePrefix) }
            ?: return null
        return byteRangeToCharTextRange(fileText, secondary.startOffset, secondary.endOffset)
    }

    private fun finalizeRange(range: TextRange, file: PsiFile, fileText: String, fileLength: Int): TextRange {
        var start = range.startOffset.coerceIn(0, fileLength)
        var end = range.endOffset.coerceIn(start, fileLength)

        // Don't skip leading whitespace or expand when the range is tiny (e.g.: trailing space at 453-454).
        // Otherwise, we'd move the highlight off the error onto the next token.
        if (end - start <= 2) {
            return TextRange.create(start, end)
        }

        while (start < end && fileText[start].isWhitespace()) {
            start++
        }

        val elementAtStart = file.findElementAt(start)
        if (elementAtStart != null) {
            val elEnd = elementAtStart.textRange.endOffset.coerceIn(0, fileLength)
            if (elEnd > end && elementAtStart.textRange.startOffset <= start) {
                end = elEnd
            }
        }

        return TextRange.create(start, end)
    }

    private fun isSameFile(secondaryPath: String, currentPath: String): Boolean {
        if (secondaryPath == currentPath) return true
        val secondaryName = secondaryPath.substringAfterLast('/')
        val currentName = currentPath.substringAfterLast('/')
        return secondaryName == currentName && secondaryPath.endsWith(currentName)
    }

    /**
     * Adds "Navigate to cause" intention actions for each secondary annotation.
     * Same-file secondaries jump to the exact offset; cross-file secondaries open the file and jump to the line.
     * Skips problems whose rule is in [noNavigateToCauseRuleIds].
     */
    private fun attachRelatedNavigation(
        builder: com.intellij.lang.annotation.AnnotationBuilder,
        rangeProblems: List<MagoProblemDescription>,
        file: PsiFile,
        fileText: String,
        fileLength: Int,
        currentFilePath: String,
    ) {
        val addedNavs = mutableSetOf<String>()
        for (problem in rangeProblems) {
            if ("${problem.category}:${problem.code}" in noNavigateToCauseRuleIds) continue
            for (secondary in problem.secondaryAnnotations) {
                val sameFile = isSameFile(secondary.filePath, currentFilePath)
                val (offset, targetPath) = if (sameFile) {
                    val charRange = byteRangeToCharTextRange(
                        fileText,
                        byteStart = secondary.startOffset,
                        byteEnd = secondary.endOffset
                    )
                    val finalRange = finalizeRange(charRange, file, fileText, fileLength)
                    finalRange.startOffset to null as String?
                } else {
                    0 to secondary.filePath
                }
                val key = "${targetPath ?: currentFilePath}:$offset-${secondary.message}"
                if (!addedNavs.add(key)) continue

                builder.withFix(
                    MagoNavigateToRelatedAction(
                        secondary.message,
                        targetOffset = offset,
                        secondary.line,
                        targetFilePath = targetPath
                    )
                )
            }
        }
    }

    private fun buildApplyAllActions(allEdits: List<MagoEdit>): List<MagoApplyEditAction?> {
        return listOf(ApplyAllScope.SAFE_ONLY, ApplyAllScope.POTENTIALLY_UNSAFE, ApplyAllScope.UNSAFE).map { scope ->
            val filtered = filterEditsByExactSafety(allEdits, scope.maxSafetyLevel)
            if (filtered.isEmpty()) null else MagoApplyEditAction(
                edits = filtered,
                isApplyAll = true,
                applyAllScope = scope
            )
        }
    }

    private fun buildApplyAllCounts(allEdits: List<MagoEdit>): List<Int> {
        return listOf(ApplyAllScope.SAFE_ONLY, ApplyAllScope.POTENTIALLY_UNSAFE, ApplyAllScope.UNSAFE).map { scope ->
            filterEditsByExactSafety(allEdits, scope.maxSafetyLevel).sumOf { it.replacements.size }
        }
    }

    private fun attachEditFixes(
        builder: com.intellij.lang.annotation.AnnotationBuilder,
        rangeProblems: List<MagoProblemDescription>,
        applyAllByLevel: List<MagoApplyEditAction?>,
        applyAllCountByLevel: List<Int>
    ) {
        val addedSubmenus = mutableSetOf<Int>()
        for (problem in rangeProblems) {
            for (edit in problem.edits) {
                val level = edit.maxSafetyLevel()
                if (level !in 0..2 || !addedSubmenus.add(level)) continue

                val individualActions = rangeProblems
                    .flatMap { p -> p.edits.filter { it.maxSafetyLevel() == level }.map { e -> e to p } }
                    .distinctBy { (e, _) -> e }
                    .map { (e, p) ->
                        val description = p.help.ifBlank { p.myMessage }
                        MagoApplyEditAction(listOf(e), fixDescription = description.ifBlank { null })
                    }
                val applyAllForLevel = if (applyAllCountByLevel.getOrNull(level)?.let { it > 1 } == true)
                    applyAllByLevel.getOrNull(level) else null
                val actions = individualActions + listOfNotNull(applyAllForLevel)
                if (actions.isEmpty()) continue
                if (actions.size == 1) {
                    builder.withFix(actions.single())
                } else {
                    builder.withFix(MagoApplyEditSubmenuAction(actions.first(), actions))
                }
            }
        }
    }

    /** Rule ids that must not get a "Suppress" intention (e.g.: use a different fix or none). */
    private val nonIgnorableRuleIds = setOf(
        "analysis:semantics", // Does not work, it's a semantic error
        "lint:strict-types", // Adds it above the `<?php` tag
        "lint:no-redundant-file", // Remove-file fix instead of suppress
    )

    /** Rule ids that must not get "Navigate to cause" intentions for their secondaries. */
    private val noNavigateToCauseRuleIds = setOf(
        "analysis:type-inspection",
    )

    private fun attachRemoveRedundantFileFix(
        builder: com.intellij.lang.annotation.AnnotationBuilder,
        rangeProblems: List<MagoProblemDescription>,
    ) {
        if (rangeProblems.any { it.category == "lint" && it.code == "no-redundant-file" }) {
            builder.withFix(MagoRemoveRedundantFileAction())
        }
    }

    private fun attachSuppressFixes(
        builder: com.intellij.lang.annotation.AnnotationBuilder,
        rangeProblems: List<MagoProblemDescription>,
        fileText: String
    ) {
        val addedFixes = mutableSetOf<String>()
        for (problem in rangeProblems) {
            if ("${problem.category}:${problem.code}" in nonIgnorableRuleIds) continue

            val problemCharRange = byteRangeToCharTextRange(
                fileText,
                byteStart = problem.startChar,
                byteEnd = problem.endChar
            )
            var problemStartOffset = problemCharRange.startOffset
            while (problemStartOffset < problemCharRange.endOffset && fileText[problemStartOffset].isWhitespace()) {
                problemStartOffset++
            }

            val key = "suppress_${problem.category}_${problem.code}"
            if (addedFixes.add(key)) {
                builder.withFix(
                    createSuppressSubmenuAction(
                        problem.category,
                        problem.code,
                        problem.lineNumber,
                        problemStartOffset
                    )
                )
            }
        }
    }

    private fun formatGroupedHtmlMessage(problems: List<MagoProblemDescription>): String {
        val sb = StringBuilder("<html>")

        problems.forEachIndexed { index, problem ->
            if (index > 0) sb.append("<br/><hr/>")

            sb.append("<b>").append(MagoBundle.message("annotator.tooltip.magoLabel")).append("</b> ").append(escapeAndFormat(problem.myMessage))
                .append(" [").append(problem.category).append(":").append(problem.code).append("]")

            for (note in problem.notes) {
                sb.append("<br/><br/>").append(escapeAndFormat(note))
            }

            if (problem.help.isNotEmpty()) {
                sb.append("<br/><br/><b>").append(MagoBundle.message("annotator.tooltip.helpLabel")).append("</b> ").append(escapeAndFormat(problem.help))
            }

            if (problem.secondaryAnnotations.isNotEmpty()) {
                sb.append("<br/><br/><b>").append(MagoBundle.message("annotator.tooltip.relatedLabel")).append("</b>")
                for (secondary in problem.secondaryAnnotations) {
                    sb.append("<br/>&nbsp;&nbsp;↳ <i>").append(escapeAndFormat(secondary.message)).append("</i>")
                    sb.append(" <span style='color:gray'>").append(MagoBundle.message("annotator.tooltip.lineLabel", secondary.line + 1)).append("</span>")
                }
            }
        }

        sb.append("</html>")
        return sb.toString()
    }

    private fun escapeAndFormat(text: String): String {
        var result = StringUtil.escapeXmlEntities(text)
        var open = true
        while (result.contains("`")) {
            result = if (open) {
                result.replaceFirst("`", "<b><code>")
            } else {
                result.replaceFirst("`", "</code></b>")
            }
            open = !open
        }
        if (!open) result += "</code></b>"
        return result
    }

    private fun byteRangeToCharTextRange(text: String, byteStart: Int, byteEnd: Int): TextRange {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val safeByteStart = byteStart.coerceIn(0, bytes.size)
        val safeByteEnd = byteEnd.coerceIn(safeByteStart, bytes.size)

        val charStart = String(bytes.copyOf(safeByteStart), Charsets.UTF_8).length
        val charEnd = String(bytes.copyOf(safeByteEnd), Charsets.UTF_8).length
        return TextRange.create(charStart, charEnd)
    }
}
