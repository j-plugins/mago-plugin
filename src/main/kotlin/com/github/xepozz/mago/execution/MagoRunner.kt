package com.github.xepozz.mago.execution

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import java.io.File

interface MagoRunner {
    /**
     * @param workDir When set, the process runs with this as the current working directory.
     *                This ensures relative paths in config (e.g.: baseline file) resolve correctly.
     */
    fun run(
        project: Project,
        exePath: String,
        args: List<String>,
        timeoutMs: Int = 30_000,
        workDir: File? = null
    ): ProcessOutput

    /**
     * Run mago with [stdinContent] as standard input (e.g.: for --stdin-input).
     * Used for editor integration so baselines use the real file path.
     * If the runner cannot support stdin (e.g.: older Mago version), it may return a failed ProcessOutput
     * so the caller can fall back to the temp-file method.
     *
     * @param workDir When set, the process runs with this as the current working directory.
     *                This ensures relative paths in config (e.g.: baseline file) resolve correctly.
     */
    fun runWithStdin(
        project: Project,
        exePath: String,
        args: List<String>,
        stdinContent: String,
        timeoutMs: Int = 30_000,
        workDir: File? = null
    ): ProcessOutput = run(project, exePath, args, timeoutMs, workDir)
}
