package com.github.xepozz.mago.model

enum class MagoSeverity {
    ERROR,
    WARNING,
    INFO
}

data class MagoAnnotationSpan(
    val message: String,
    val kind: String,
    val filePath: String,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
)

data class MagoProblemDescription(
    val severity: MagoSeverity,
    val lineNumber: Int,
    val startChar: Int,
    val endChar: Int,
    var myMessage: String,
    val myFile: String,
    val code: String,
    val category: String,
    val help: String,
    val notes: List<String>,
    val edits: List<MagoEdit> = emptyList(),
    val secondaryAnnotations: List<MagoAnnotationSpan> = emptyList(),
)

data class MagoEdit(
    val name: String,
    val path: String,
    val replacements: List<MagoReplacement>
)

data class MagoReplacement(
    val start: Int,
    val end: Int,
    val newText: String,
    val safety: String
)
