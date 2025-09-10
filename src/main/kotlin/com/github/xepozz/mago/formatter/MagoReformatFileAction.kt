package com.github.xepozz.mago.formatter

import com.github.xepozz.mago.MagoValidationInspection
import com.github.xepozz.mago.MagoBundle
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolReformatFileAction

class MagoReformatFileAction : QualityToolReformatFileAction<MagoValidationInspection>(MagoReformatFile()) {
    override fun getFamilyName() = MagoBundle.message("quality.tool.mago")

    override fun getText() = MagoBundle.message("quality.tool.mago.quick.fix.text")

    override fun getInspection(project: Project, file: PsiFile): MagoValidationInspection? {
        return InspectionProjectProfileManager.getInstance(project)
            .currentProfile.getUnwrappedTool(MagoValidationInspection().shortName, file) as MagoValidationInspection?
    }
}
