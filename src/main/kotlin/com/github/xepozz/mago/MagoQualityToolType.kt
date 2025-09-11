package com.github.xepozz.mago

import com.github.xepozz.mago.MagoConfigurationBaseManager.Companion.MAGO
import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.ObjectUtils.tryCast
import com.jetbrains.php.tools.quality.QualityToolType
import com.jetbrains.php.tools.quality.QualityToolValidationGlobalInspection

class MagoQualityToolType : QualityToolType<MagoConfiguration>() {
    override fun getDisplayName() = MAGO

    override fun getQualityToolBlackList(project: Project) = MagoBlackList.getInstance(project)

    override fun getConfigurationManager(project: Project) = MagoConfigurationManager.getInstance(project)

    override fun getInspection() = MagoValidationInspection()

    override fun getConfigurationProvider() = MagoConfigurationProvider.getInstances()

    override fun createConfigurableForm(project: Project, settings: MagoConfiguration) =
        MagoConfigurableForm(project, settings)

    override fun getToolConfigurable(project: Project): Configurable = MagoConfigurable(project)

    override fun getProjectConfiguration(project: Project) =
        MagoProjectConfiguration.getInstance(project)

    override fun createConfiguration() = MagoConfiguration()

    override fun getInspectionId() = "MagoGlobal"

    override fun getHelpTopic() = "reference.settings.php.mago"

    override fun getGlobalTool(project: Project, profile: InspectionProfile?): QualityToolValidationGlobalInspection? {
        val newProfile = profile ?: InspectionProjectProfileManager.getInstance(project).currentProfile

        val inspectionTool = newProfile.getInspectionTool(inspectionId, project) ?: return null

        return tryCast(inspectionTool.tool, MagoGlobalInspection::class.java)
    }

    override fun getInspectionShortName(project: Project) = getGlobalTool(project, null)?.shortName
        ?: inspection.shortName

    companion object {
        val INSTANCE = MagoQualityToolType()
    }
}
