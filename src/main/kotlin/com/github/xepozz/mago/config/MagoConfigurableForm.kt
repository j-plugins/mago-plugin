package com.github.xepozz.mago.config

import com.github.xepozz.mago.config.MagoConfigurationBaseManager.Companion.MAGO
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.tools.quality.*

// TODO: change to PhpStanOptionsPanel
class MagoConfigurableForm(project: Project, configuration: MagoConfiguration) :
    QualityToolConfigurableForm<MagoConfiguration>(project, configuration, MAGO, "Mago") {
    override fun getQualityToolType(): QualityToolType<QualityToolConfiguration> {
        try {
            @Suppress("UNCHECKED_CAST")
            return MagoQualityToolType.INSTANCE as QualityToolType<QualityToolConfiguration>
        } catch (e: Throwable) {
            throw e
        }
    }

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
