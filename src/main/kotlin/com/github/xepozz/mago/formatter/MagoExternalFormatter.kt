package com.github.xepozz.mago.formatter

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.analysis.MagoCliOptions
import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.execution.LocalMagoRunner
import com.github.xepozz.mago.execution.MagoRunner
import com.github.xepozz.mago.execution.RemoteInterpreterMagoRunner
import com.github.xepozz.mago.utils.DebugLogger
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.openapi.project.Project
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.PhpFileType
import java.io.File

class MagoExternalFormatter : AsyncDocumentFormattingService() {
    override fun getFeatures(): Set<FormattingService.Feature> = emptySet()

    override fun canFormat(file: PsiFile): Boolean {
        val project = file.project
        if (project.isDisposed) return false

        val settings = MagoProjectConfiguration.getInstance(project)
        if (!settings.formatterEnabled) return false

        val exe = settings.getEffectiveToolPath(project)
        if (exe.isBlank()) return false

        return file.fileType == PhpFileType.INSTANCE
    }

    override fun getNotificationGroupId(): String = "Mago"

    override fun getName(): String = MagoBundle.message("formatter.name")

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val context = request.context
        val project = context.project
        val virtualFile = context.virtualFile ?: return null
        val settings = MagoProjectConfiguration.getInstance(project)

        val (runner, exe) = chooseRunnerAndExe(project, settings)
        if (exe.isBlank()) return null

        return object : FormattingTask {
            @Volatile
            private var cancelled = false

            override fun run() {
                if (cancelled) return

                val parentDir = virtualFile.parent?.path
                if (parentDir == null) {
                    request.onError(MagoBundle.message("formatter.name"), MagoBundle.message("formatter.error.noParentDir"))
                    return
                }

                val tempFile = File.createTempFile(
                    ".mago-fmt-",
                    ".php",
                    File(parentDir)
                )
                tempFile.deleteOnExit()
                try {
                    tempFile.writeText(request.documentText, Charsets.UTF_8)

                    val tempPath = FileUtil.toSystemIndependentName(tempFile.absolutePath)
                    val args = MagoCliOptions.getFormatOptions(settings, project, listOf(tempPath))

                    DebugLogger.inform(
                        project,
                        title = MagoBundle.message("formatter.title"),
                        content = "File: ${virtualFile.path}<br>" +
                                "Executable: $exe<br><br>" +
                                "Format options: ${args.joinToString(" ")}"
                    )

                    val output = runner.run(project, exe, args)

                    if (cancelled) return

                    if (output.exitCode != 0) {
                        val stderr = output.stderr.trim()
                        request.onError(
                            MagoBundle.message("formatter.error.title"),
                            stderr.ifBlank { MagoBundle.message("formatter.error.exitCode", output.exitCode) }
                        )
                        return
                    }

                    val formattedText = tempFile.readText(Charsets.UTF_8)
                    request.onTextReady(formattedText)
                } catch (e: Exception) {
                    request.onError(MagoBundle.message("formatter.error.generic"), e.message ?: MagoBundle.message("formatter.error.unknown"))
                } finally {
                    tempFile.delete()
                }
            }

            override fun cancel(): Boolean {
                cancelled = true
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }

    private fun chooseRunnerAndExe(project: Project, settings: MagoProjectConfiguration): Pair<MagoRunner, String> {
        val interpreter = settings.resolveInterpreter(project)
        return if (interpreter != null && interpreter.isRemote) {
            RemoteInterpreterMagoRunner(interpreter) to settings.getEffectiveToolPath(project)
        } else {
            LocalMagoRunner() to settings.getEffectiveToolPath(project)
        }
    }
}
