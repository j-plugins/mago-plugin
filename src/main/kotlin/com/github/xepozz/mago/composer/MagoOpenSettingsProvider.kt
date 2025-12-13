package com.github.xepozz.mago.composer

import com.github.xepozz.mago.MagoBundle
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.jetbrains.php.composer.actions.log.ComposerLogMessageBuilder

class MagoOpenSettingsProvider : ComposerLogMessageBuilder.Settings("\u300C") {
    override fun show(project: Project) {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, MagoBundle.message("configurable.quality.tool.php.mago"))
    }

    companion object {
        val INSTANCE = MagoOpenSettingsProvider()
    }
}