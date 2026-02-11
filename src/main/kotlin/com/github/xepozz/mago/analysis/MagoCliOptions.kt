package com.github.xepozz.mago.analysis

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.github.xepozz.mago.findVirtualFile
import com.github.xepozz.mago.normalizePath
import com.github.xepozz.mago.toPathForExecution
import com.intellij.execution.configurations.ParametersList
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

object MagoCliOptions {
    data class ResolvedWorkspace(val workspaceDir: VirtualFile, val configFile: String)

    fun getAnalyzeOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) =
        buildOptionsForSingleFile(settings, project, filePath, "analyze", settings.analyzeAdditionalParameters)

    fun getLintOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) =
        buildOptionsForSingleFile(settings, project, filePath, "lint", settings.lintAdditionalParameters)

    fun getGuardOptions(settings: MagoProjectConfiguration, project: Project, filePath: String) =
        buildOptionsForSingleFile(settings, project, filePath, "guard", settings.guardAdditionalParameters)

    fun getAnalyzeOptionsStdin(settings: MagoProjectConfiguration, project: Project, originalFilePath: String) =
        buildOptionsForStdin(settings, project, originalFilePath, "analyze", settings.analyzeAdditionalParameters)

    fun getLintOptionsStdin(settings: MagoProjectConfiguration, project: Project, originalFilePath: String) =
        buildOptionsForStdin(settings, project, originalFilePath, "lint", settings.lintAdditionalParameters)

    fun getGuardOptionsStdin(settings: MagoProjectConfiguration, project: Project, originalFilePath: String) =
        buildOptionsForStdin(settings, project, originalFilePath, "guard", settings.guardAdditionalParameters)

    fun getFormatOptions(settings: MagoProjectConfiguration, project: Project, files: Collection<String>) = buildList {
        val resolved = resolveForFile(project, settings, files.firstOrNull() ?: "")
        addWorkspace(resolved.workspaceDir)
        addConfig(resolved.configFile)

        add("fmt")
        addAll(files.map { toWorkspaceRelativePath(resolved.workspaceDir, it) })
    }.plus(ParametersList.parse(settings.formatAdditionalParameters))

    private fun buildOptionsForSingleFile(
        settings: MagoProjectConfiguration,
        project: Project,
        filePath: String,
        command: String,
        additionalParameters: String,
    ) = buildList {
        val resolved = resolveForFile(project, settings, filePath)
        addWorkspace(resolved.workspaceDir)
        addConfig(resolved.configFile)

        add(command)
        add(toWorkspaceRelativePath(resolved.workspaceDir, filePath))
        add("--reporting-format=json")
        addAll(ParametersList.parse(additionalParameters))
    }

    private fun buildOptionsForStdin(
        settings: MagoProjectConfiguration,
        project: Project,
        originalFilePath: String,
        command: String,
        additionalParameters: String,
    ) = buildList {
        val resolved = resolveForFile(project, settings, originalFilePath)
        addWorkspace(resolved.workspaceDir)
        addConfig(resolved.configFile)

        add(command)
        add(toWorkspaceRelativePath(resolved.workspaceDir, originalFilePath))
        add("--stdin-input")
        add("--reporting-format=json")
        addAll(ParametersList.parse(additionalParameters))
    }

    /**
     * Resolves the correct workspace directory and config file for [filePath].
     *
     * 1. Check explicit workspace mappings (longest prefix match wins).
     * 2. Fall back to auto-detected workspace + the global [MagoProjectConfiguration.configurationFile].
     */
    fun resolveForFile(project: Project, settings: MagoProjectConfiguration, filePath: String): ResolvedWorkspace {
        val normalizedPath = filePath.normalizePath()

        val mapping = settings.workspaceMappings
            .filter { it.workspace.isNotBlank() }
            .filter {
                val wsPath = it.workspace.normalizePath().removeSuffix("/")
                normalizedPath.startsWith("$wsPath/") || normalizedPath == wsPath
            }
            .maxByOrNull { it.workspace.length }

        if (mapping != null) {
            val wsFile = mapping.workspace.findVirtualFile()
            if (wsFile != null) {
                return ResolvedWorkspace(wsFile, mapping.configFile)
            }
        }

        val workspace = findWorkspace(project, filePath)
        val defaultConfig = settings.configurationFile
        val configToUse = when {
            defaultConfig.isBlank() -> ""
            else -> {
                val configParent = defaultConfig.normalizePath().trimEnd('/').let { p ->
                    val lastSlash = p.lastIndexOf('/')
                    if (lastSlash <= 0) p else p.substring(0, lastSlash)
                }
                if (configParent.isNotEmpty() &&
                    (normalizedPath == configParent || normalizedPath.startsWith("$configParent/"))
                )
                    defaultConfig
                else
                    ""
                // File is not under the default config's project; leave config empty so Mago
                // discovers mago.toml from the workspace root (e.g.: core's own config).
            }
        }
        return ResolvedWorkspace(workspace, configToUse)
    }

    fun findWorkspace(project: Project, filePath: String?): VirtualFile {
        if (filePath != null) {
            val normalizedFilePath = filePath.normalizePath()

            val file = filePath.findVirtualFile()
            if (file != null) {
                val closestRoot = ReadAction.compute<VirtualFile?, RuntimeException> {
                    val module = ModuleUtilCore.findModuleForFile(file, project)
                    val candidateRoots = mutableListOf<VirtualFile>()

                    if (module != null) {
                        candidateRoots.addAll(module.rootManager.contentRoots)
                    }

                    ProjectFileIndex.getInstance(project).getContentRootForFile(file)
                        ?.let { candidateRoots.add(it) }

                    candidateRoots
                        .filter {
                            normalizedFilePath.startsWith(
                                prefix = it.path.normalizePath().removeSuffix("/") + "/"
                            )
                        }
                        .maxByOrNull { it.path.length }
                }
                if (closestRoot != null) return closestRoot
            }

            // Scan ALL modules' content roots — handles temp files and multi-attached
            // projects where the file index doesn't know about the file.
            val allModulesMatch = ReadAction.compute<VirtualFile?, RuntimeException> {
                ModuleManager.getInstance(project).modules
                    .flatMap { it.rootManager.contentRoots.toList() }
                    .filter {
                        val rootPath = it.path.normalizePath().removeSuffix("/")
                        normalizedFilePath.startsWith("$rootPath/")
                    }
                    .maxByOrNull { it.path.length }
            }
            if (allModulesMatch != null) return allModulesMatch
        }

        project.guessProjectDir()?.let { return it }

        val basePath = project.basePath
        if (!basePath.isNullOrBlank()) {
            basePath.findVirtualFile()?.let { return it }
        }

        return "/".findVirtualFile()!!
    }

    private fun toWorkspaceRelativePath(workspace: VirtualFile, absoluteFilePath: String): String =
        toRelativePath(workspace.path, absoluteFilePath)

    internal fun toRelativePath(basePath: String, absoluteFilePath: String): String {
        val bp = basePath.normalizePath()
        val afp = absoluteFilePath.normalizePath()
        val relative = FileUtil.getRelativePath(bp, afp, '/')
        return ensureMagoPath(relative ?: afp)
    }

    internal fun ensureMagoPath(path: String): String = when {
        path.isEmpty() -> path
        FileUtil.isAbsolute(path) || isWindowsAbsolute(path) || path.startsWith('\\') -> path
        path.startsWith("./") || path.startsWith(".\\") -> path
        else -> "./$path"
    }

    private fun isWindowsAbsolute(path: String): Boolean =
        path.length >= 2 && path[0].isLetter() && path[1] == ':'

    private fun MutableList<String>.addWorkspace(workspace: VirtualFile) {
        val projectPath = workspace.path.toPathForExecution()
        add("--workspace=$projectPath")
    }

    private fun MutableList<String>.addConfig(configFile: String) {
        if (configFile.isNotEmpty()) {
            add("--config=${configFile.toPathForExecution()}")
        }
    }
}
