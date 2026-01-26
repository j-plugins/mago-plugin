package com.github.xepozz.mago.config

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.php.tools.quality.QualityToolProcessCreator

@Service(Service.Level.PROJECT)
class MagoSchemaHolder(val project: Project) {
    private var mySchemaFile: VirtualFile? = null
    private var run = false

    fun getSchema() = mySchemaFile

    fun dumpSchema() {
        // don't run dump several times
        if (run) return
        run = true

        val magoConfiguration = MagoProjectConfiguration.getInstance(project)
            .run { findConfigurationById(selectedConfigurationId, project) }
            ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val magoExecutable = magoConfiguration.toolPath

            QualityToolProcessCreator
                .getToolOutput(
                    project,
                    null,
                    magoExecutable,
                    1,
                    "Dumping schema...",
                    null,
                    "config",
                    "--schema",
                )
                .apply {
                    if (exitCode != 0) {
                        error("Failed to dump mago schema: ${stderr}")
                    }
                    mySchemaFile = LightVirtualFile("mago.schema.json", stdout)
                    restartSchemaServices()
                }
        }
    }

    private fun restartSchemaServices() {
        val openProjects = ProjectManager.getInstance().openProjects
        for (project in openProjects) {
            val service = project.getService(JsonSchemaService::class.java)
            service.reset()
        }
    }
}
