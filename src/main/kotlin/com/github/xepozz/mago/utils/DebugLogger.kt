package com.github.xepozz.mago.utils

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.openapi.project.Project

object DebugLogger {
    fun inform(project: Project, title: String, content: String) {
        val settings = project.getService(MagoProjectConfiguration::class.java)
        if (settings.debug) {
            NotificationsUtil.inform(project, title, content)
        }
    }
}