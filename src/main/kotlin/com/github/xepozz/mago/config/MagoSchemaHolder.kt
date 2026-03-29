package com.github.xepozz.mago.config

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.execution.LocalMagoRunner
import com.github.xepozz.mago.execution.MagoRunner
import com.github.xepozz.mago.execution.RemoteInterpreterMagoRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService

@Service(Service.Level.PROJECT)
class MagoSchemaHolder(val project: Project) {
    private var mySchemaFile: VirtualFile? = null
    private var run = false

    fun getSchema() = mySchemaFile

    fun dumpSchema() {
        if (run) return
        run = true

        val settings = MagoProjectConfiguration.getInstance(project)

        val (runner, magoExecutable) = chooseRunnerAndExe(settings)
        if (magoExecutable.isBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            val out = runner.run(
                project = project,
                exePath = magoExecutable,
                args = listOf("config", "--schema"),
                timeoutMs = 30_000
            )

            if (out.exitCode != 0) {
                run = false
                return@executeOnPooledThread
            }

            mySchemaFile = LightVirtualFile("mago.schema.json", out.stdout)
            restartSchemaServices()
        }
    }

    private fun chooseRunnerAndExe(settings: MagoProjectConfiguration): Pair<MagoRunner, String> {
        val interpreter = settings.resolveInterpreter(project)
        return if (interpreter != null && interpreter.isRemote) {
            RemoteInterpreterMagoRunner(interpreter) to settings.getEffectiveToolPath(project)
        } else {
            LocalMagoRunner() to settings.getEffectiveToolPath(project)
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
