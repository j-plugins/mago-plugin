package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.lang.inspections.PhpInspectionsUtil
import com.jetbrains.php.tools.quality.QualityToolConfigurationComboBox
import com.jetbrains.php.tools.quality.QualityToolsIgnoreFilesConfigurable
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComponent

class MagoConfigurable(val project: Project) : Configurable {
    val settings = project.getService(MagoProjectConfiguration::class.java)
    val inspectionProfileManager = InspectionProfileManager.getInstance(project)

    val qualityToolConfigurationComboBox = QualityToolConfigurationComboBox(project, getQualityToolType())
    var myPanel = panel {
        row {
            browserLink("Download Mago", "https://github.com/carthage-software/mago")
            browserLink("Report a plugin bug", "https://github.com/j-plugins/mago-plugin/issues")
            browserLink("Request a plugin feature", "https://github.com/j-plugins/mago-plugin/issues")
        }
        group(MagoBundle.message("settings.options.title")) {
            row {
                cell(qualityToolConfigurationComboBox)
                    .label("Mago executable")
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile())
                    .bindText(settings::configurationFile)
                    .label("Configuration file")
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
            row {
                cell(
                    ActionLink(
                        PhpBundle.message("guality.tool.configuration.show.ignored.files"),
                        ActionListener { e: ActionEvent? ->
                            ShowSettingsUtil.getInstance().editConfigurable(
                                project,
                                QualityToolsIgnoreFilesConfigurable(getQualityToolType(), project)
                            )
                        })
                )
            }
        }
        group(MagoBundle.message("settings.inspections.title")) {
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
                        { it.selectInspectionTool(getInspectionShortName()) },
                    )
                )
                cell(OnOffButton())
                    .bindSelected({
                        inspectionProfileManager.currentProfile
                            .isToolEnabled(HighlightDisplayKey.find(getInspectionShortName()))
                    }, {
                        inspectionProfileManager.currentProfile
                            .setToolEnabled(getInspectionShortName(), it)
                    })
            }.layout(RowLayout.PARENT_GRID)
            row {
                textField()
                    .label("Additional parameters")
                    .bindText(settings::analyzeAdditionalParameters)
                    .comment("Read more: mago analyze --help")
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }
        group(MagoBundle.message("settings.linter.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::linterEnabled)
            }
                .layout(RowLayout.PARENT_GRID)
                .visible(true)
                .enabled(false)
        }
        group(MagoBundle.message("settings.guard.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::guardEnabled)
            }
                .layout(RowLayout.PARENT_GRID)
                .visible(true)
                .enabled(false)
        }
        group(MagoBundle.message("settings.formatter.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::formatterEnabled)
            }.layout(RowLayout.PARENT_GRID)
            row {
                textField()
                    .label("Additional parameters")
                    .bindText(settings::formatAdditionalParameters)
                    .comment("Read more: mago fmt --help")
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
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

