package com.github.xepozz.mago.composer

import com.github.xepozz.mago.MagoConfiguration
import com.github.xepozz.mago.MagoConfigurationManager
import com.github.xepozz.mago.MagoOpenSettingsProvider
import com.github.xepozz.mago.MagoQualityToolType
import com.github.xepozz.mago.MagoValidationInspection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.php.tools.quality.QualityToolsComposerConfig

class MagoComposerConfig : QualityToolsComposerConfig<MagoConfiguration, MagoValidationInspection>(
    PACKAGE,
    RELATIVE_PATH
) {
    override fun getQualityInspectionShortName() = MagoQualityToolType.Companion.INSTANCE.inspectionId

    override fun getConfigurationManager(project: Project) = MagoConfigurationManager.Companion.getInstance(project)

    override fun getSettings() = MagoOpenSettingsProvider.Companion.INSTANCE

    companion object {
        private const val PACKAGE: String = "carthage-software/mago"
        private val RELATIVE_PATH: String = "bin/mago${if (SystemInfo.isWindows) ".exe" else ""}"
    }
}
