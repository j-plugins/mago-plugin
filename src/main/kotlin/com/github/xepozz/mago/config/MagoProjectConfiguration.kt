package com.github.xepozz.mago.config

import com.github.xepozz.mago.MagoQualityToolType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.php.tools.quality.QualityToolProjectConfiguration

@Service(Service.Level.PROJECT)
@State(name = "MagoProjectConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MagoProjectConfiguration : QualityToolProjectConfiguration<MagoConfiguration>(),
    PersistentStateComponent<MagoProjectConfiguration> {

    override fun getState() = this

    override fun loadState(state: MagoProjectConfiguration) {
        XmlSerializerUtil.copyBean(state, this)
    }

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    companion object {
        fun getInstance(project: Project): MagoProjectConfiguration =
            project.getService(MagoProjectConfiguration::class.java)
    }
}