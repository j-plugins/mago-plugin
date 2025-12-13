package com.github.xepozz.mago.configuration.remote

import com.github.xepozz.mago.configuration.MagoConfigurableForm
import com.github.xepozz.mago.configuration.MagoConfiguration
import com.github.xepozz.mago.configuration.MagoConfigurationProvider
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.remote.tools.quality.QualityToolByInterpreterConfigurableForm
import com.jetbrains.php.tools.quality.QualityToolConfigurableForm
import org.jdom.Element

class MagoRemoteConfigurationProvider : MagoConfigurationProvider() {
    override fun canLoad(tagName: String) = tagName == MAGO_BY_INTERPRETER

    override fun load(element: Element) = XmlSerializer.deserialize(element, MagoRemoteConfiguration::class.java)

    override fun createConfigurationForm(
        project: Project,
        settings: MagoConfiguration,
    ): QualityToolConfigurableForm<*>? {
        if (settings !is MagoRemoteConfiguration) {
            return null
        }

        val delegate = MagoConfigurableForm(project, settings)
        return QualityToolByInterpreterConfigurableForm(
            project,
            settings,
            delegate,
        )
    }

    override fun createNewInstance(
        project: Project?,
        existingSettings: List<MagoConfiguration>,
    ) = MagoRemoteConfiguration()

    override fun createConfigurationByInterpreter(interpreter: PhpInterpreter): MagoConfiguration {
        val settings = MagoRemoteConfiguration()
        settings.setInterpreterId(interpreter.id)
        return settings
    }

    companion object {
        private const val MAGO_BY_INTERPRETER: String = "mago_by_interpreter"
    }
}
