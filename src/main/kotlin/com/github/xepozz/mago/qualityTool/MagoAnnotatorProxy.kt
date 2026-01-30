package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.normalizePath
import com.intellij.codeInspection.InspectionProfile
import com.intellij.execution.configurations.ParametersList
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolAnnotator
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolConfiguration
import java.io.File

open class MagoAnnotatorProxy : QualityToolAnnotator<MagoValidationInspection>() {
    companion object {
        private val LOG: Logger = Logger.getInstance(MagoAnnotatorProxy::class.java)

        val INSTANCE = MagoAnnotatorProxy()

        fun getFormatOptions(settings: MagoProjectConfiguration, project: Project, files: Collection<String>) =
            buildList {
                addWorkspace(project)
                addConfig(project, settings)

                add("fmt")
                addAll(files.map { toWorkspaceRelativePath(project, it) })
            }
                .plus(ParametersList.parse(settings.formatAdditionalParameters))
                .apply { println("format options: ${this.joinToString(" ")}") }

        fun getAnalyzeOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) = buildList {
            addWorkspace(project)
            addConfig(project, settings)

            add("analyze")
            add(toWorkspaceRelativePath(project, filePath))
            add("--reporting-format=json")
//            filePath?.let { add(it) }
        }
            .plus(ParametersList.parse(settings.analyzeAdditionalParameters))
            .apply {
                val options = this.joinToString(" ")
                println("analyze options: $options")
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Mago")
                    .createNotification("Analyze options", """Options: $options, filePath: $filePath""", NotificationType.INFORMATION)
                    .notify(project)
            }

        private fun toWorkspaceRelativePath(project: Project, absoluteFilePath: String): String {
            val basePath = project.basePath ?: return absoluteFilePath
            return toRelativePath(basePath, absoluteFilePath)
        }

        internal fun toRelativePath(basePath: String, absoluteFilePath: String): String {
            val basePath = basePath.normalizePath()
            val absoluteFilePath = absoluteFilePath.normalizePath()
            println("project base: $basePath")

            val relative = FileUtil.getRelativePath(basePath, absoluteFilePath, '/')
            return ensureMagoPath(relative ?: absoluteFilePath)
        }

        internal fun ensureMagoPath(path: String): String {
            return when {
                path.isEmpty() -> path
                FileUtil.isAbsolute(path) || isWindowsAbsolute(path) || path.startsWith("\\\\?\\") -> path
                path.startsWith("./") || path.startsWith(".\\") -> path
                // Mago ignores relative paths unless they are explicitly prefixed.
                else -> ".${File.separator}$path"
            }
        }

        private fun isWindowsAbsolute(path: String): Boolean {
            return path.length >= 2 && path[0].isLetter() && path[1] == ':'
        }

        private fun MutableList<String>.addWorkspace(project: Project) {
            val projectPath = updateIfRemoteMappingExists(
                project.basePath ?: return,
                project,
                INSTANCE.qualityToolType
            ).let { FileUtil.toSystemIndependentName(it) }
            add("--workspace=$projectPath")
        }

        private fun MutableList<String>.addConfig(project: Project, settings: MagoProjectConfiguration) {
            val configurationFile = updateIfRemoteMappingExists(
                settings.configurationFile,
                project,
                INSTANCE.qualityToolType
            ).let { toWorkspaceRelativePath(project, it) }

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
