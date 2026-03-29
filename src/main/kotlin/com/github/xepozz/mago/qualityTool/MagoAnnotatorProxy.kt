package com.github.xepozz.mago.qualityTool

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolAnnotator
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolConfiguration
import com.jetbrains.php.tools.quality.QualityToolMessage
import com.jetbrains.php.tools.quality.QualityToolMessageProcessor

/**
 * Minimal stub required by the QualityTool infrastructure.
 * The actual annotation pipeline uses [com.github.xepozz.mago.annotator.MagoExternalAnnotator].
 */
open class MagoAnnotatorProxy : QualityToolAnnotator<MagoValidationInspection>() {
    companion object {
        val INSTANCE = MagoAnnotatorProxy()
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun createMessageProcessor(collectedInfo: QualityToolAnnotatorInfo<MagoValidationInspection>) =
        object : QualityToolMessageProcessor(collectedInfo) {
            override fun parseLine(line: String) {}
            override fun severityToDisplayLevel(severity: QualityToolMessage.Severity) =
                HighlightDisplayLevel.WEAK_WARNING

            override fun getQualityToolType() = MagoQualityToolType.INSTANCE
            override fun done() {}
        }

    override fun getPairedBatchInspectionShortName() = qualityToolType.inspectionId

    override fun getOptions(
        filePath: String?,
        inspection: MagoValidationInspection,
        profile: InspectionProfile?,
        project: Project,
    ): List<String> = emptyList()

    override fun createAnnotatorInfo(
        file: PsiFile?,
        tool: MagoValidationInspection?,
        inspectionProfile: InspectionProfile?,
        project: Project?,
        configuration: QualityToolConfiguration?,
        isOnTheFly: Boolean
    ): QualityToolAnnotatorInfo<MagoValidationInspection> {
        return super.createAnnotatorInfo(file, tool, inspectionProfile, project, configuration, false)
    }

    override fun runOnTempFiles() = true
}
