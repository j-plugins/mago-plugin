package com.github.xepozz.mago.analysis

import com.github.xepozz.mago.model.MagoAnnotationSpan
import com.github.xepozz.mago.model.MagoEdit
import com.github.xepozz.mago.model.MagoProblemDescription
import com.github.xepozz.mago.model.MagoReplacement
import com.github.xepozz.mago.model.MagoSeverity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil

class MagoJsonMessageHandler {
    fun parseJson(line: String, category: String = "analysis"): List<MagoProblemDescription> {
        return JsonParser.parseString(line)
            .apply { if (this == null || this.isJsonNull) return emptyList() }
            .asJsonObject
            .getAsJsonArray("issues")
            ?.map { it.asJsonObject }
            ?.flatMap { issue ->
                val code = issue.get("code").asString
                val help = issue.get("help")?.asString ?: ""
                val notes = issue.getAsJsonArray("notes")?.map { it.asString } ?: emptyList()
                val edits = parseEdits(issue)

                val allAnnotations = issue.getAsJsonArray("annotations")
                    ?.map { it.asJsonObject }
                    ?: emptyList()

                val secondarySpans = allAnnotations
                    .filter { it.get("kind").asString == "Secondary" }
                    .mapNotNull { parseAnnotationSpan(it) }

                allAnnotations
                    .filter { ann ->
                        val kind = ann.get("kind").asString
                        kind == "Primary" || (code == "type-inspection" && kind == "Secondary")
                    }
                    .mapNotNull { annotation ->
                        val span = annotation.getAsJsonObject("span") ?: return@mapNotNull null
                        val filePath = extractFilePath(span) ?: return@mapNotNull null

                        val kind = annotation.get("kind").asString
                        val message = if (kind == "Primary") {
                            issue.get("message").asString.trimEnd('.')
                        } else {
                            annotation.get("message").asString.trimEnd('.')
                        }

                        MagoProblemDescription(
                            severity = levelToSeverity(issue.get("level").asString),
                            lineNumber = span.getAsJsonObject("start")?.get("line")?.asInt
                                ?: return@mapNotNull null,
                            startChar = span.getAsJsonObject("start")?.get("offset")?.asInt
                                ?: return@mapNotNull null,
                            endChar = span.getAsJsonObject("end")?.get("offset")?.asInt
                                ?: return@mapNotNull null,
                            myMessage = message,
                            myFile = filePath,
                            code = code,
                            category = category,
                            help = help,
                            notes = notes,
                            edits = edits,
                            secondaryAnnotations = secondarySpans,
                        )
                    }
            }
            ?: emptyList()
    }

    private fun parseEdits(issue: JsonObject): List<MagoEdit> {
        return issue.getAsJsonArray("edits")?.map { it.asJsonArray }?.map { editArray ->
            val fileId = editArray.get(0).asJsonObject
            val replacements = editArray.get(1).asJsonArray.map { it.asJsonObject }.map { replacement ->
                val range = replacement.getAsJsonObject("range")
                MagoReplacement(
                    range.get("start").asInt,
                    range.get("end").asInt,
                    replacement.get("new_text").asString,
                    replacement.get("safety").asString
                )
            }
            MagoEdit(fileId.get("name").asString, normalizePathFromMago(fileId.get("path").asString), replacements)
        } ?: emptyList()
    }

    private fun parseAnnotationSpan(annotation: JsonObject): MagoAnnotationSpan? {
        val span = annotation.getAsJsonObject("span") ?: return null
        val filePath = extractFilePath(span) ?: return null
        return MagoAnnotationSpan(
            message = annotation.get("message")?.asString ?: "",
            kind = annotation.get("kind")?.asString ?: "Secondary",
            filePath = filePath,
            startOffset = span.getAsJsonObject("start")?.get("offset")?.asInt ?: return null,
            endOffset = span.getAsJsonObject("end")?.get("offset")?.asInt ?: return null,
            line = span.getAsJsonObject("start")?.get("line")?.asInt ?: return null,
        )
    }

    private fun extractFilePath(span: JsonObject): String? {
        return span.getAsJsonObject("file_id")
            ?.get("path")
            ?.asString
            ?.removePrefix("\\\\?\\")
            ?.let { FileUtil.toSystemIndependentName(normalizePathFromMago(it)) }
    }

    /** Removes redundant /./ and normalize the path from mago output (e.g.: /opt/project/./src/foo.php). */
    private fun normalizePathFromMago(path: String): String =
        path.replace("/./", "/")

    fun levelToSeverity(level: String?): MagoSeverity = when (level) {
        "Error" -> MagoSeverity.ERROR
        "Warning" -> MagoSeverity.WARNING
        "Help", "Note" -> MagoSeverity.INFO
        else -> MagoSeverity.INFO
    }
}
