package com.github.xepozz.mago

import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolAnnotator
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolConfiguration

open class MagoAnnotatorProxy : QualityToolAnnotator<MagoValidationInspection>() {
    companion object {
        private val LOG: Logger = Logger.getInstance(MagoAnnotatorProxy::class.java)

        val INSTANCE = MagoAnnotatorProxy()

        fun getFormatOptions(projectPath: String, files: Collection<String>) = buildList {
            add("--workspace=$projectPath")

            add("fmt")
            addAll(files)
        }
//            .apply { println("format options: ${this.joinToString(" ")}") }

        fun getAnalyzeOptions(projectPath: String, filePath: String?) = buildList {
            add("--workspace=$projectPath")

            add("analyze")
            add("--reporting-format=json")
//            filePath?.let { add(it) }
        }
//            .apply { println("analyze options: ${this.joinToString(" ")}") }
    }

    override fun getOptions(
        filePath: String?,
        inspection: MagoValidationInspection,
        profile: InspectionProfile?,
        project: Project,
    ): List<String> {
        val projectPath = project.basePath ?: return emptyList()

        return getAnalyzeOptions(projectPath, filePath)
    }

    override fun createAnnotatorInfo(
        file: PsiFile?,
        tool: MagoValidationInspection,
        inspectionProfile: InspectionProfile,
        project: Project,
        configuration: QualityToolConfiguration,
        isOnTheFly: Boolean,
    ): QualityToolAnnotatorInfo<MagoValidationInspection> {
        if (!isOnTheFly) {
            LOG.warn("isOnTheFly is False")
        }

        return QualityToolAnnotatorInfo(file, tool, inspectionProfile, project, configuration, false)
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun createMessageProcessor(collectedInfo: QualityToolAnnotatorInfo<MagoValidationInspection>) =
        MagoMessageProcessor(collectedInfo)

    override fun getPairedBatchInspectionShortName() = qualityToolType.inspectionId

    override fun runOnTempFiles() = false
}
