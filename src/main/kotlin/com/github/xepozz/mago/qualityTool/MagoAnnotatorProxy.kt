package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.codeInspection.InspectionProfile
import com.intellij.execution.configurations.ParametersList
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

        fun getFormatOptions(settings: MagoProjectConfiguration, project: Project, files: Collection<String>) =
            buildList {
                addWorkspace(project)
                addConfig(project, settings)

                add("fmt")
                addAll(files)
            }
                .plus(ParametersList.parse(settings.formatAdditionalParameters))
                .apply { println("format options: ${this.joinToString(" ")}") }

        fun getAnalyzeOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) = buildList {
            addWorkspace(project)
            addConfig(project, settings)

            add("analyze")
            add(filePath)
            add("--reporting-format=json")
//            filePath?.let { add(it) }
        }
            .plus(ParametersList.parse(settings.analyzeAdditionalParameters))
            .apply { println("analyze options: ${this.joinToString(" ")}") }

        private fun MutableList<String>.addWorkspace(project: Project) {
            val projectPath = updateIfRemoteMappingExists(
                project.basePath ?: return,
                project,
                INSTANCE.qualityToolType
            )
            add("--workspace=$projectPath")
        }

        private fun MutableList<String>.addConfig(project: Project, settings: MagoProjectConfiguration) {
            val configurationFile = updateIfRemoteMappingExists(
                settings.configurationFile,
                project,
                INSTANCE.qualityToolType
            )
            if (configurationFile.isNotEmpty()) {
                add("--config=$configurationFile")
            }
        }
    }

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

    override fun getOptions(
        filePath: String?,
        inspection: MagoValidationInspection,
        profile: InspectionProfile?,
        project: Project,
    ): List<String> {
        checkNotNull(filePath)
        val settings = project.getService(MagoProjectConfiguration::class.java)

        return getAnalyzeOptions(settings, project, filePath)
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun createMessageProcessor(collectedInfo: QualityToolAnnotatorInfo<MagoValidationInspection>) =
        MagoMessageProcessor(collectedInfo)

    override fun getPairedBatchInspectionShortName() = qualityToolType.inspectionId

    /**
     * It seems it may break work with Docker,
     * but in another case, errors are mapped over wrong code tokes
     */
    override fun runOnTempFiles() = true
}
