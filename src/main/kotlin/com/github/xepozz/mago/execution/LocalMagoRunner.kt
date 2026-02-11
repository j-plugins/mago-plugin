package com.github.xepozz.mago.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class LocalMagoRunner : MagoRunner {
    override fun run(
        project: Project,
        exePath: String,
        args: List<String>,
        timeoutMs: Int,
        workDir: File?
    ): ProcessOutput {
        val cmd = GeneralCommandLine(exePath).withParameters(args)
        workDir?.let { cmd.withWorkDirectory(it) }
        return CapturingProcessHandler(cmd).runProcess(timeoutMs)
    }

    override fun runWithStdin(
        project: Project,
        exePath: String,
        args: List<String>,
        stdinContent: String,
        timeoutMs: Int,
        workDir: File?
    ): ProcessOutput {
        val command = listOf(exePath) + args
        val pb = ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.PIPE)
        workDir?.let { pb.directory(it) }
        val process = pb.start()
        val stdinBytes = stdinContent.toByteArray(Charsets.UTF_8)
        Thread {
            try {
                process.outputStream.use { it.write(stdinBytes) }
            } catch (_: Exception) {
            }
        }.apply { start(); join(timeoutMs.coerceAtLeast(1000).toLong()) }
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        Thread { process.inputStream.transferTo(stdout) }.start()
        Thread { process.errorStream.transferTo(stderr) }.start()
        val finished = process.waitFor(timeoutMs.coerceAtLeast(1000).toLong(), TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        return ProcessOutput().apply {
            appendStdout(stdout.toString(Charsets.UTF_8.name()))
            appendStderr(stderr.toString(Charsets.UTF_8.name()))
            exitCode = if (finished) process.exitValue() else -1
        }
    }
}
