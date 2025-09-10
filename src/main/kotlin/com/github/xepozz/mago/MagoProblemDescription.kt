package com.github.xepozz.mago

import com.jetbrains.php.tools.quality.QualityToolMessage
import com.jetbrains.php.tools.quality.QualityToolXmlMessageProcessor

class MagoProblemDescription(
    severity: QualityToolMessage.Severity?,
    lineNumber: Int,
    val startChar: Int,
    val endChar: Int,
    message: String,
    file: String,
    code: String,
    help: String,
    notes: List<String>,
) : QualityToolXmlMessageProcessor.ProblemDescription(
    severity,
    lineNumber,
    startChar,
    message,
    file,
)
