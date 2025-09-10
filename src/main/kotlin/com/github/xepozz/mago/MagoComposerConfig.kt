package com.github.xepozz.mago

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.php.tools.quality.QualityToolsComposerConfig

class MagoComposerConfig : QualityToolsComposerConfig<MagoConfiguration, MagoValidationInspection>(
    PACKAGE,
    RELATIVE_PATH
) {
    override fun getQualityInspectionShortName() = MagoQualityToolType.INSTANCE.inspectionId

    override fun getConfigurationManager(project: Project) = MagoConfigurationManager.getInstance(project)

    override fun getSettings() = MagoOpenSettingsProvider.INSTANCE

    companion object {
        private const val PACKAGE: String = "carthage-software/mago"
        private val RELATIVE_PATH: String = "bin/mago${if (SystemInfo.isWindows) ".exe" else ""}"
    }
}
