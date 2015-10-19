/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.ImportLayoutPanel
import com.intellij.application.options.PackagePanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.PackageEntryTable
import com.intellij.ui.OptionGroup
import com.intellij.ui.components.JBScrollPane
import org.jdom.Element
import org.jetbrains.kotlin.idea.core.formatter.JetCodeStyleSettings
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.table.AbstractTableModel

class ImportSettingsPanelWrapper(settings: CodeStyleSettings) : CodeStyleAbstractPanel(settings) {
    private val importsPanel = ImportSettingsPanel(settings)

    private fun CodeStyleSettings.kotlinSettings() = getCustomSettings(JetCodeStyleSettings::class.java)

    override fun getRightMargin() = throw UnsupportedOperationException()

    override fun createHighlighter(scheme: EditorColorsScheme) = throw UnsupportedOperationException()

    override fun getFileType() = throw UnsupportedOperationException()

    override fun getPreviewText() = null

    override fun apply(settings: CodeStyleSettings) = importsPanel.apply(settings.kotlinSettings())

    override fun isModified(settings: CodeStyleSettings) = importsPanel.isModified(settings.kotlinSettings())

    override fun getPanel() = importsPanel

    override fun resetImpl(settings: CodeStyleSettings) {
        importsPanel.reset(settings.kotlinSettings())
    }

    override fun getTabTitle() = ApplicationBundle.message("title.imports")
}

class ImportSettingsPanel(private val commonSettings: CodeStyleSettings) : JPanel() {
    private val cbImportNestedClasses = JCheckBox("Insert imports for nested classes")

    private val starImportPackageEntryTable = PackageEntryTable()
    private val dummyImportLayoutPanel = object : ImportLayoutPanel() {
        override fun areStaticImportsEnabled() = false
        override fun refresh() { }
    }
    private val starImportPackageTable = ImportLayoutPanel.createTableForPackageEntries(starImportPackageEntryTable, dummyImportLayoutPanel)

    init {
        setLayout(BorderLayout())
        add(JBScrollPane(JPanel(GridBagLayout()).apply {
            val constraints = GridBagConstraints().apply {
                weightx = 1.0
                insets = Insets(0, 10, 10, 10)
            }
            add(createGeneralOptionsPanel(), constraints.apply {
                fill = GridBagConstraints.HORIZONTAL
                gridy = 0
            })
            add(PackagePanel.createPackagesPanel(starImportPackageTable, starImportPackageEntryTable), constraints.apply {
                gridy = 1
                fill = GridBagConstraints.BOTH
                weighty = 1.0
            })
        }), BorderLayout.CENTER)
    }

    private fun createGeneralOptionsPanel(): JPanel {

/*
        fun updateEnabled() {
            starImportLimitField.setEnabled(rbUseStarImportsIfMore.isSelected())
        }
        rbUseStarImportsIfMore.addChangeListener { updateEnabled() }
        updateEnabled()
*/

        return OptionGroup(ApplicationBundle.message("title.general")).apply {
            this.add(NameCountToUseStarImportSelector().createPanel(), NameCountToUseStarImportSelector().createPanel())

            add(cbImportNestedClasses)
        }.createPanel()
    }

    private class NameCountToUseStarImportSelector(label: String) : OptionGroup() {
        private val rbUseSingleImports = JRadioButton("With single name")
        private val rbUseStarImports = JRadioButton("With '*'")
        private val rbUseStarImportsIfMore = JRadioButton("With '*' if more than ")
        private val starImportLimitModel = SpinnerNumberModel(4, 1, 100, 1)
        private val starImportLimitField = JSpinner(starImportLimitModel)

        init {
            ButtonGroup().apply {
                add(rbUseSingleImports)
                add(rbUseStarImports)
                add(rbUseStarImportsIfMore)
            }

            add(JLabel(label))
            add(rbUseSingleImports, true)
            add(rbUseStarImports, true)
            val jPanel: JPanel = JPanel(GridBagLayout())
            add(jPanel.apply {
                val constraints = GridBagConstraints().apply { gridx = GridBagConstraints.RELATIVE }
                getUIClassID()
                this.add(rbUseStarImportsIfMore, constraints)
                this.add(starImportLimitField, constraints)
                this.add(JLabel(" names used"), constraints.apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 })
            }, true)
        }
    }

    fun reset(settings: JetCodeStyleSettings) {
/*
        when (settings.NAME_COUNT_TO_USE_STAR_IMPORT) {
            Int.MAX_VALUE -> rbUseSingleImports.setSelected(true)

            1 -> rbUseStarImports.setSelected(true)

            else -> {
                rbUseStarImportsIfMore.setSelected(true)
                starImportLimitField.setValue(settings.NAME_COUNT_TO_USE_STAR_IMPORT - 1)
            }
        }
*/

        cbImportNestedClasses.setSelected(settings.IMPORT_NESTED_CLASSES)

        starImportPackageEntryTable.copyFrom(settings.PACKAGES_TO_USE_STAR_IMPORTS)
        (starImportPackageTable.getModel() as AbstractTableModel).fireTableDataChanged()
        if (starImportPackageTable.getRowCount() > 0) {
            starImportPackageTable.getSelectionModel().setSelectionInterval(0, 0)
        }
    }

    fun apply(settings: JetCodeStyleSettings, dropEmptyPackages: Boolean = true) {
/*
        settings.NAME_COUNT_TO_USE_STAR_IMPORT = when {
            rbUseSingleImports.isSelected() -> Int.MAX_VALUE
            rbUseStarImports.isSelected() -> 1
            else -> (starImportLimitModel.getNumber() as Int) + 1
        }
*/
        settings.IMPORT_NESTED_CLASSES = cbImportNestedClasses.isSelected()

        if (dropEmptyPackages) {
            starImportPackageEntryTable.removeEmptyPackages()
        }
        settings.PACKAGES_TO_USE_STAR_IMPORTS.copyFrom(starImportPackageEntryTable)
    }

    fun isModified(settings: JetCodeStyleSettings): Boolean {
        val tempSettings = JetCodeStyleSettings(commonSettings)
        apply(tempSettings, dropEmptyPackages = false)
        val root = Element("fake")
        tempSettings.writeExternal(root, settings)
        return root.getChildren().isNotEmpty()
    }
}
