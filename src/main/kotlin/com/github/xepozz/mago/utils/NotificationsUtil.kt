package com.github.xepozz.mago.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationsUtil {
    fun inform(project: Project, title: String, content: String) {
        sendNotification(project, title, content, NotificationType.INFORMATION)
    }

    fun error(project: Project, title: String, content: String) {
        sendNotification(project, title, content, NotificationType.ERROR)
    }

    private fun sendNotification(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Mago")
            .createNotification(
                title,
                content,
                type
            )
            .notify(project)
    }
}
