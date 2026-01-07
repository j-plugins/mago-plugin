package com.github.xepozz.mago.qualityTool

import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.php.tools.quality.QualityToolMessage

class MagoJsonMessageHandler {
    fun parseJson(line: String): List<MagoProblemDescription> {
//        println("JSON: $line")
        return JsonParser.parseString(line)
            .apply { if (this == null || this.isJsonNull) return emptyList() }
            .asJsonObject
            .getAsJsonArray("issues")
            ?.map { it.asJsonObject }
            ?.flatMap { issue ->
                issue.getAsJsonArray("annotations")
                    ?.map { it.asJsonObject }
                    ?.mapNotNull { annotation ->
                        val span = annotation.getAsJsonObject("span") ?: return@mapNotNull null

                        MagoProblemDescription(
                            levelToSeverity(issue.get("level").asString),
                            span.getAsJsonObject("start")?.get("line")?.asInt ?: return@mapNotNull null,
                            span.getAsJsonObject("start")?.get("offset")?.asInt ?: return@mapNotNull null,
                            span.getAsJsonObject("end")?.get("offset")?.asInt ?: return@mapNotNull null,
                            "Mago: ${issue.get("message").asString.trimEnd('.')} [${issue.get("code").asString}]",
                            span.getAsJsonObject("file_id")
                                ?.get("path")
                                ?.asString
                                .let { FileUtil.toCanonicalPath(it) ?: "" },
                            issue.get("code")?.asString ?: "",
                            issue.get("help")?.asString ?: "",
                            issue.getAsJsonArray("notes")?.map { it.asString } ?: emptyList(),
                        )
                    }
                    ?: emptyList()
            }
            ?: emptyList()
    }

    fun levelToSeverity(level: String?) = when (level) {
        "Error" -> QualityToolMessage.Severity.ERROR
        "Warning" -> QualityToolMessage.Severity.WARNING
        else -> null
    }
}