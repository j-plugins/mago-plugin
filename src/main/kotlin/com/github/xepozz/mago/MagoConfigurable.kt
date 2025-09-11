package com.github.xepozz.mago

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel

//import com.jetbrains.php.tools.quality.QualityToolProjectConfigurableForm
class MagoConfigurable(project: Project) : Configurable, Configurable.NoScroll {
    override fun getDisplayName() = "Display Name"

    override fun getHelpTopic() = "reference.settings.php.mago"

//    override fun getId() = "settings.php.quality.tools.php.mago"

    //    override fun getQualityToolType() = MagoQualityToolType.INSTANCE
    override fun createComponent() = panel {
        row { label("Mago") }
    }

    override fun isModified() = false

    override fun apply() {
    }
}
