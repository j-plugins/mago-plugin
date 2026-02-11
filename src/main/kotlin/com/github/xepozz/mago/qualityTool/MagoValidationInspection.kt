package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.MagoBundle
import com.jetbrains.php.tools.quality.QualityToolValidationInspection

@Suppress("InspectionDescriptionNotFoundInspection")
class MagoValidationInspection : QualityToolValidationInspection<MagoValidationInspection>() {
    override fun getAnnotator() = MagoAnnotatorProxy.INSTANCE

    override fun getToolName() = MagoBundle.message("quality.tool.mago")
}
