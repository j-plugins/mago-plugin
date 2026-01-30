package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.normalizePath
import com.intellij.codeInspection.InspectionProfile
import com.intellij.execution.configurations.ParametersList
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
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
                val workspace = findWorkspace(project, files.firstOrNull())
                addWorkspace(workspace, project)
                addConfig(workspace, project, settings)

                add("fmt")
                addAll(files.map { toWorkspaceRelativePath(workspace, it) })
            }
                .plus(ParametersList.parse(settings.formatAdditionalParameters))
                .apply { val options = this.joinToString(" ")
                    println("format options: $options")
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Mago")
                        .createNotification("Format options", """Options: $options, filePaths: ${files.joinToString(", ") { it }}""", NotificationType.INFORMATION)
                        .notify(project)
                }

        fun findWorkspace(project: Project, filePath: String?): VirtualFile {
            if (filePath == null) return project.baseDir
            val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return project.baseDir
            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return project.baseDir
            return module.rootManager.contentRoots.firstOrNull() ?: project.baseDir
        }

        fun getAnalyzeOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) = buildList {
            val workspace = findWorkspace(project, filePath)
            addWorkspace(workspace, project)
            addConfig(workspace, project, settings)

            add("analyze")
            add(toWorkspaceRelativePath(workspace, filePath))
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
        private fun toWorkspaceRelativePath(workspace: VirtualFile, absoluteFilePath: String): String {
            return toRelativePath(workspace.path, absoluteFilePath)
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

        private fun MutableList<String>.addWorkspace(workspace: VirtualFile, project: Project) {
            val projectPath = updateIfRemoteMappingExists(
                workspace.path,
                project,
                INSTANCE.qualityToolType
            ).let { FileUtil.toSystemIndependentName(it) }
            add("--workspace=$projectPath")
        }

        private fun MutableList<String>.addConfig(workspace: VirtualFile, project: Project, settings: MagoProjectConfiguration) {
            val configurationFile = updateIfRemoteMappingExists(
                settings.configurationFile,
                project,
                INSTANCE.qualityToolType,
            ).let { toWorkspaceRelativePath(workspace, it) }

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
