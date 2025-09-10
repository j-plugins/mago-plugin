package com.github.xepozz.mago

import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.php.tools.quality.QualityToolAnnotatorInfo
import com.jetbrains.php.tools.quality.QualityToolConfiguration

class MagoQualityToolAnnotatorInfo(
    psiFile: PsiFile?,
    inspection: MagoValidationInspection,
    profile: InspectionProfile,
    project: Project,
    configuration: QualityToolConfiguration,
    isOnTheFly: Boolean,
) : QualityToolAnnotatorInfo<MagoValidationInspection>(
    psiFile,
    inspection,
    profile,
    project,
    configuration,
    isOnTheFly
)
