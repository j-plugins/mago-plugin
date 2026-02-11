package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import com.jetbrains.php.tools.quality.QualityToolProjectConfiguration

@Service(Service.Level.PROJECT)
@State(name = "MagoProjectConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MagoProjectConfiguration : QualityToolProjectConfiguration<MagoConfiguration>(),
    PersistentStateComponent<MagoProjectConfiguration> {

    var enabled: Boolean = true

    var guardEnabled = false
    var linterEnabled = false
    var formatterEnabled = true

    var analyzeAdditionalParameters = ""
    var lintAdditionalParameters = ""
    var guardAdditionalParameters = ""
    var formatAdditionalParameters = ""
    var formatAfterFix = false

    var configurationFile = ""
    var workspaceMappings: MutableList<MagoWorkspaceMapping> = mutableListOf()
    var debug = false

    override fun getState() = this

    override fun loadState(state: MagoProjectConfiguration) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    fun findSelectedConfigurationSafe(project: Project): MagoConfiguration? {
        return try {
            findSelectedConfiguration(project)
        } catch (_: Exception) {
            null
        }
    }

    fun resolveInterpreter(project: Project): PhpInterpreter? {
        val config = findSelectedConfigurationSafe(project) ?: return null
        val interpreterId = config.interpreterId
        if (interpreterId.isNullOrBlank()) return null
        return PhpInterpretersManagerImpl.getInstance(project).findInterpreterById(interpreterId)
    }

    fun isRemoteInterpreter(project: Project): Boolean =
        resolveInterpreter(project)?.isRemote == true

    fun getEffectiveToolPath(project: Project): String =
        findSelectedConfigurationSafe(project)?.toolPath ?: ""

    companion object {
        fun getInstance(project: Project): MagoProjectConfiguration =
            project.getService(MagoProjectConfiguration::class.java)
    }
}
