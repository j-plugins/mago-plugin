package com.github.xepozz.mago

import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.jetbrains.php.tools.quality.QualityToolValidationGlobalInspection
import com.jetbrains.php.tools.quality.QualityToolXmlMessageProcessor.ProblemDescription

class MagoGlobalInspection : QualityToolValidationGlobalInspection(), ExternalAnnotatorBatchInspection {
    override fun getAnnotator() = MagoAnnotatorProxy.INSTANCE

    override fun getKey() = MAGO_ANNOTATOR_INFO

    override fun getSharedLocalInspectionTool() = MagoValidationInspection()

    companion object {
        private val MAGO_ANNOTATOR_INFO = Key.create<List<ProblemDescription>>("ANNOTATOR_INFO_MAGO")
    }
}
