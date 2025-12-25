package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.tools.quality.QualityToolCustomSettings

// a bit useless, but still
class MagoCustomOptionsForm(
    private val project: Project,
    private val configuration: MagoConfiguration,
) : QualityToolCustomSettings() {
    private var myPanel = panel {
        row("Other parameters:") {
            expandableTextField()
                .enabled(false)
                .align(AlignX.FILL)
                .bindText(configuration::customParameters)
        }
    }

    override fun createComponent() = myPanel

    override fun isModified() = myPanel.isModified()

    override fun apply() {
        myPanel.apply()
    }

    override fun reset() {
        myPanel.reset()
    }

    override fun getDisplayName() = null

    override fun validate() = Pair.create(true, "")
}
