package com.github.xepozz.mago

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.jetbrains.php.tools.quality.QualityToolProjectConfigurableForm

class MagoConfigurable(project: Project) : QualityToolProjectConfigurableForm(project), Configurable.NoScroll {
    override fun getHelpTopic() = "reference.settings.php.mago"

    override fun getId() = "settings.php.quality.tools.php.mago"

    override fun getQualityToolType() = MagoQualityToolType.INSTANCE
}
