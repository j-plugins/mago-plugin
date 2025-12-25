package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.configuration.MagoConfigurationBaseManager.Companion.MAGO
import com.github.xepozz.mago.qualityTool.MagoCustomOptionsForm
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.tools.quality.QualityToolConfigurableForm
import com.jetbrains.php.tools.quality.QualityToolConfiguration
import com.jetbrains.php.tools.quality.QualityToolType

// TODO: change to PhpStanOptionsPanel
class MagoConfigurableForm(project: Project, configuration: MagoConfiguration) :
    QualityToolConfigurableForm<MagoConfiguration>(project, configuration, MAGO, "mago") {
    override fun getQualityToolType(): QualityToolType<QualityToolConfiguration> {
        try {
            @Suppress("UNCHECKED_CAST")
            return MagoQualityToolType.INSTANCE as QualityToolType<QualityToolConfiguration>
        } catch (e: Throwable) {
            println("error: $e")
            throw e
        }
    }

    // allow any files to be selected
    override fun isValidToolFile(file: VirtualFile?): Boolean = true

    override fun getCustomConfigurable(project: Project, configuration: MagoConfiguration) =
        MagoCustomOptionsForm(project, configuration)

    override fun getHelpTopic() = "reference.settings.php.mago"

    override fun validateWithNoAnsi() = false

    override fun validateMessage(message: String): Pair<Boolean, String> {
        val regex = Regex("^mago (?<version>.+)$")

        return regex.find(message)?.groups?.get("version")
            ?.let { Pair.create(true, "OK, Mago version ${it.value}") }
            ?: Pair.create(false, PhpBundle.message("quality.tool.can.not.determine.version", message))
    }
}
