package com.github.xepozz.mago.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.jetbrains.php.tools.quality.QualityToolConfigurationManager

@Service(Service.Level.PROJECT)
class MagoConfigurationManager(project: Project?) :
    QualityToolConfigurationManager<MagoConfiguration>(project) {
    init {
        if (project != null) {
            myProjectManager = project.getService(MagoProjectConfigurationManager::class.java)
        }
        myApplicationManager = ApplicationManager.getApplication().getService(MagoAppConfigurationManager::class.java)
    }

    @Service(Service.Level.PROJECT)
    @State(name = "Mago", storages = [Storage("php-tools.xml")])
    internal class MagoProjectConfigurationManager : MagoConfigurationBaseManager()

    @Service(Service.Level.APP)
    @State(name = "Mago", storages = [Storage("php-tools.xml")])
    internal class MagoAppConfigurationManager : MagoConfigurationBaseManager()

    companion object {
        fun getInstance(project: Project): MagoConfigurationManager =
            project.getService(MagoConfigurationManager::class.java)
    }
}
