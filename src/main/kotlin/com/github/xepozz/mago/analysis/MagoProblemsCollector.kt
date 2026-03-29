package com.github.xepozz.mago.analysis

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.execution.LocalMagoRunner
import com.github.xepozz.mago.execution.MagoRunner
import com.github.xepozz.mago.execution.RemoteInterpreterMagoRunner
import com.github.xepozz.mago.model.MagoProblemDescription
import com.github.xepozz.mago.utils.DebugLogger
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import java.io.File

class MagoProblemsCollector(
    private val parser: MagoJsonMessageHandler = MagoJsonMessageHandler(),
) {
    data class Result(
        val problems: List<MagoProblemDescription>,
        val errorOutput: String = "",
        /** When non-null, problem paths refer to this temp file; the caller should rewrite to the real file name. */
        val tempFileNameUsed: String? = null
    )

    /**
     * Collect problems for a file. When [stdinContent] is non-null, tries --stdin-input first so
     * baselines use the real path; if Mago doesn't support it, falls back to a temp file and logs
     * that the user should update Mago. [tempFileParentDir] is required when [stdinContent] is set
     * (used for a fallback temp file).
     */
    fun collectForFile(
        project: Project,
        filePath: String,
        originalPath: String? = null,
        stdinContent: String? = null,
        tempFileParentDir: String? = null
    ): Result {
        val settings = MagoProjectConfiguration.getInstance(project)
        if (!settings.enabled) return Result(emptyList(), "")

        val (runner, exe) = chooseRunnerAndExe(project, settings)
        if (exe.isBlank()) return Result(emptyList(), MagoBundle.message("problemsCollector.exeNotConfigured"))

        val displayPath = originalPath ?: filePath
        val resolved = MagoCliOptions.resolveForFile(project, settings, filePath)

        if (resolved.configFile.isEmpty()) {
            if (settings.debug) {
                DebugLogger.inform(
                    project,
                    title = MagoBundle.message("problemsCollector.skipped.title"),
                    content = MagoBundle.message("problemsCollector.skipped.content", displayPath)
                )
            }
            return Result(emptyList(), "")
        }

        val workDir = File(resolved.workspaceDir.path)
        return if (stdinContent != null && tempFileParentDir != null) {
            tryStdinThenFallback(
                project,
                settings,
                runner,
                exe,
                displayPath,
                configDisplay = resolved.configFile,
                stdinContent,
                tempFileParentDir,
                workDir
            )
        } else {
            runWithFile(
                project,
                settings,
                runner,
                exe,
                filePath,
                displayPath,
                configDisplay = resolved.configFile,
                workDir
            )
        }
    }

    private fun tryStdinThenFallback(
        project: Project,
        settings: MagoProjectConfiguration,
        runner: MagoRunner,
        exe: String,
        displayPath: String,
        configDisplay: String,
        stdinContent: String,
        tempFileParentDir: String,
        workDir: File
    ): Result {
        val analyzeArgs = MagoCliOptions.getAnalyzeOptionsStdin(settings, project, displayPath)
        val analyzeOut = runner.runWithStdin(
            project,
            exePath = exe,
            analyzeArgs,
            stdinContent,
            timeoutMs = 30_000,
            workDir
        )
        if (isStdinUnsupported(analyzeOut)) {
            if (settings.debug) {
                DebugLogger.inform(
                    project,
                    title = MagoBundle.message("problemsCollector.stdinFallback.title"),
                    content = MagoBundle.message("problemsCollector.stdinFallback.content", displayPath)
                )
            }
            val tempFile = File.createTempFile(
                ".mago-",
                ".php",
                File(tempFileParentDir)
            )
            tempFile.deleteOnExit()
            try {
                tempFile.writeText(stdinContent, Charsets.UTF_8)
                val runResult = runWithFile(
                    project,
                    settings,
                    runner,
                    exe,
                    tempFile.absolutePath,
                    displayPath,
                    configDisplay,
                    workDir
                )
                return runResult.copy(tempFileNameUsed = tempFile.name)
            } finally {
                tempFile.delete()
            }
        }
        val all = mutableListOf<MagoProblemDescription>()
        val errors = StringBuilder()
        val debugLines = mutableListOf<String>()
        val analyze = parseOutput(analyzeOut, "analysis")
        all += analyze.problems
        if (analyze.errorOutput.isNotBlank()) errors.appendLine(analyze.errorOutput)
        debugLines += "Analyze options (stdin): ${analyzeArgs.joinToString(" ")}"
        if (settings.linterEnabled) {
            val lintArgs = MagoCliOptions.getLintOptionsStdin(settings, project, displayPath)
            val lintOut = runner.runWithStdin(
                project,
                exePath = exe,
                lintArgs,
                stdinContent,
                timeoutMs = 30_000,
                workDir = workDir
            )
            val lint = parseOutput(lintOut, "lint")
            all += lint.problems
            if (lint.errorOutput.isNotBlank()) errors.appendLine(lint.errorOutput)
            debugLines += "Lint options (stdin): ${lintArgs.joinToString(" ")}"
        }
        if (settings.guardEnabled) {
            val guardArgs = MagoCliOptions.getGuardOptionsStdin(settings, project, displayPath)
            val guardOut = runner.runWithStdin(
                project,
                exePath = exe,
                guardArgs,
                stdinContent,
                timeoutMs = 30_000,
                workDir
            )
            val guard = parseOutput(guardOut, "guard")
            all += guard.problems
            if (guard.errorOutput.isNotBlank()) errors.appendLine(guard.errorOutput)
            debugLines += "Guard options (stdin): ${guardArgs.joinToString(" ")}"
        }
        logDebugResult(project, all, displayPath, configDisplay, exe, debugLines)
        return Result(problems = all, errorOutput = errors.toString(), tempFileNameUsed = null)
    }

    private fun isStdinUnsupported(out: ProcessOutput): Boolean {
        if (out.exitCode != 2) return false
        val stderr = out.stderr
        return stderr.contains("stdin-input") || stderr.contains("unexpected argument")
    }

    private fun runWithFile(
        project: Project,
        settings: MagoProjectConfiguration,
        runner: MagoRunner,
        exe: String,
        filePath: String,
        displayPath: String,
        configDisplay: String,
        workDir: File
    ): Result {
        val all = mutableListOf<MagoProblemDescription>()
        val errors = StringBuilder()
        val debugLines = mutableListOf<String>()
        val tempName = File(filePath).name
        val originalName = File(displayPath).name

        val analyzeArgs = MagoCliOptions.getAnalyzeOptions(settings, project, filePath)
        val analyze = runAndParse(project, runner, exe, analyzeArgs, "analysis", workDir)
        all += analyze.problems
        if (analyze.errorOutput.isNotBlank()) errors.appendLine(analyze.errorOutput)
        debugLines += "Analyze options: ${
            analyzeArgs.joinToString(" ") {
                it.replace(
                    oldValue = tempName,
                    newValue = originalName
                )
            }
        }"

        if (settings.linterEnabled) {
            val lintArgs = MagoCliOptions.getLintOptions(settings, project, filePath)
            val lint = runAndParse(project, runner, exe, lintArgs, "lint", workDir)
            all += lint.problems
            if (lint.errorOutput.isNotBlank()) errors.appendLine(lint.errorOutput)
            debugLines += "Lint options: ${
                lintArgs.joinToString(" ") {
                    it.replace(
                        oldValue = tempName,
                        newValue = originalName
                    )
                }
            }"
        }
        if (settings.guardEnabled) {
            val guardArgs = MagoCliOptions.getGuardOptions(settings, project, filePath)
            val guard = runAndParse(project, runner, exe, guardArgs, "guard", workDir)
            all += guard.problems
            if (guard.errorOutput.isNotBlank()) errors.appendLine(guard.errorOutput)
            debugLines += "Guard options: ${
                guardArgs.joinToString(" ") {
                    it.replace(
                        oldValue = tempName,
                        newValue = originalName
                    )
                }
            }"
        }

        logDebugResult(project, all, displayPath, configDisplay, exe, debugLines)
        return Result(problems = all, errorOutput = errors.toString(), tempFileNameUsed = null)
    }

    private fun logDebugResult(
        project: Project,
        problems: List<MagoProblemDescription>,
        displayPath: String,
        configDisplay: String,
        exe: String,
        debugLines: List<String>
    ) {
        DebugLogger.inform(
            project,
            title = MagoBundle.message("problemsCollector.problems.title", problems.size),
            content = "File: $displayPath<br>" +
                    "Config: $configDisplay<br>" +
                    "Executable: $exe<br><br>${debugLines.joinToString("<br><br>")}"
        )
    }

    /** Parse runner output: JSON to problems, exit code 0/1 = success, >= 2 = failure. */
    private fun parseOutput(out: ProcessOutput, category: String): Result {
        val stdout = out.stdout.trim()
        val stderr = out.stderr.trim()
        val problems = if (stdout.isEmpty()) emptyList()
        else try {
            parser.parseJson(stdout, category)
        } catch (_: Exception) {
            emptyList()
        }
        val errText = buildString {
            if (out.exitCode >= 2) {
                append("Exit code ${out.exitCode}. ")
                if (stderr.isNotBlank()) append(stderr)
            }
        }.trim()
        return Result(problems, errText)
    }

    private fun chooseRunnerAndExe(project: Project, settings: MagoProjectConfiguration): Pair<MagoRunner, String> {
        val interpreter = settings.resolveInterpreter(project)
        return if (interpreter != null && interpreter.isRemote) {
            RemoteInterpreterMagoRunner(interpreter) to settings.getEffectiveToolPath(project)
        } else {
            LocalMagoRunner() to settings.getEffectiveToolPath(project)
        }
    }

    private fun runAndParse(
        project: Project,
        runner: MagoRunner,
        exePath: String,
        args: List<String>,
        category: String,
        workDir: File? = null
    ): Result {
        return try {
            val out = runner.run(project, exePath, args, timeoutMs = 30_000, workDir)
            parseOutput(out, category)
        } catch (t: Throwable) {
            Result(emptyList(), "${t::class.java.simpleName}: ${t.message ?: MagoBundle.message("problemsCollector.error.unknown")}")
        }
    }
}
