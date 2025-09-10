package com.github.xepozz.mago

import com.jetbrains.php.tools.quality.QualityToolAnnotator
import com.jetbrains.php.tools.quality.QualityToolValidationInspection
import com.github.xepozz.mago.MagoConfigurationBaseManager.Companion.MAGO

class MagoValidationInspection : QualityToolValidationInspection<MagoValidationInspection>() {
    override fun getAnnotator() = MagoAnnotatorProxy.INSTANCE

    override fun getToolName() = MAGO
}
