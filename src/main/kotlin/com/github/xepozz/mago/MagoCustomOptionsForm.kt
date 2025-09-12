package com.github.xepozz.mago

import com.github.xepozz.mago.config.MagoConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.tools.quality.QualityToolCustomSettings

class MagoCustomOptionsForm(
    private val project: Project,
    private val configuration: MagoConfiguration,
) : QualityToolCustomSettings() {

    // TODO: maybe use MagoConfiguration?
    data class Model(
        var customParameters: String = "",
    )

    private lateinit var panel: DialogPanel
    private val model = Model()

    override fun createComponent() = panel {
        row("Other parameters:") {
            expandableTextField()
                .align(AlignX.FILL)
                .bindText(model::customParameters)
        }
    }.also { panel = it }

    override fun isModified() = panel.isModified()

    override fun apply() {
        panel.apply()

        configuration.customParameters = model.customParameters
    }

    override fun reset() {
        model.customParameters = configuration.customParameters

        panel.reset()
    }

    override fun getDisplayName() = null

    override fun validate() = Pair.create(false, "")
}
