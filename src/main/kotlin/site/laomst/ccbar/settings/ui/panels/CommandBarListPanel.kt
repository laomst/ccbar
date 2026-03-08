package site.laomst.ccbar.settings.ui.panels

import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.ui.renderers.CommandBarListCellRenderer
import site.laomst.ccbar.settings.ui.shared.CommandBarListListener
import site.laomst.ccbar.settings.ui.shared.EditingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.util.Collections
import java.util.UUID
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/**
 * CommandBar 列表面板
 * 负责显示和管理 CommandBar 列表
 */
class CommandBarListPanel(
    private val context: EditingContext,
    private val listener: CommandBarListListener
) {
    private lateinit var listModel: CollectionListModel<CommandBarConfig>
    private lateinit var list: JBList<CommandBarConfig>

    /**
     * 创建面板
     */
    fun createPanel(): JComponent {
        listModel = CollectionListModel(context.currentState.commandBars)
        list = JBList(listModel).apply {
            cellRenderer = CommandBarListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { onSelectionChanged() }
        }

        val panel = BorderLayoutPanel().withBorder(JBUI.Borders.empty(8))
        @Suppress("DEPRECATION")
        val decorator = ToolbarDecorator.createDecorator(list)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction { addCommandBar() }
            .setRemoveAction { removeCommandBar() }
            .setMoveUpAction { moveUp() }
            .setMoveDownAction { moveDown() }
            .addExtraAction(object : AnActionButton("复制", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) = copyCommandBar()
                override fun isEnabled(): Boolean = list.selectedIndex >= 0
            })

        panel.addToCenter(decorator.createPanel())
        return panel
    }

    /**
     * 刷新列表数据
     */
    fun refreshData() {
        listModel.removeAll()
        for (btn in context.currentState.commandBars) {
            listModel.add(btn)
        }
    }

    /**
     * 获取当前选中的索引
     */
    fun getSelectedIndex(): Int = list.selectedIndex

    /**
     * 设置选中索引
     */
    fun setSelectedIndex(index: Int) {
        list.selectedIndex = index
    }

    /**
     * 重绘列表
     */
    fun repaintList() {
        list.repaint()
    }

    private fun onSelectionChanged() {
        if (context.ignoreUpdate) return

        val index = list.selectedIndex
        if (index >= 0) {
            context.selectedCommandBar = listModel.getElementAt(index)
        } else {
            context.selectedCommandBar = null
        }
        listener.onCommandBarSelected(context.selectedCommandBar)
    }

    private fun addCommandBar() {
        val newCommandBar = CommandBarConfig(
            id = UUID.randomUUID().toString(),
            name = "New CommandBar",
            icon = "builtin:AllIcons.Actions.Execute"
        )
        listModel.add(newCommandBar)
        context.currentState.commandBars.add(newCommandBar)
        list.selectedIndex = listModel.size - 1
        context.notifyCommandBarListChanged()
    }

    private fun removeCommandBar() {
        val index = list.selectedIndex
        if (index >= 0) {
            val result = Messages.showYesNoDialog(
                "确定要删除 CommandBar '${listModel.getElementAt(index).name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                listModel.remove(index)
                context.currentState.commandBars.removeAt(index)
                context.selectedCommandBar = null
                context.notifyCommandBarListChanged()
                listener.onCommandBarSelected(null)
            }
        }
    }

    private fun moveUp() {
        val index = list.selectedIndex
        if (index > 0) {
            context.ignoreUpdate = true
            try {
                Collections.swap(context.currentState.commandBars, index, index - 1)
                val item = listModel.getElementAt(index)
                listModel.remove(index)
                listModel.add(index - 1, item)
                list.selectedIndex = index - 1
            } finally {
                context.ignoreUpdate = false
            }
            onSelectionChanged()
        }
    }

    private fun moveDown() {
        val index = list.selectedIndex
        if (index < listModel.size - 1) {
            context.ignoreUpdate = true
            try {
                Collections.swap(context.currentState.commandBars, index, index + 1)
                val item = listModel.getElementAt(index)
                listModel.remove(index)
                listModel.add(index + 1, item)
                list.selectedIndex = index + 1
            } finally {
                context.ignoreUpdate = false
            }
            onSelectionChanged()
        }
    }

    private fun copyCommandBar() {
        val index = list.selectedIndex
        if (index < 0) return
        val source = listModel.getElementAt(index)
        val copy = source.deepCopy().apply {
            id = UUID.randomUUID().toString()
            name = source.name + "-copy"
            commonQuickParams.forEach { it.id = UUID.randomUUID().toString() }
            commands.forEach {
                it.id = UUID.randomUUID().toString()
                it.quickParams.forEach { sb -> sb.id = UUID.randomUUID().toString() }
            }
        }
        val insertIndex = index + 1
        context.currentState.commandBars.add(insertIndex, copy)
        listModel.add(insertIndex, copy)
        list.selectedIndex = insertIndex
        context.notifyCommandBarListChanged()
    }
}
