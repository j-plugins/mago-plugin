package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.lang.inspections.PhpInspectionsUtil
import com.jetbrains.php.tools.quality.QualityToolConfigurationComboBox
import javax.swing.JComponent

class MagoConfigurable(val project: Project) : Configurable {
    val settings = project.getService(MagoProjectConfiguration::class.java)
    val inspectionProfileManager = InspectionProfileManager.getInstance(project)

    val qualityToolConfigurationComboBox = QualityToolConfigurationComboBox(project, getQualityToolType())
    var myPanel = panel {
        group("Features") {
            row {
                cell(
                    PhpInspectionsUtil.createPanelWithSettingsLink(
                        MagoBundle.message("quality.tool.settings.link.inspection", "Mago"),
                        ErrorsConfigurable::class.java,
                        {
                            ConfigurableExtensionPointUtil.createProjectConfigurableForProvider(
                                project,
                                ErrorsConfigurableProvider::class.java
                            ) as ErrorsConfigurable?
                        },
                        { it.selectInspectionTool(getInspectionShortName()) })
                )
                cell(OnOffButton())
                    .bindSelected({
                        inspectionProfileManager.currentProfile
                            .isToolEnabled(HighlightDisplayKey.find(getInspectionShortName()))
                    }, {
                        inspectionProfileManager.currentProfile
                            .setToolEnabled(getInspectionShortName(), it)
                    })
            }
            row {
                cell(OnOffButton())
                    .label("Linter")
                    .bindSelected(settings::linterEnabled)
            }.visible(true).enabled(false)

            row {
                cell(OnOffButton())
                    .label("Formatter")
                    .bindSelected(settings::formatterEnabled)
            }
        }
        group("Options") {
            row {
                cell(qualityToolConfigurationComboBox)
                    .label("Mago executable")
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                label("Additional parameters")
                textField()
                    .bindText(settings::additionalParameters)
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)

            row {
                browserLink("Download Mago", "https://github.com/carthage-software/mago")
            }
        }
    }

    override fun createComponent(): JComponent = myPanel

    override fun isModified(): Boolean {
        return myPanel.isModified()
                || qualityToolConfigurationComboBox.selectedItemId != getSavedSelectedConfigurationId()
    }

    override fun apply() {
        updateSelectedConfiguration(qualityToolConfigurationComboBox.selectedItemId)
        myPanel.apply()
    }

    private fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun reset() {
        qualityToolConfigurationComboBox.reset(project, getSavedSelectedConfigurationId())
    }

    private fun getInspectionShortName() = getQualityToolType().getInspectionShortName(project)

    override fun getDisplayName() = getQualityToolType().getDisplayName()

    private fun updateSelectedConfiguration(newConfigurationId: String?) {
        val projectConfiguration = getQualityToolType().getProjectConfiguration(project)
        if (newConfigurationId != projectConfiguration.selectedConfigurationId) {
            projectConfiguration.selectedConfigurationId = newConfigurationId
        }
    }

    private fun getSavedSelectedConfigurationId(): String? {
        return getQualityToolType().getProjectConfiguration(project).selectedConfigurationId
    }

}

