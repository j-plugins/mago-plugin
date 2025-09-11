package com.github.xepozz.mago

import com.jetbrains.php.tools.quality.QualityToolMessage
import com.jetbrains.php.tools.quality.QualityToolXmlMessageProcessor

class MagoProblemDescription(
    severity: QualityToolMessage.Severity?,
    lineNumber: Int,
    val startChar: Int,
    val endChar: Int,
    var myMessage: String,
    val myFile: String,
    val code: String,
    val help: String,
    val notes: List<String>,
) : QualityToolXmlMessageProcessor.ProblemDescription(
    severity,
    lineNumber,
    startChar,
    myMessage,
    myFile,
)
