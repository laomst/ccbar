package com.github.ccbar.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * 环境变量编辑对话框
 * 提供表格形式编辑环境变量（变量名、值），支持增删和上下移动
 */
class EnvVariablesDialog(
    project: Project?,
    initialEnvVars: String
) : DialogWrapper(project) {

    private val tableModel = object : DefaultTableModel(arrayOf("变量名", "值"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }

    private val table = JBTable(tableModel)

    /**
     * 获取编辑后的环境变量文本（KEY1=val1;KEY2=val2 格式）
     */
    val envVariablesText: String
        get() {
            // 提交正在编辑的单元格
            if (table.isEditing) {
                table.cellEditor?.stopCellEditing()
            }
            val entries = mutableListOf<String>()
            for (i in 0 until tableModel.rowCount) {
                val key = (tableModel.getValueAt(i, 0) as? String)?.trim() ?: ""
                val value = (tableModel.getValueAt(i, 1) as? String)?.trim() ?: ""
                if (key.isNotEmpty()) {
                    entries.add("$key=$value")
                }
            }
            return entries.joinToString(";")
        }

    init {
        title = "环境变量配置"
        setOKButtonText("确定")
        setCancelButtonText("取消")

        // 解析初始环境变量到表格
        if (initialEnvVars.isNotBlank()) {
            for (entry in initialEnvVars.split(";")) {
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) continue
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex > 0) {
                    val key = trimmed.substring(0, eqIndex)
                    val value = trimmed.substring(eqIndex + 1)
                    tableModel.addRow(arrayOf(key, value))
                } else {
                    tableModel.addRow(arrayOf(trimmed, ""))
                }
            }
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(450, 300)
        }

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction {
                // 提交正在编辑的单元格
                if (table.isEditing) {
                    table.cellEditor?.stopCellEditing()
                }
                tableModel.addRow(arrayOf("", ""))
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
