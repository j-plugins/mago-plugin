package com.github.xepozz.mago

import com.google.gson.JsonParser
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.tools.quality.*

class MagoMessageProcessor(private val info: QualityToolAnnotatorInfo<*>) : QualityToolMessageProcessor(info) {
    var startParsing = false
    val buffer = StringBuffer()

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun getMessagePrefix() = "Mago"

    override fun parseLine(line: String) {
        val outputLine = line.trim()
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

    override fun addMessage(message: QualityToolMessage) {
        super.addMessage(message)
    }

    override fun done() {
//        println("done: $buffer")
        val messageHandler = MagoJsonMessageHandler()

        messageHandler.parseJson(buffer.toString())
//            .apply { println("problemList: $this") }
            .forEach { problem ->
                addMessage(
                    QualityToolMessage(
                        this,
                        TextRange(problem.startChar, problem.endChar),
                        problem.severity,
                        problem.message,
                    )
                )
            }
    }

    private class MagoJsonMessageHandler {
        fun parseJson(line: String) = JsonParser.parseString(line)
            .asJsonObject.get("issues")
            .asJsonArray
            .map { it.asJsonObject }
            .map { issue ->
                issue.get("annotations")
                    .asJsonArray
                    .map { it.asJsonObject }
                    .map { annotation ->
                        val span = annotation.get("span").asJsonObject

                        MagoProblemDescription(
                            levelToSeverity(issue.get("level").asString),
                            span.get("start").asJsonObject.get("line").asInt,
                            span.get("start").asJsonObject.get("offset").asInt,
                            span.get("end").asJsonObject.get("offset").asInt,
                            buildString {
                                append(issue.get("message").asString)
                                append(": ")
                                append(annotation.get("message").asString)
                            },
                            span.get("file_id").asJsonObject.get("path").asString,
                            issue.get("code").asString,
                            issue.get("help").asString,
                            issue.get("notes").asJsonArray.map { it.asString },
                        )
                    }
            }
            .flatten()

        fun levelToSeverity(level: String?) = when (level) {
            "Error" -> QualityToolMessage.Severity.ERROR
            "Warning" -> QualityToolMessage.Severity.WARNING
            else -> null
        }
    }
}
