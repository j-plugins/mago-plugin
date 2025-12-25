package com.github.xepozz.mago.formatter

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.qualityTool.MagoAnnotatorProxy
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.config.commandLine.PhpCommandSettings
import com.jetbrains.php.tools.quality.QualityToolConfiguration
import com.jetbrains.php.tools.quality.QualityToolReformatFile
import com.jetbrains.php.tools.quality.QualityToolValidationException

class MagoReformatFile(val project: Project) : QualityToolReformatFile() {
    val settings = project.getService(MagoProjectConfiguration::class.java)

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun fillArguments(options: MutableList<String>, command: PhpCommandSettings, workDirectory: String?) {
        for (i in 0..<options.size - 1) {
            if (i == 0) {
                command.addPathArgument(options[i])
            } else {
                command.addArgument(options[i])
            }
        }
    }

    override fun getCurrentConfiguration(project: Project): QualityToolConfiguration? {
        try {
            return MagoProjectConfiguration.getInstance(project).findSelectedConfiguration(project)
        } catch (e: QualityToolValidationException) {
            LOG.warn(e.message)
            return null
        }
    }

    override fun getName() = MagoBundle.message("quality.tool.mago")

    override fun getToolPath(settings: QualityToolConfiguration?) = settings?.toolPath

    override fun getOptions(project: Project, virtualFiles: Array<VirtualFile>): List<String> {
        val projectPath = project.basePath ?: return emptyList()
        val files = virtualFiles.map { it.path }

        val formatOptions = MagoAnnotatorProxy.getFormatOptions(projectPath, files)
//        println("format options: ${formatOptions.joinToString(" ")}")
        return formatOptions
    }
}
