package com.github.xepozz.mago.qualityTool

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolExecutionException
import com.jetbrains.php.tools.quality.QualityToolMessage
import com.jetbrains.php.tools.quality.QualityToolMessageProcessor

// it does analysis everytime when you change a file in the file editor
// should be optimized
class MagoMessageProcessor(private val info: QualityToolAnnotatorInfo<*>) : QualityToolMessageProcessor(info) {
    var startParsing = false
    val buffer = StringBuffer()
    val errorBuffer = StringBuffer()

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun parseLine(line: String) {
        val outputLine = line.trim()

//        println("parseLine $outputLine for $info")
        if (!startParsing) {
            if (!outputLine.startsWith("{")) {
                errorBuffer.append(outputLine)
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
//            .apply {
//                thisLogger<MagoMessageProcessor>().info("files: ${map { it.file }}, current: ${file.virtualFile.canonicalPath}")
//            }
            .filter {
                val currentFilePath = file.virtualFile.canonicalPath ?: return@filter false

//                thisLogger<MagoMessageProcessor>().info("compare ${it.file} ends with $currentFilePath")
                it.file.endsWith(currentFilePath)
            }
            .map { problem ->
                QualityToolMessage(
                    this,
                    TextRange(problem.startChar, problem.endChar),
                    problem.severity,
                    problem.message,
                    MagoReformatFileAction(info.project),
                    MarkIgnoreAction(problem.code, problem.lineNumber),
                )
            }
            .apply {
                if (isEmpty() && errorBuffer.isNotEmpty()) {
                    throw QualityToolExecutionException("Caught errors while running Mago: $errorBuffer")
                }
            }
            .forEach { addMessage(it) }
    }
}
