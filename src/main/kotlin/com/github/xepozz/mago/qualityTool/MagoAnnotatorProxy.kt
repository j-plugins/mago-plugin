package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.codeInspection.InspectionProfile
import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.composer.ComposerDetectionManager
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
                addConfig(settings)

                add("fmt")
                addAll(files)
            }
                .plus(ParametersList.parse(settings.formatAdditionalParameters))
                .apply { println("format options: ${this.joinToString(" ")}") }

        fun getAnalyzeOptions(settings: MagoProjectConfiguration, project: Project, filePath: String?) = buildList {
            addWorkspace(project)
            addConfig(settings)

            add("analyze")
            add("--reporting-format=json")
//            filePath?.let { add(it) }
        }
            .plus(ParametersList.parse(settings.analyzeAdditionalParameters))
            .apply { println("analyze options: ${this.joinToString(" ")}") }

        private fun MutableList<String>.addWorkspace(project: Project) {
            val configs = ComposerDetectionManager.detectComposer(project)
            println("configs: $configs")
            val projectPath = configs?.main ?: project.basePath ?: return

            add("--workspace=$projectPath")
        }

        private fun MutableList<String>.addConfig(settings: MagoProjectConfiguration) {
            if (settings.configurationFile.isNotEmpty()) {
                add("--config=${settings.configurationFile}")
            }
        }
    }

    override fun getOptions(
        filePath: String?,
        inspection: MagoValidationInspection,
        profile: InspectionProfile?,
        project: Project,
    ): List<String> {
        val settings = project.getService(MagoProjectConfiguration::class.java)

        return getAnalyzeOptions(settings, project, filePath)
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

        println("tool path: ${configuration.toolPath}")

        return QualityToolAnnotatorInfo(
            file,
            tool,
            inspectionProfile,
            project,
            configuration.interpreterId,
            configuration.toolPath,
            configuration.maxMessagesPerFile,
            configuration.timeout,
            false
        )
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun createMessageProcessor(collectedInfo: QualityToolAnnotatorInfo<MagoValidationInspection>) =
        MagoMessageProcessor(collectedInfo)

    override fun getPairedBatchInspectionShortName() = qualityToolType.inspectionId

    override fun runOnTempFiles() = false
}
