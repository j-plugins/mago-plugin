package com.github.xepozz.mago

import com.google.gson.JsonParser
import com.jetbrains.php.tools.quality.QualityToolMessage

class MagoJsonMessageHandler {
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
//                                MagoProblemDescription(
//                                    levelToSeverity(issue.get("level").asString),
//                                    span.get("start").asJsonObject.get("line").asInt,
//                                    span.get("start").asJsonObject.get("offset").asInt,
//                                    span.get("end").asJsonObject.get("offset").asInt,
//                                    annotation.get("message").asString,
//                                    span.get("file_id").asJsonObject.get("path").asString,
//                                    issue.get("code").asString,
//                                    issue.get("help").asString,
//                                    issue.get("notes").asJsonArray.map { it.asString },
//                                ),
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