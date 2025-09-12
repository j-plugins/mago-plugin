package com.github.xepozz.mago.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.lang.inspections.PhpInspectionsUtil

//import com.jetbrains.php.tools.quality.QualityToolProjectConfigurableForm
class MagoConfigurable(project: Project) : Configurable {
    val settings = project.getService(MagoProjectConfiguration::class.java)

    override fun getDisplayName() = "Mago"

    override fun getHelpTopic() = "reference.settings.php.mago"

//    override fun getId() = "settings.php.quality.tools.php.mago"

    //    override fun getQualityToolType() = MagoQualityToolType.INSTANCE
    override fun createComponent() = panel {

        group("Features") {
            row {
                cell(OnOffButton())
                    .label("Static analysis")
                    .bindSelected({ true }, {})

                PhpInspectionsUtil.createPanelWithSettingsLink(
                    PhpBundle.message("quality.tool.settings.link.inspection", "Mago"),
                    ErrorsConfigurable::class.java,
                    { project ->
                        ConfigurableExtensionPointUtil.createProjectConfigurableForProvider(
                            project,
                            ErrorsConfigurableProvider::class.java
                        ) as ErrorsConfigurable?
                    },
                    { it.selectInspectionTool("MagoGlobalInspection") })
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
    }

    override fun isModified(): Boolean {

        return false
    }

    override fun apply() {
    }
}

