package com.github.xepozz.mago.configuration.remote

import com.github.xepozz.mago.configuration.MagoConfigurableForm
import com.github.xepozz.mago.configuration.MagoConfiguration
import com.github.xepozz.mago.configuration.MagoConfigurationBaseManager
import com.github.xepozz.mago.configuration.MagoConfigurationManager
import com.github.xepozz.mago.configuration.MagoConfigurationProvider
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import com.jetbrains.php.remote.interpreter.PhpRemoteSdkAdditionalData
import com.jetbrains.php.remote.tools.quality.QualityToolByInterpreterConfigurableForm
import com.jetbrains.php.remote.tools.quality.QualityToolByInterpreterDialog
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
    ): MagoConfiguration? {
        if (project == null) return null

        val dialog = QualityToolByInterpreterDialog<MagoConfiguration?, MagoConfiguration?>(
            project,
            existingSettings,
            MagoConfigurationBaseManager.MAGO,
            MagoConfiguration::class.java,
            MagoQualityToolType.INSTANCE
        )

        if (!dialog.showAndGet()) return null

        val id = PhpInterpretersManagerImpl.getInstance(project).findInterpreterId(dialog.selectedInterpreterName)
        if (id.isNullOrEmpty()) {
            return QualityToolByInterpreterDialog.getLocalOrDefaultInterpreterConfiguration(
                dialog.selectedInterpreterName,
                project,
                MagoQualityToolType.INSTANCE
            ) as MagoConfiguration?
        }

        val settings = MagoRemoteConfiguration()
        settings.setInterpreterId(id)
        val data = PhpInterpretersManagerImpl.getInstance(project).findInterpreterDataById(id)
        this.fillDefaultSettings(
            project,
            settings,
            MagoConfigurationManager.getInstance(project).getOrCreateLocalSettings(),
            data,
            data is PhpRemoteSdkAdditionalData
        )
        return settings
    }

    override fun createConfigurationByInterpreter(interpreter: PhpInterpreter): MagoConfiguration {
        val settings = MagoRemoteConfiguration()
        settings.setInterpreterId(interpreter.id)
        return settings
    }

    companion object {
        private const val MAGO_BY_INTERPRETER: String = "mago_by_interpreter"
    }
}
