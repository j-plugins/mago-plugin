package com.github.xepozz.mago.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationsUtil {
    fun inform(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Mago")
            .createNotification(
                title,
                content,
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}