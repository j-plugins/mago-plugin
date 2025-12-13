package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.configuration.MagoConfigurationBaseManager.Companion.MAGO
import com.jetbrains.php.tools.quality.QualityToolValidationInspection

class MagoValidationInspection : QualityToolValidationInspection<MagoValidationInspection>() {
    override fun getAnnotator() = MagoAnnotatorProxy.INSTANCE

    override fun getToolName() = MAGO
}
