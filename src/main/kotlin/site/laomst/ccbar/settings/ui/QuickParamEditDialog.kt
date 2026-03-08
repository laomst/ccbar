package site.laomst.ccbar.settings.ui

import site.laomst.ccbar.settings.QuickParamConfig
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
 * 快捷参数编辑对话框
 * 提供表格形式编辑快捷参数（名称、参数、启用），支持增删和上下移动
 */
class QuickParamEditDialog(
    project: Project?,
    private val initialQuickParams: List<QuickParamConfig>
) : DialogWrapper(project) {

    private val tableModel = object : DefaultTableModel(arrayOf("名称", "参数", "启用"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
        override fun getColumnClass(columnIndex: Int): Class<*> {
            return if (columnIndex == 2) java.lang.Boolean::class.java else String::class.java
        }
    }

    private val table = JBTable(tableModel)

    /**
     * 获取编辑后的快捷参数列表
     */
    fun getEditedQuickParams(): List<QuickParamConfig> {
        if (table.isEditing) {
            table.cellEditor?.stopCellEditing()
        }
        val result = mutableListOf<QuickParamConfig>()
        for (i in 0 until tableModel.rowCount) {
            val name = (tableModel.getValueAt(i, 0) as? String)?.trim() ?: ""
            val params = (tableModel.getValueAt(i, 1) as? String)?.trim() ?: ""
            val enabled = tableModel.getValueAt(i, 2) as? Boolean ?: true
            val id = if (i < initialQuickParams.size) initialQuickParams[i].id else UUID.randomUUID().toString()
            result.add(QuickParamConfig(id = id, name = name, params = params, enabled = enabled))
        }
        return result
    }

    init {
        title = "快捷参数编辑"
        setOKButtonText("确定")
        setCancelButtonText("取消")

        for (sb in initialQuickParams) {
            tableModel.addRow(arrayOf(sb.name, sb.params, sb.enabled))
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            preferredSize = Dimension(550, 350)
        }

        // 设置"启用"列宽度
        table.columnModel.getColumn(2).apply {
            preferredWidth = 50
            maxWidth = 60
            minWidth = 40
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
                tableModel.addRow(arrayOf("New QuickParam", "", true))
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
