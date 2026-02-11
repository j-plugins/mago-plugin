package com.github.xepozz.mago.composer

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.configuration.MagoConfigurationManager
import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.nio.file.Files
import java.nio.file.Path

class MagoComposerAutoDetectActivity : ProjectActivity {
    companion object {
        private const val PACKAGE: String = "carthage-software/mago"

        private const val RELATIVE_PATH: String = "bin/mago"
        private const val RELATIVE_PATH_WINDOWS: String = "bin/mago.bat"
    }

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        maybeSuggestOrApply(project, basePath)

        project.messageBus.connect().subscribe(
            topic = VirtualFileManager.VFS_CHANGES,
            handler = object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val needle = "/vendor/$RELATIVE_PATH".replace('\\', '/')
                    val touched = events.any { event ->
                        val path = event.path.replace('\\', '/')
                        path.endsWith(needle)
                    }
                    if (!touched) return

                    maybeSuggestOrApply(project, basePath)
                }
            }
        )
    }

    private fun maybeSuggestOrApply(project: Project, basePath: String) {
        val settings = MagoProjectConfiguration.getInstance(project)

        if (settings.isRemoteInterpreter(project)) return

        val localConfig = MagoConfigurationManager.getInstance(project).getOrCreateLocalSettings()
        if (localConfig.toolPath.isNotBlank()) return

        val candidate = composerMagoPath(basePath) ?: return

        val group = NotificationGroupManager.getInstance().getNotificationGroup("Mago")
        @Suppress("DialogTitleCapitalization")
        group.createNotification(
            title = MagoBundle.message("composer.detected.title"),
            content = MagoBundle.message("composer.detected.content", PACKAGE, candidate),
            type = NotificationType.INFORMATION
        )
            .addAction(
                NotificationAction.createSimple(MagoBundle.message("composer.action.useMago")) {
                    localConfig.toolPath = candidate.toString()
                }
            )
            .addAction(
                NotificationAction.createSimple(MagoBundle.message("composer.action.openSettings")) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, MagoBundle.message("mago.title"))
                }
            )
            .notify(project)
    }

    private fun composerMagoPath(basePath: String): Path? {
        val rel = if (SystemInfo.isWindows) RELATIVE_PATH_WINDOWS else RELATIVE_PATH
        val path = Path.of(basePath, "vendor", rel)
        return if (Files.isRegularFile(path)) path else null
    }
}
