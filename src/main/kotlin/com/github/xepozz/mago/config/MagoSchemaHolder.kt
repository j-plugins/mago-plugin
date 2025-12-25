package com.github.xepozz.mago.config

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class MagoSchemaHolder(val project: Project) {
    private var mySchemaFile: VirtualFile? = null
    private var run = false

    fun getSchema() = mySchemaFile

    fun dumpSchema() {
        // don't run dump several times
        if (run) return
        run = true

        val magoConfiguration = MagoProjectConfiguration
            .getInstance(project)
            .findSelectedConfiguration(project)
            ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val magoExecutable = magoConfiguration.toolPath

            val command = GeneralCommandLine().apply {
                exePath = magoExecutable
                addParameter("config")
                addParameter("--schema")
            }
            val processHandler = OSProcessHandler(command)

            val output = ProcessOutput()
            processHandler.addProcessListener(CapturingProcessAdapter(output))
            processHandler.startNotify()
            processHandler.waitFor(1.seconds.inWholeMilliseconds)
            if (output.exitCode != 0) {
                error("Failed to dump mago schema: ${output.stderr}")
            }
            mySchemaFile = LightVirtualFile("mago.schema.json", output.stdout)

            restartSchemaServices()
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
