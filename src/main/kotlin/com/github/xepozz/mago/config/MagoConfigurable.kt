package com.github.xepozz.mago.config

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.MagoQualityToolType
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.lang.inspections.PhpInspectionsUtil

class MagoConfigurable(val project: Project) : Configurable {
    val settings = project.getService(MagoProjectConfiguration::class.java)
    val inspectionProfileManager = InspectionProfileManager.getInstance(project)

    lateinit var myPanel: DialogPanel

    override fun getDisplayName() = "Mago"

    override fun getHelpTopic() = "reference.settings.php.mago"

    fun getQualityToolType() = MagoQualityToolType.INSTANCE

    fun getInspectionShortName() = getQualityToolType().getInspectionShortName(project)

    override fun createComponent(): DialogPanel {

        return panel {

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
                        .bindSelected({ false }, {})
                }.visible(true).enabled(false)

                row {
                    cell(OnOffButton())
                        .label("Formatter")
                        .bindSelected({ false }, {})
                }.visible(true).enabled(false)
            }
            group("Options") {
                row {
                    label("Path to Mago executable")
                    textField()
                    browserLink("Download Mago", "https://github.com/carthage-software/mago")
                }
                row {
                    label("Additional parameters")
                    textField()
                }
            }
        }.also { myPanel = it }
    }

    override fun isModified(): Boolean {
        return this.myPanel.isModified()
    }

    override fun apply() {
        this.myPanel.apply()
    }

    override fun reset() {
        this.myPanel.reset()
    }
}

