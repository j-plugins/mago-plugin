package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.php.tools.quality.QualityToolAddToIgnoredAction
import com.jetbrains.php.tools.quality.QualityToolType

class MagoAddToIgnoredAction : QualityToolAddToIgnoredAction() {
    override fun getQualityToolType(project: Project?): QualityToolType<MagoConfiguration> {
        return MagoQualityToolType.INSTANCE
    }
}
