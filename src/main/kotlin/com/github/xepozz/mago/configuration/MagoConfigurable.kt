package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.fileChooser.FileChooserDescriptor
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

class MagoConfigurable(val project: Project) : Configurable {
    val settings = project.getService(MagoProjectConfiguration::class.java)
    val inspectionProfileManager = InspectionProfileManager.getInstance(project)

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
                label("Path to Mago executable")
                textFieldWithBrowseButton(FileChooserDescriptor(true, false, false, false, false, false))
                    .bindText(settings::magoExecutable)
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

    override fun getDisplayName() = "Mago"

    override fun getHelpTopic() = "reference.settings.php.mago"

    fun getQualityToolType() = MagoQualityToolType.INSTANCE

    fun getInspectionShortName() = getQualityToolType().getInspectionShortName(project)

    override fun createComponent() = myPanel

    override fun isModified() = this.myPanel.isModified()

    override fun apply() {
        myPanel.apply()
    }

    override fun reset() {
        myPanel.reset()
    }
}

