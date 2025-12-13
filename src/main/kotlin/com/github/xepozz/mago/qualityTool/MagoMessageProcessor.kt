package com.github.xepozz.mago.qualityTool

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.util.TextRange
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
        MagoJsonMessageHandler()
            .parseJson(buffer.toString())
            .apply { println("parsed: ${this.size}") }
            .filter { it.myFile == this.file.virtualFile.canonicalPath }
            .apply { println("problemList: ${this.size}") }
            .forEach { problem ->
                addMessage(
                    QualityToolMessage(
                        this,
                        TextRange(problem.startChar, problem.endChar),
                        problem.severity,
                        problem.message,
                        MarkIgnoreAction(problem.code, problem.lineNumber)
                    )
                )
            }
    }
}
