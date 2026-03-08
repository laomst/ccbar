package site.laomst.ccbar.settings.ui.panels

import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.CommandConfig
import site.laomst.ccbar.settings.CommandType
import site.laomst.ccbar.settings.ui.components.AddCommandPopup
import site.laomst.ccbar.settings.ui.renderers.CommandListCellRenderer
import site.laomst.ccbar.settings.ui.shared.CommandListListener
import site.laomst.ccbar.settings.ui.shared.EditingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.ui.Messages
import com.intellij.ui.AnActionButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.util.Collections
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities

/**
 * Command 列表面板
 * 负责显示和管理 Command 列表（包括分割线）
 */
class CommandListPanel(
    private val context: EditingContext,
    private val listener: CommandListListener
) {
    private lateinit var listModel: CollectionListModel<CommandConfig>
    private lateinit var list: JBList<CommandConfig>
    private val addCommandPopup = AddCommandPopup(
        onAddCommand = { addCommand() },
        onAddSeparator = { addSeparator() }
    )
    private var addCommandButtonRef: JComponent? = null

    /**
     * 创建面板
     */
    fun createPanel(): JComponent {
        listModel = CollectionListModel()
        list = JBList(listModel).apply {
            cellRenderer = CommandListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { onSelectionChanged() }
        }

        val panel = BorderLayoutPanel()
        @Suppress("DEPRECATION")
        val decorator = ToolbarDecorator.createDecorator(list)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction { anActionButton ->
                // 点击时也显示气泡（兼容键盘操作）
                anActionButton.preferredPopupPoint.let { addCommandPopup.showFromActionButton(it) }
            }
            .setRemoveAction { removeCommand() }
            .setMoveUpAction { moveUp() }
            .setMoveDownAction { moveDown() }
            .addExtraAction(object : AnActionButton("复制", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) = copyCommand()
                override fun isEnabled(): Boolean = list.selectedIndex >= 0
            })

        val decoratorPanel = decorator.createPanel()

        // 在添加按钮上设置鼠标悬浮监听
        setupAddButtonHoverListener(decoratorPanel)

        panel.addToCenter(decoratorPanel)
        return panel
    }

    /**
     * 刷新列表数据
     */
    fun refreshData(commandBar: CommandBarConfig?) {
        listModel.removeAll()
        commandBar?.commands?.let { commands ->
            for (command in commands) {
                listModel.add(command)
            }
            if (commands.isNotEmpty()) {
                list.selectedIndex = 0
            } else {
                context.selectedCommand = null
                listener.onCommandSelected(null)
            }
        }
    }

    /**
     * 清空列表
     */
    fun clearData() {
        listModel.removeAll()
        context.selectedCommand = null
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
        if (index >= 0 && context.selectedCommandBar != null) {
            context.selectedCommand = listModel.getElementAt(index)
        } else {
            context.selectedCommand = null
        }
        listener.onCommandSelected(context.selectedCommand)
    }

    private fun addCommand() {
        val commandBar = context.selectedCommandBar ?: return
        val newCommand = CommandConfig(
            id = UUID.randomUUID().toString(),
            name = "New Command",
            baseCommand = "",
            defaultTerminalName = ""
        )
        commandBar.commands.add(newCommand)
        listModel.add(newCommand)
        list.selectedIndex = listModel.size - 1
        context.notifyCommandListChanged()
    }

    private fun addSeparator() {
        val commandBar = context.selectedCommandBar ?: return
        val newSeparator = CommandConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            type = CommandType.SEPARATOR
        )
        commandBar.commands.add(newSeparator)
        listModel.add(newSeparator)
        list.selectedIndex = listModel.size - 1
        context.notifyCommandListChanged()
    }

    private fun removeCommand() {
        val commandBar = context.selectedCommandBar ?: return
        val index = list.selectedIndex
        if (index >= 0) {
            val command = listModel.getElementAt(index)
            val typeName = if (command.isSeparator()) "分割线" else "Command"
            val result = Messages.showYesNoDialog(
                "确定要删除${typeName} '${command.name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                commandBar.commands.removeAt(index)
                listModel.remove(index)
                context.selectedCommand = null
                listener.onCommandSelected(null)
                context.notifyCommandListChanged()
            }
        }
    }

    private fun moveUp() {
        val commandBar = context.selectedCommandBar ?: return
        val index = list.selectedIndex
        if (index > 0) {
            context.ignoreUpdate = true
            try {
                Collections.swap(commandBar.commands, index, index - 1)
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
        val commandBar = context.selectedCommandBar ?: return
        val index = list.selectedIndex
        if (index < listModel.size - 1) {
            context.ignoreUpdate = true
            try {
                Collections.swap(commandBar.commands, index, index + 1)
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

    private fun copyCommand() {
        val commandBar = context.selectedCommandBar ?: return
        val index = list.selectedIndex
        if (index < 0) return
        val source = listModel.getElementAt(index)
        val copy = source.deepCopy().apply {
            id = UUID.randomUUID().toString()
            name = source.name + "-copy"
            quickParams.forEach { sb -> sb.id = UUID.randomUUID().toString() }
        }
        val insertIndex = index + 1
        commandBar.commands.add(insertIndex, copy)
        listModel.add(insertIndex, copy)
        list.selectedIndex = insertIndex
        context.notifyCommandListChanged()
    }

    /**
     * 延迟到面板显示后，在 ActionToolbar 中找到添加按钮的视觉组件并绑定鼠标悬浮监听
     */
    private fun setupAddButtonHoverListener(decoratorPanel: JPanel) {
        decoratorPanel.addHierarchyListener {
            if (addCommandButtonRef != null || !decoratorPanel.isShowing) return@addHierarchyListener
            SwingUtilities.invokeLater { findAndBindAddButton(decoratorPanel) }
        }
    }

    /**
     * 通过 IntelliJ 平台 API 在 ActionToolbar 中精确匹配添加按钮的视觉组件
     */
    private fun findAndBindAddButton(decoratorPanel: JPanel) {
        if (addCommandButtonRef != null) return

        // 通过 ToolbarDecorator 官方 API 获取 Add 对应的 AnActionButton
        val addAction = ToolbarDecorator.findAddButton(decoratorPanel) ?: return

        // 找到 CommonActionsPanel，获取其内部的 ActionToolbar
        val actionsPanel = UIUtil.findComponentOfType(decoratorPanel, CommonActionsPanel::class.java) ?: return
        val toolbarComp = actionsPanel.toolbar.component

        // 遍历 toolbar 子组件，匹配持有 Add Action 的视觉按钮
        for (comp in toolbarComp.components) {
            if (comp !is JComponent || comp !is AnActionHolder) continue
            val action = (comp as AnActionHolder).action
            if (action == addAction ||
                (action is ActionWithDelegate<*> && action.delegate == addAction)) {
                addCommandButtonRef = comp
                // 禁用按钮的 tooltip，避免与气泡弹窗冲突
                AddCommandPopup.suppressActionButtonTooltip(comp)
                addCommandPopup.bindToButton(comp)
                return
            }
        }
    }
}
