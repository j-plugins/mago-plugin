package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.tools.quality.QualityToolConfigurationComboBox
import com.jetbrains.php.tools.quality.QualityToolsIgnoreFilesConfigurable
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.SwingConstants

class MagoConfigurable(val project: Project) : Configurable {
    private val settings = MagoProjectConfiguration.getInstance(project)

    private val qualityToolConfigurationComboBox =
        QualityToolConfigurationComboBox(project, MagoQualityToolType.INSTANCE)

    private val workspaceMappingsModel: ListTableModel<MagoWorkspaceMapping> = ListTableModel(
        object : ColumnInfo<MagoWorkspaceMapping, String>(MagoBundle.message("settings.workspaceMappings.column.workspace")) {
            override fun valueOf(item: MagoWorkspaceMapping) = item.workspace
            override fun setValue(item: MagoWorkspaceMapping, value: String) {
                item.workspace = value
            }

            override fun isCellEditable(item: MagoWorkspaceMapping) = true
        },
        object : ColumnInfo<MagoWorkspaceMapping, String>(
            MagoBundle.message("settings.workspaceMappings.column.configFile")
        ) {
            override fun valueOf(item: MagoWorkspaceMapping) = item.configFile
            override fun setValue(item: MagoWorkspaceMapping, value: String) {
                item.configFile = value
            }

            override fun isCellEditable(item: MagoWorkspaceMapping) = true
        }
    )

    private val workspaceMappingsTable = JBTable(workspaceMappingsModel).apply {
        setRowHeight(30)
        columnModel.getColumn(0).cellEditor = PathCellEditor(
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        columnModel.getColumn(1).cellEditor = PathCellEditor(
            FileChooserDescriptorFactory.createSingleFileDescriptor("toml")
        )
    }

    private val workspaceMappingsPanel = ToolbarDecorator.createDecorator(workspaceMappingsTable)
        .setAddAction {
            workspaceMappingsModel.addRow(MagoWorkspaceMapping())
            val row = workspaceMappingsModel.rowCount - 1
            workspaceMappingsTable.editCellAt(row, 0)
        }
        .setRemoveAction {
            val row = workspaceMappingsTable.selectedRow
            if (row >= 0) workspaceMappingsModel.removeRow(row)
        }
        .createPanel()
        .apply { preferredSize = Dimension(0, 150) }

    private inner class PathCellEditor(
        descriptor: com.intellij.openapi.fileChooser.FileChooserDescriptor
    ) : AbstractTableCellEditor() {
        private val field = TextFieldWithBrowseButton()

        init {
            field.addBrowseFolderListener(project, descriptor)
        }

        override fun getCellEditorValue(): Any = field.text

        override fun getTableCellEditorComponent(
            table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int
        ): Component {
            field.text = value?.toString() ?: ""
            return field
        }
    }

    private var myPanel = panel {
        row {
            browserLink(MagoBundle.message("settings.link.download"), "https://github.com/carthage-software/mago")
            browserLink(MagoBundle.message("settings.link.documentation"), "https://mago.carthage.software/guide/getting-started")
            browserLink(MagoBundle.message("settings.link.reportBug"), "https://github.com/j-plugins/mago-plugin/issues")
            browserLink(MagoBundle.message("settings.link.requestFeature"), "https://github.com/j-plugins/mago-plugin/issues")

            cell(
                JBLabel(
                    MagoBundle.message("settings.debug.label"),
                    AllIcons.Toolwindows.ToolWindowDebugger,
                    SwingConstants.RIGHT
                )
            )
                .align(AlignX.RIGHT)
                .resizableColumn()
            cell(OnOffButton())
                .bindSelected(settings::debug)
                .align(AlignX.RIGHT)
        }

        group(MagoBundle.message("settings.options.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::enabled)
                    .comment(MagoBundle.message("settings.enabled.comment"))
            }.layout(RowLayout.PARENT_GRID)

            row {
                cell(qualityToolConfigurationComboBox)
                    .label(MagoBundle.message("settings.configuration.label"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)

            row {
                @Suppress("UnstableApiUsage")
                textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile())
                    .bindText(settings::configurationFile)
                    .label(MagoBundle.message("settings.defaultConfigFile.label"))
                    .comment(MagoBundle.message("settings.defaultConfigFile.comment"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)

            row {
                cell(
                    ActionLink(
                        PhpBundle.message("guality.tool.configuration.show.ignored.files"),
                        ActionListener {
                            ShowSettingsUtil.getInstance().editConfigurable(
                                project,
                                QualityToolsIgnoreFilesConfigurable(
                                    MagoQualityToolType.INSTANCE,
                                    project
                                )
                            )
                        })
                )
            }
        }

        group(MagoBundle.message("settings.workspaceMappings.title")) {
            row {
                comment(MagoBundle.message("settings.workspaceMappings.comment"))
            }
            row {
                cell(workspaceMappingsPanel)
                    .align(AlignX.FILL)
            }
        }

        group(MagoBundle.message("settings.analyzer.title")) {
            row {
                browserLink(MagoBundle.message("settings.link.documentation"), "https://mago.carthage.software/tools/analyzer/overview")
                    .align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)

            row {
                textField()
                    .label(MagoBundle.message("settings.additionalParameters"))
                    .bindText(settings::analyzeAdditionalParameters)
                    .comment(MagoBundle.message("settings.analyzer.paramsComment"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }

        group(MagoBundle.message("settings.formatter.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::formatterEnabled)
                browserLink(MagoBundle.message("settings.link.documentation"), "https://mago.carthage.software/tools/formatter/overview")
                    .align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)

            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.formatter.formatAfterFix"))
                    .bindSelected(settings::formatAfterFix)
                comment(MagoBundle.message("settings.formatter.formatAfterFix.comment"))
            }.layout(RowLayout.PARENT_GRID)

            row {
                textField()
                    .label(MagoBundle.message("settings.additionalParameters"))
                    .bindText(settings::formatAdditionalParameters)
                    .comment(MagoBundle.message("settings.formatter.paramsComment"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }

        group(MagoBundle.message("settings.linter.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::linterEnabled)
                browserLink(MagoBundle.message("settings.link.documentation"), "https://mago.carthage.software/tools/linter/overview")
                    .align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)

            row {
                textField()
                    .label(MagoBundle.message("settings.additionalParameters"))
                    .bindText(settings::lintAdditionalParameters)
                    .comment(MagoBundle.message("settings.linter.paramsComment"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }

        group(MagoBundle.message("settings.guard.title")) {
            row {
                cell(OnOffButton())
                    .label(MagoBundle.message("settings.enabled"))
                    .bindSelected(settings::guardEnabled)
                browserLink(MagoBundle.message("settings.link.documentation"), "https://mago.carthage.software/tools/guard/overview")
                    .align(AlignX.RIGHT)
            }.layout(RowLayout.PARENT_GRID)

            row {
                textField()
                    .label(MagoBundle.message("settings.additionalParameters"))
                    .bindText(settings::guardAdditionalParameters)
                    .comment(MagoBundle.message("settings.guard.paramsComment"))
                    .align(AlignX.FILL)
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    override fun createComponent(): JComponent = myPanel

    override fun isModified(): Boolean {
        return myPanel.isModified()
                || qualityToolConfigurationComboBox.selectedItemId != getSavedSelectedConfigurationId()
                || workspaceMappingsChanged()
    }

    override fun apply() {
        updateSelectedConfiguration(qualityToolConfigurationComboBox.selectedItemId)
        myPanel.apply()
        settings.workspaceMappings = workspaceMappingsModel.items
            .map { MagoWorkspaceMapping(it.workspace, it.configFile) }
            .toMutableList()
    }

    override fun reset() {
        myPanel.reset()
        qualityToolConfigurationComboBox.reset(project, getSavedSelectedConfigurationId())
        workspaceMappingsModel.items = settings.workspaceMappings
            .map { MagoWorkspaceMapping(it.workspace, it.configFile) }
    }

    override fun getDisplayName() = MagoBundle.message("mago.title")

    private fun updateSelectedConfiguration(newConfigurationId: String?) {
        val projectConfiguration = MagoQualityToolType.INSTANCE.getProjectConfiguration(project)
        if (newConfigurationId != projectConfiguration.selectedConfigurationId) {
            projectConfiguration.selectedConfigurationId = newConfigurationId
        }
    }

    private fun getSavedSelectedConfigurationId(): String? {
        return MagoQualityToolType.INSTANCE.getProjectConfiguration(project).selectedConfigurationId
    }

    private fun workspaceMappingsChanged(): Boolean {
        val current = workspaceMappingsModel.items
        val saved = settings.workspaceMappings
        if (current.size != saved.size) return true
        return current.zip(saved).any { (c, s) ->
            c.workspace != s.workspace || c.configFile != s.configFile
        }
    }
}
