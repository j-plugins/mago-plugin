package com.github.xepozz.mago.qualityTool

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.configuration.MagoConfigurationBaseManager
import com.github.xepozz.mago.formatter.MagoReformatFile
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolReformatFileAction

class MagoReformatFileAction(val project: Project) :
    QualityToolReformatFileAction<MagoValidationInspection>(MagoReformatFile(project)) {
    override fun getFamilyName() = MagoConfigurationBaseManager.MAGO
    override fun getText() = MagoBundle.message("quality.tool.mago.quick.fix.text")

    override fun getInspection(
        project: Project,
        file: PsiFile,
    ) = InspectionProjectProfileManager.getInstance(project)
        .currentProfile
        .getUnwrappedTool(MagoValidationInspection().shortName, file) as? MagoValidationInspection
}
