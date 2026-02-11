package com.github.xepozz.mago.execution

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.jetbrains.php.config.interpreters.PhpInterpreter
import com.jetbrains.php.run.remote.PhpRemoteInterpreterManager
import java.io.File
import java.util.Base64
import javax.swing.JPanel

class RemoteInterpreterMagoRunner(private val interpreter: PhpInterpreter) : MagoRunner {
    override fun run(
        project: Project,
        exePath: String,
        args: List<String>,
        timeoutMs: Int,
        workDir: File?
    ): ProcessOutput {
        val (manager, mappedArgs) = mapPathsAndGetManager(project, args) ?: return errorOutput(
            PhpRemoteInterpreterManager.getRemoteInterpreterPluginIsDisabledErrorMessage()
        )
        val cmdLine = GeneralCommandLine(exePath).withParameters(mappedArgs)
        return manager.getProcessOutput(project, interpreter.phpSdkAdditionalData, cmdLine, "", JPanel())
    }

    override fun runWithStdin(
        project: Project,
        exePath: String,
        args: List<String>,
        stdinContent: String,
        timeoutMs: Int,
        workDir: File?
    ): ProcessOutput {
        val (manager, mappedArgs) = mapPathsAndGetManager(project, args) ?: return errorOutput(
            PhpRemoteInterpreterManager.getRemoteInterpreterPluginIsDisabledErrorMessage()
        )
        // Hack: the PHP Docker plugin never sends stdin and never closes it, so mago would hang.
        // Run sh -c 'printf "%s" "$..." | base64 -d | mago ...' with content in env.
        // Use a name mago won't treat as config (mago maps MAGO_* env vars to config keys).
        val stdinB64 = Base64.getEncoder().encodeToString(stdinContent.toByteArray(Charsets.UTF_8))
        val pipeline = buildShellPipeline(exePath, mappedArgs)
        val cmdLine = GeneralCommandLine("/bin/sh")
            .withParameters("-c", pipeline)
            .withEnvironment("PLUGIN_STDIN_B64", stdinB64)
        return manager.getProcessOutput(project, interpreter.phpSdkAdditionalData, cmdLine, "", JPanel())
    }

    /** Builds sh -c pipeline: printf '%s' "$PLUGIN_STDIN_B64" | base64 -d | <exePath> <args> */
    private fun buildShellPipeline(exePath: String, args: List<String>): String {
        val quotedArgs = args.joinToString(" ") { arg ->
            "'" + arg.replace("'", "'\"'\"'") + "'"
        }
        return $$"printf '%s' \"$PLUGIN_STDIN_B64\" | base64 -d | $$exePath $$quotedArgs"
    }

    /**
     * Maps all host paths in [args] to remote paths using the PHP plugin's path mapper.
     * Handles both bare paths and --key=path args (e.g. --workspace=, --config=) so that
     * remote interpreters (e.g.: Docker with a different mount like /opt/project) receive
     * the correct paths.
     */
    private fun mapPathsAndGetManager(project: Project, args: List<String>): Pair<PhpRemoteInterpreterManager, List<String>>? {
        val manager = PhpRemoteInterpreterManager.getInstance() ?: return null
        val pathProcessor = manager.createPathMapper(project, interpreter.phpSdkAdditionalData)
        val mappedArgs = args.map { arg ->
            when {
                arg.startsWith("--workspace=") -> {
                    val path = arg.removePrefix("--workspace=")
                    if (pathProcessor.canProcess(path)) "--workspace=${pathProcessor.process(path)}" else arg
                }
                arg.startsWith("--config=") -> {
                    val path = arg.removePrefix("--config=")
                    if (pathProcessor.canProcess(path)) "--config=${pathProcessor.process(path)}" else arg
                }
                pathProcessor.canProcess(arg) -> pathProcessor.process(arg)
                else -> arg
            }
        }
        return manager to mappedArgs
    }

    private fun errorOutput(message: String, exitCode: Int = -1): ProcessOutput {
        return ProcessOutput().also {
            it.appendStderr(message)
            if (exitCode >= 0) it.exitCode = exitCode
        }
    }
}
