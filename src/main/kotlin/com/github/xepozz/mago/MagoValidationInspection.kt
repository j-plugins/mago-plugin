package com.github.xepozz.mago

import com.github.xepozz.mago.config.MagoConfigurationBaseManager.Companion.MAGO
import com.jetbrains.php.tools.quality.QualityToolValidationInspection

class MagoValidationInspection : QualityToolValidationInspection<MagoValidationInspection>() {
    override fun getAnnotator() = MagoAnnotatorProxy.INSTANCE

    override fun getToolName() = MAGO
}
