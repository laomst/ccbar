package com.github.ccbar.settings.ui

import com.github.ccbar.settings.SubButtonConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * SubButton 编辑对话框
 * 提供表格形式编辑 SubButton（名称、参数），支持增删和上下移动
 */
class SubButtonEditDialog(
    project: Project?,
    private val initialSubButtons: List<SubButtonConfig>
) : DialogWrapper(project) {

    private val tableModel = object : DefaultTableModel(arrayOf("名称", "参数"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    private val table = JBTable(tableModel)

    /**
     * 获取编辑后的 SubButton 列表
     */
    fun getEditedSubButtons(): List<SubButtonConfig> {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }
        val result = mutableListOf<SubButtonConfig>()
        for (i in 0 until tableModel.rowCount) {
            val name = (tableModel.getValueAt(i, 0) as? String)?.trim() ?: ""
            val params = (tableModel.getValueAt(i, 1) as? String)?.trim() ?: ""
            val id = if (i < initialSubButtons.size) initialSubButtons[i].id else UUID.randomUUID().toString()
            result.add(SubButtonConfig(id = id, name = name, params = params))
        }
        return result
    }

    init {
        title = "SubButton 编辑"
        setOKButtonText("确定")
        setCancelButtonText("取消")

        for (sb in initialSubButtons) {
            tableModel.addRow(arrayOf(sb.name, sb.params))
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(500, 350)
        }

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction {
                if (table.isEditing) {
                    table.cellEditor?.stopCellEditing()
                }
                tableModel.addRow(arrayOf("New SubButton", ""))
                val newRow = tableModel.rowCount - 1
                table.setRowSelectionInterval(newRow, newRow)
                table.editCellAt(newRow, 0)
            }
            .setRemoveAction {
                if (table.isEditing) {
                    table.cellEditor?.stopCellEditing()
                }
                val row = table.selectedRow
                if (row >= 0) {
                    tableModel.removeRow(row)
                    if (tableModel.rowCount > 0) {
                        val newSelection = minOf(row, tableModel.rowCount - 1)
                        table.setRowSelectionInterval(newSelection, newSelection)
                    }
                }
            }
            .setMoveUpAction {
                if (table.isEditing) {
                    table.cellEditor?.stopCellEditing()
                }
                val row = table.selectedRow
                if (row > 0) {
                    tableModel.moveRow(row, row, row - 1)
                    table.setRowSelectionInterval(row - 1, row - 1)
                }
            }
            .setMoveDownAction {
                if (table.isEditing) {
                    table.cellEditor?.stopCellEditing()
                }
                val row = table.selectedRow
                if (row >= 0 && row < tableModel.rowCount - 1) {
                    tableModel.moveRow(row, row, row + 1)
                    table.setRowSelectionInterval(row + 1, row + 1)
                }
            }

        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        return panel
    }
}
