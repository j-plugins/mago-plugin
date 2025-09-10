package com.github.xepozz.mago

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Pair
import com.intellij.ui.LanguageTextField
import com.intellij.ui.RelativeFont
import com.intellij.ui.dsl.builder.*
import com.jetbrains.php.tools.quality.QualityToolCustomSettings
import org.intellij.lang.regexp.RegExpLanguage
import java.awt.Font
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

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

    override fun createComponent(): JComponent {
        panel = panel {
            row("Other parameters:") {
                expandableTextField()
                    .align(AlignX.FILL)
                    .bindText(model::customParameters)
            }
        }

        return panel
    }

    override fun isModified(): Boolean {
        panel.reset()
        return model.customParameters != configuration.customParameters
    }

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
