package com.github.xepozz.mago

import com.google.gson.JsonParser
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolMessage
import com.jetbrains.php.tools.quality.QualityToolMessageProcessor

// it does analysis everytime when you change a file in the file editor
// should be optimized
class MagoMessageProcessor(private val info: QualityToolAnnotatorInfo<*>) : QualityToolMessageProcessor(info) {
    var startParsing = false
    val buffer = StringBuffer()

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

//    override fun getMessagePrefix() = "Mago"

    override fun parseLine(line: String) {
        val outputLine = line.trim()

//        println("parseLine $outputLine for $info")
        if (!startParsing) {
            if (!outputLine.startsWith("{")) {
                return
            }
            startParsing = true
        }

        buffer.append(outputLine)
    }

    override fun severityToDisplayLevel(severity: QualityToolMessage.Severity) =
        HighlightDisplayLevel.find(severity.name)

    override fun done() {
//        println("done: $buffer")
        val messageHandler = MagoJsonMessageHandler()

        messageHandler.parseJson(buffer.toString())
            .filter { it.myFile == this.file.virtualFile.canonicalPath }
//            .apply { println("problemList: $this") }
            .forEach { problem ->
                addMessage(
                    QualityToolMessage(
                        this,
                        TextRange(problem.startChar, problem.endChar),
                        problem.severity,
                        problem.message,
                        object : IntentionAction {
                            override fun getText() = "Suppress `${problem.code}`"

                            override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = true

                            override fun invoke(project: Project, editor: Editor, file: PsiFile) {
                                TODO("Not yet implemented")
                            }

                            override fun startInWriteAction() = true

                            override fun getFamilyName() = "Mago"
                        }
                    )
                )
            }
    }

    private class MagoJsonMessageHandler {
        fun parseJson(line: String): List<MagoProblemDescription> {
            return JsonParser.parseString(line)
                .apply { if (this == null || this.isJsonNull) return emptyList() }
                .asJsonObject.get("issues")
                .asJsonArray
                .map { it.asJsonObject }
                .map { issue ->
                    issue.get("annotations")
                        .asJsonArray
                        .map { it.asJsonObject }
                        .flatMap { annotation ->
                            val span = annotation.get("span").asJsonObject

                            listOf(
                                MagoProblemDescription(
                                    levelToSeverity(issue.get("level").asString),
                                    span.get("start").asJsonObject.get("line").asInt,
                                    span.get("start").asJsonObject.get("offset").asInt,
                                    span.get("end").asJsonObject.get("offset").asInt,
                                    "Mago: ${issue.get("message").asString.trimEnd('.')} [${issue.get("code").asString}]",
                                    span.get("file_id").asJsonObject.get("path").asString,
                                    issue.get("code").asString,
                                    issue.get("help").asString,
                                    issue.get("notes").asJsonArray.map { it.asString },
                                ),
                                MagoProblemDescription(
                                    levelToSeverity(issue.get("level").asString),
                                    span.get("start").asJsonObject.get("line").asInt,
                                    span.get("start").asJsonObject.get("offset").asInt,
                                    span.get("end").asJsonObject.get("offset").asInt,
                                    annotation.get("message").asString,
                                    span.get("file_id").asJsonObject.get("path").asString,
                                    issue.get("code").asString,
                                    issue.get("help").asString,
                                    issue.get("notes").asJsonArray.map { it.asString },
                                ),
                            )
                        }
                }
                .flatten()
        }

        fun levelToSeverity(level: String?) = when (level) {
            "Error" -> QualityToolMessage.Severity.ERROR
            "Warning" -> QualityToolMessage.Severity.WARNING
            else -> null
        }
    }
}
