package com.github.ccbar.settings.ui

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.*
import com.github.ccbar.terminal.EnvVariablesDialog
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 配置模式
 */
private enum class ConfigMode {
    SYSTEM,   // 系统配置
    PROJECT   // 项目配置
}

/**
 * CCBar 设置面板
 * 使用 JBSplitter 构建左右分栏布局
 * 支持系统配置和项目配置切换
 */
class CCBarSettingsPanel(private val project: Project?) {

    // 系统配置编辑状态（深拷贝）
    private var editingSystemState: CCBarSettings.State = CCBarSettings.State()

    // 项目配置编辑状态（深拷贝）
    private var editingProjectState: CCBarProjectSettings.ProjectState = CCBarProjectSettings.ProjectState()

    // 当前配置模式
    private var currentConfigMode: ConfigMode = ConfigMode.SYSTEM

    // 当前编辑状态（根据模式动态切换）
    private val editingState: CCBarSettings.State
        get() = if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
            // 项目配置模式：使用包装器将项目配置伪装成系统配置格式
            CCBarSettings.State(editingProjectState.commandBars)
        } else {
            editingSystemState
        }

    // 项目配置是否启用
    private var isProjectConfigEnabled: Boolean = false

    // UI 组件
    private lateinit var buttonListModel: CollectionListModel<CommandBarConfig>
    private lateinit var buttonList: JBList<CommandBarConfig>
    private lateinit var optionListModel: CollectionListModel<CommandConfig>
    private lateinit var optionList: JBList<CommandConfig>
    private lateinit var quickParamSummaryField: JBTextField

    // Button 详情字段
    private lateinit var buttonNameField: JBTextField
    private lateinit var buttonIconField: TextFieldWithBrowseButton
    private lateinit var buttonCommandField: JBTextField
    private lateinit var buttonWorkingDirectoryField: TextFieldWithBrowseButton
    private lateinit var buttonTerminalNameField: JBTextField

    // Button 提示标签（用于动态更新文字）
    private lateinit var buttonCommandHintLabel: JLabel
    private lateinit var buttonWorkDirHintLabel: JLabel

    // Button 工作目录面板（用于控制显示/隐藏）
    private lateinit var buttonWorkingDirectoryPanel: JComponent

    // Button 环境变量面板和字段（仅直接命令模式时显示）
    private lateinit var buttonEnvVariablesPanel: JComponent
    private lateinit var buttonEnvVariablesField: JBTextField

    // Button 终端名称面板（用于控制显示/隐藏）
    private lateinit var buttonTerminalNamePanel: JComponent

    // Button 终端模式面板和下拉框（仅直接命令模式时显示）
    private lateinit var buttonTerminalModePanel: JComponent
    private lateinit var buttonTerminalModeCheckbox: JCheckBox

    // Button 简易模式复选框（仅Command 列表模式时显示）
    private lateinit var simpleModePanel: JComponent
    private lateinit var simpleModeCheckbox: JCheckBox

    // Button 启用复选框
    private lateinit var buttonEnabledCheckbox: JCheckBox

    // Command 启用复选框
    private lateinit var optionEnabledCheckbox: JCheckBox
    private lateinit var optionEnabledPanel: JComponent

    // Options 面板引用（用于控制显示/隐藏）
    private lateinit var optionPanel: JComponent

    // 右侧详情容器（用于切换空状态和详情面板）
    private lateinit var rightContainer: JPanel

    // Option 详情字段
    private lateinit var optionNameField: JBTextField
    private lateinit var optionIconField: TextFieldWithBrowseButton
    private lateinit var optionIconPanel: JPanel
    private lateinit var baseCommandField: JBTextField
    private lateinit var optionEnvVariablesField: JBTextField
    private lateinit var optionEnvVariablesPanel: JPanel
    private lateinit var workingDirectoryField: TextFieldWithBrowseButton
    private lateinit var defaultTerminalNameField: JBTextField

    // Option 提示标签（用于动态更新文字）
    private lateinit var optionWorkDirHintLabel: JLabel

    // Option 详情面板和 快捷参数面板（用于控制显示/隐藏）
    private lateinit var optionDetailOuterPanel: JComponent
    private lateinit var quickParamOuterPanel: JComponent

    // Option 详情面板中各个字段的容器（用于控制分割线时只显示名称）
    private lateinit var optionCommandPanel: JPanel
    private lateinit var optionDirPanel: JPanel
    private lateinit var optionDirHintPanel: JPanel
    private lateinit var optionTerminalNamePanel: JPanel

    // Option 终端模式面板和下拉框
    private lateinit var optionTerminalModePanel: JPanel
    private lateinit var optionTerminalModeCheckbox: JCheckBox

    // Option 详情面板的边框（用于动态更新标题）
    private lateinit var optionDetailTitledBorder: javax.swing.border.TitledBorder

    // 当前选中的 Button 和 Option
    private var selectedCommandBar: CommandBarConfig? = null
    private var selectedCommand: CommandConfig? = null

    // 忽略更新标志（用于批量更新时避免循环）
    private var ignoreUpdate = false

    // 添加 Command气泡弹窗
    private var addOptionPopup: JBPopup? = null

    // 气泡延迟关闭计时器
    private var popupCloseTimer: javax.swing.Timer? = null

    // 添加CommandBar的 UI 组件引用（用于气泡锚定）
    private var addOptionButtonRef: JComponent? = null

    // 卡片布局常量
    private companion object {
        const val CARD_EMPTY = "empty"
        const val CARD_DETAIL = "detail"
    }

    /**
     * 获取当前打开项目的根路径
     */
    private fun getCurrentProjectPath(): String? {
        return project?.basePath ?: ProjectManager.getInstance().openProjects.firstOrNull()?.basePath
    }

    /**
     * 创建主面板
     */
    fun createPanel(): JComponent {
        // 初始化系统配置编辑状态（深拷贝，确保编辑状态和原始状态独立）
        val systemSettings = CCBarSettings.getInstance()
        editingSystemState = systemSettings.state.deepCopy()

        // 初始化项目配置编辑状态（如果有项目）
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            editingProjectState = projectSettings.state.deepCopy()
            isProjectConfigEnabled = editingProjectState.enabled

            // 如果项目配置已启用，默认显示项目配置
            if (isProjectConfigEnabled) {
                currentConfigMode = ConfigMode.PROJECT
            }
        }

        // 创建主面板
        val mainPanel = JPanel(BorderLayout())
        mainPanelRef = mainPanel

        // 如果有项目，添加项目配置控制区域
        if (project != null) {
            mainPanel.add(createProjectConfigControlPanel(), BorderLayout.NORTH)
        }

        // 创建配置内容区域
        val contentPanel = if (project != null && isProjectConfigEnabled) {
            // 有项目且启用项目配置：显示 Tab 切换
            createTabbedConfigPanel()
        } else {
            // 无项目或未启用项目配置：直接显示系统配置
            createSingleConfigPanel()
        }
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // 底部操作CommandBar
        mainPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH)

        return mainPanel
    }

    /**
     * 创建项目配置控制面板（头部提示区域）
     */
    private fun createProjectConfigControlPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8, 8, 0, 8)

        // 左侧提示信息
        val infoLabel = JLabel().apply {
            icon = com.intellij.icons.AllIcons.General.Information
            horizontalTextPosition = SwingConstants.RIGHT
            iconTextGap = JBUI.scale(4)
        }
        panel.add(infoLabel, BorderLayout.CENTER)

        // 右侧操作CommandBar
        val actionButton = JButton()
        panel.add(actionButton, BorderLayout.EAST)

        // 更新显示状态
        updateProjectConfigControlPanel(infoLabel, actionButton)

        return panel
    }

    /**
     * 更新项目配置控制面板的显示状态
     */
    private fun updateProjectConfigControlPanel(infoLabel: JLabel, actionButton: JButton) {
        if (isProjectConfigEnabled) {
            infoLabel.text = "项目配置已启用，系统配置将不会被加载"
            actionButton.text = "禁用项目配置"
            actionButton.actionListeners.forEach { actionButton.removeActionListener(it) }
            actionButton.addActionListener { disableProjectConfig() }
        } else {
            infoLabel.text = "启用项目配置后，可以为当前项目设置独立的CommandBar 配置。项目配置存储在 .idea 目录中，跟随项目。"
            actionButton.text = "启用项目配置"
            actionButton.actionListeners.forEach { actionButton.removeActionListener(it) }
            actionButton.addActionListener { enableProjectConfig() }
        }
    }

    /**
     * 创建带 Tab 切换的配置面板
     */
    private lateinit var tabbedPaneRef: JTabbedPane
    private lateinit var sharedConfigPanel: JComponent

    private fun createTabbedConfigPanel(): JComponent {
        val tabbedPane = JTabbedPane()
        tabbedPaneRef = tabbedPane

        // 创建共享的配置内容面板
        sharedConfigPanel = createConfigContentPanel()

        // 创建占位面板
        val emptyPanel1 = JPanel()
        val emptyPanel2 = JPanel()

        // 两个 Tab 先用空面板占位
        tabbedPane.addTab("系统配置", emptyPanel1)
        tabbedPane.addTab("项目配置", emptyPanel2)

        // 根据当前模式设置初始 Tab 内容
        val initialIndex = if (currentConfigMode == ConfigMode.PROJECT) 1 else 0
        tabbedPane.setComponentAt(initialIndex, sharedConfigPanel)

        // 设置默认选中
        tabbedPane.selectedIndex = initialIndex

        // 监听 Tab 切换
        tabbedPane.addChangeListener { e ->
            val source = e.source as JTabbedPane
            val newIndex = source.selectedIndex
            val newMode = if (newIndex == 0) ConfigMode.SYSTEM else ConfigMode.PROJECT

            if (newMode != currentConfigMode) {
                // 切换模式前，保存当前编辑状态
                saveCurrentEditingState()

                // 切换模式
                currentConfigMode = newMode

                // 将共享面板移动到新 Tab
                val oldIndex = if (newMode == ConfigMode.PROJECT) 0 else 1
                source.setComponentAt(oldIndex, JPanel())  // 旧 Tab 用空面板
                source.setComponentAt(newIndex, sharedConfigPanel)  // 新 Tab 用共享面板

                // 刷新配置面板数据
                refreshConfigPanel()

                // 更新底部CommandBar面板
                updateActionButtonsPanel()
            }
        }

        return tabbedPane
    }

    /**
     * 创建单一配置面板（无 Tab 切换）
     */
    private fun createSingleConfigPanel(): JComponent {
        val outerPanel = JPanel(BorderLayout())

        // 如果有项目但未启用项目配置，显示启用提示
        if (project != null && !isProjectConfigEnabled) {
            val hintPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                add(JLabel("系统配置（所有项目共享）").apply {
                    font = font.deriveFont(java.awt.Font.BOLD)
                }, BorderLayout.WEST)
            }
            outerPanel.add(hintPanel, BorderLayout.NORTH)
        }

        outerPanel.add(createConfigContentPanel(), BorderLayout.CENTER)

        return outerPanel
    }

    /**
     * 创建配置内容面板（左右分栏布局）
     */
    private fun createConfigContentPanel(): JComponent {
        // 创建左右分割面板
        val splitter = OnePixelSplitter(false, 0.2f).apply {
            firstComponent = createButtonListPanel()
            // 右侧：使用卡片布局切换空状态和详情面板
            rightContainer = JPanel(CardLayout())
            rightContainer.add(createEmptyPanel(), CARD_EMPTY)
            rightContainer.add(createDetailPanel(), CARD_DETAIL)
            secondComponent = rightContainer
        }

        // 初始显示空状态面板
        showEmptyPanel()

        return splitter
    }

    /**
     * 保存当前编辑状态
     * 注意：由于 buttonListModel 中的元素和 editingState.commandBars 中的元素是同一个对象引用，
     * 用户的修改会直接反映到 editingProjectState 或 editingSystemState 中，
     * 所以不需要额外的深拷贝操作。
     */
    private fun saveCurrentEditingState() {
        // 快捷参数数据已通过对话框直接同步到数据模型，无需额外操作
        // 不需要深拷贝，因为修改已经直接反映到 editingProjectState/editingSystemState 中
    }

    /**
     * 刷新配置面板
     */
    private fun refreshConfigPanel() {
        // 重新加载CommandBar列表
        buttonListModel.removeAll()
        for (btn in editingState.commandBars) {
            buttonListModel.add(btn)
        }

        // 清空选中状态
        selectedCommandBar = null
        selectedCommand = null
        clearButtonDetail()
        clearOptionDetail()
        optionListModel.removeAll()
        updateQuickParamSummary()

        showEmptyPanel()
    }

    /**
     * 启用项目配置
     * - 如果项目配置已有数据（之前禁用过），恢复之前的数据
     * - 如果项目配置没有数据（首次启用），复制系统配置
     */
    private fun enableProjectConfig() {
        // 如果项目配置没有数据，才复制系统配置
        if (editingProjectState.commandBars.isEmpty()) {
            editingProjectState.commandBars = editingSystemState.deepCopy().commandBars
        }

        editingProjectState.enabled = true
        isProjectConfigEnabled = true
        currentConfigMode = ConfigMode.PROJECT

        // 需要重建整个面板
        rebuildMainPanel()
    }

    /**
     * 禁用项目配置
     */
    private fun disableProjectConfig() {
        editingProjectState.enabled = false
        isProjectConfigEnabled = false
        currentConfigMode = ConfigMode.SYSTEM

        // 需要重建整个面板
        rebuildMainPanel()
    }

    /**
     * 重置项目配置为系统配置
     */
    private fun resetProjectConfigToSystem() {
        val result = Messages.showYesNoDialog(
            "确定要将项目配置重置为系统配置吗？当前项目配置将被覆盖。",
            "确认重置",
            null
        )
        if (result == Messages.YES) {
            editingProjectState.commandBars = editingSystemState.deepCopy().commandBars
            refreshConfigPanel()
        }
    }

    /**
     * 重建主面板（用于启用/禁用项目配置后刷新 UI）
     */
    private lateinit var mainPanelRef: JPanel

    private fun rebuildMainPanel() {
        // 保存当前主面板的引用
        val parent = mainPanelRef.parent ?: return

        // 创建新的主面板
        val newMainPanel = JPanel(BorderLayout())

        // 添加项目配置控制区域
        if (project != null) {
            newMainPanel.add(createProjectConfigControlPanel(), BorderLayout.NORTH)
        }

        // 创建配置内容区域
        val contentPanel = if (isProjectConfigEnabled) {
            createTabbedConfigPanel()
        } else {
            createSingleConfigPanel()
        }
        newMainPanel.add(contentPanel, BorderLayout.CENTER)

        // 底部操作CommandBar
        newMainPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH)

        // 替换面板
        parent.layout = BorderLayout()
        parent.removeAll()
        parent.add(newMainPanel, BorderLayout.CENTER)
        parent.revalidate()
        parent.repaint()

        mainPanelRef = newMainPanel
    }

    /**
     * 创建空状态面板（未选中CommandBar时显示）
     */
    private fun createEmptyPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JLabel("请从左侧选择一个 CommandBar", SwingConstants.CENTER)
        label.foreground = com.intellij.ui.JBColor.GRAY
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建 Button 列表面板
     */
    private fun createButtonListPanel(): JComponent {
        buttonListModel = CollectionListModel(editingState.commandBars)
        buttonList = JBList(buttonListModel).apply {
            cellRenderer = ButtonListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { onButtonSelected() }
        }

        val panel = BorderLayoutPanel().withBorder(JBUI.Borders.empty(8))
        @Suppress("DEPRECATION")
        val decorator = com.intellij.ui.ToolbarDecorator.createDecorator(buttonList)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction { addButton() }
            .setRemoveAction { removeButton() }
            .setMoveUpAction { moveButtonUp() }
            .setMoveDownAction { moveButtonDown() }
            .addExtraAction(object : com.intellij.ui.AnActionButton("复制", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) = copyButton()
                override fun isEnabled(): Boolean = buttonList.selectedIndex >= 0
            })

        panel.addToCenter(decorator.createPanel())
        return panel
    }

    /**
     * 创建详情面板（右侧）
     */
    private fun createDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)

        // Button 详情
        val buttonDetailPanel = createButtonDetailPanel()

        // Option 列表和详情
        optionPanel = createOptionPanel()

        // 使用 BorderLayout 替代 JSplitPane，便于控制 optionPanel 的显示/隐藏
        panel.add(buttonDetailPanel, BorderLayout.NORTH)
        panel.add(optionPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建 Button 详情面板
     */
    private fun createButtonDetailPanel(): JComponent {
        val outerPanel = JPanel(BorderLayout())
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("CommandBar 详情")
        }

        // 启用复选框
        buttonEnabledCheckbox = JCheckBox("启用").apply {
            addActionListener {
                if (!ignoreUpdate) {
                    selectedCommandBar?.enabled = isSelected
                    buttonList.repaint()
                }
            }
        }

        // Name 字段 + 启用复选框（同一行）
        val namePanel = JPanel(BorderLayout())
        namePanel.add(JLabel("名称:"), BorderLayout.WEST)
        buttonNameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateButtonName()
                override fun removeUpdate(e: DocumentEvent?) = updateButtonName()
                override fun changedUpdate(e: DocumentEvent?) = updateButtonName()
            })
        }
        namePanel.add(buttonNameField, BorderLayout.CENTER)
        namePanel.add(buttonEnabledCheckbox, BorderLayout.EAST)
        panel.add(namePanel)

        // Icon 字段（包含输入框 + 内置图标下拉CommandBar + 文件浏览CommandBar）
        val iconPanel = JPanel(BorderLayout())
        iconPanel.add(JLabel("图标:"), BorderLayout.WEST)

        // 创建带文件浏览的输入框
        buttonIconField = TextFieldWithBrowseButton().apply {
            (textField as? JBTextField)?.emptyText?.text = "builtin:AllIcons.Actions.Execute"
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateButtonIcon()
                override fun removeUpdate(e: DocumentEvent?) = updateButtonIcon()
                override fun changedUpdate(e: DocumentEvent?) = updateButtonIcon()
            })
            // 文件浏览功能
            addBrowseFolderListener(
                "选择图标文件",
                "选择 SVG、PNG、ICO、JPG、GIF、BMP 图标文件",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter { file ->
                    val ext = file.extension?.lowercase()
                    ext in listOf("svg", "png", "ico", "jpg", "jpeg", "gif", "bmp")
                }
            )
        }

        // 创建内置图标选择下拉CommandBar
        val builtinIconBtn = JButton(AllIcons.General.ArrowDown).apply {
            toolTipText = "选择内置图标"
            isBorderPainted = true
            isFocusPainted = false
            margin = JBUI.insets(0, 2)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            // 设置为正方形
            val size = preferredSize.height
            preferredSize = Dimension(size, size)
            addActionListener {
                val popup = BuiltinIconSelector.createPopup(
                    onIconSelected = { iconPath ->
                        buttonIconField.text = iconPath
                    },
                    currentIconPath = buttonIconField.text
                )
                popup.showUnderneathOf(this)
            }
        }

        // 创建组合面板（输入框 + 下拉CommandBar）
        val iconFieldPanel = JPanel(BorderLayout()).apply {
            add(buttonIconField, BorderLayout.CENTER)
            add(builtinIconBtn, BorderLayout.EAST)
        }

        iconPanel.add(iconFieldPanel, BorderLayout.CENTER)
        panel.add(iconPanel)

        // Command 字段
        val commandPanel = JPanel(BorderLayout())
        commandPanel.add(JLabel("直接命令:"), BorderLayout.WEST)
        buttonCommandHintLabel = JLabel("输入直接命令后将不支持绑定Command 列表").apply {
            foreground = com.intellij.ui.JBColor.GRAY
        }
        buttonCommandField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateButtonCommand()
                    updateCommandHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateButtonCommand()
                    updateCommandHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateButtonCommand()
                    updateCommandHintVisibility()
                }
            })
        }
        commandPanel.add(buttonCommandField, BorderLayout.CENTER)
        panel.add(commandPanel)
        // 添加提示标签（单独一行，与输入框左对齐）
        val commandHintPanel = JPanel(BorderLayout())
        commandHintPanel.add(Box.createHorizontalStrut(JLabel("直接命令:").preferredSize.width), BorderLayout.WEST)
        commandHintPanel.add(buttonCommandHintLabel, BorderLayout.CENTER)
        panel.add(commandHintPanel)

        // Terminal Name 字段（仅直接命令模式时显示）— 紧跟图标后
        buttonTerminalNamePanel = JPanel(BorderLayout())
        buttonTerminalNamePanel.add(JLabel("默认终端窗口名称:"), BorderLayout.WEST)
        buttonTerminalNameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateButtonTerminalName()
                override fun removeUpdate(e: DocumentEvent?) = updateButtonTerminalName()
                override fun changedUpdate(e: DocumentEvent?) = updateButtonTerminalName()
            })
        }
        buttonTerminalNamePanel.add(buttonTerminalNameField, BorderLayout.CENTER)
        panel.add(buttonTerminalNamePanel)

        // Terminal Mode 字段（仅直接命令模式时显示）— 紧跟终端名称后
        buttonTerminalModePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val buttonTerminalModeRow = JPanel(BorderLayout())
        buttonTerminalModeCheckbox = JCheckBox("在编辑器中打开").apply {
            addActionListener {
                if (!ignoreUpdate) updateButtonTerminalMode()
            }
        }
        buttonTerminalModeRow.add(buttonTerminalModeCheckbox, BorderLayout.WEST)
        buttonTerminalModePanel.add(buttonTerminalModeRow)
        val buttonTerminalModeHintPanel = JPanel(BorderLayout())
        val buttonCheckboxSpacer = Box.createHorizontalStrut(JCheckBox().preferredSize.width)
        buttonTerminalModeHintPanel.add(buttonCheckboxSpacer, BorderLayout.WEST)
        buttonTerminalModeHintPanel.add(JLabel("默认通过终端工具窗口打开").apply {
            foreground = com.intellij.ui.JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
        buttonTerminalModePanel.add(buttonTerminalModeHintPanel)
        panel.add(buttonTerminalModePanel)

        // Working Directory 字段（仅直接命令模式时显示）
        buttonWorkingDirectoryPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val workDirPanel = JPanel(BorderLayout())
        workDirPanel.add(JLabel("工作目录:"), BorderLayout.WEST)
        val projectPath = getCurrentProjectPath()
        buttonWorkDirHintLabel = JLabel("留空时使用项目根路径").apply {
            foreground = com.intellij.ui.JBColor.GRAY
        }
        buttonWorkingDirectoryField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择工作目录",
                "选择终端的工作目录",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            // 设置 placeholder
            (textField as? JBTextField)?.emptyText?.text = projectPath ?: ""
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateButtonWorkingDirectory()
                    updateWorkDirHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateButtonWorkingDirectory()
                    updateWorkDirHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateButtonWorkingDirectory()
                    updateWorkDirHintVisibility()
                }
            })
        }
        workDirPanel.add(buttonWorkingDirectoryField, BorderLayout.CENTER)
        buttonWorkingDirectoryPanel.add(workDirPanel)
        // 添加提示标签（单独一行，与输入框左对齐）
        val workDirHintPanel = JPanel(BorderLayout())
        workDirHintPanel.add(Box.createHorizontalStrut(JLabel("工作目录:").preferredSize.width), BorderLayout.WEST)
        workDirHintPanel.add(buttonWorkDirHintLabel, BorderLayout.CENTER)
        buttonWorkingDirectoryPanel.add(workDirHintPanel)
        panel.add(buttonWorkingDirectoryPanel)

        // Environment Variables 字段（仅直接命令模式时显示）
        buttonEnvVariablesPanel = JPanel(BorderLayout())
        buttonEnvVariablesPanel.add(JLabel("环境变量:"), BorderLayout.WEST)
        val buttonEnvFieldPanel = JPanel(BorderLayout())
        buttonEnvVariablesField = JBTextField().apply {
            emptyText.text = "KEY1=val1;KEY2=val2"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateButtonEnvVariables()
                override fun removeUpdate(e: DocumentEvent?) = updateButtonEnvVariables()
                override fun changedUpdate(e: DocumentEvent?) = updateButtonEnvVariables()
            })
        }
        buttonEnvFieldPanel.add(buttonEnvVariablesField, BorderLayout.CENTER)
        val buttonEnvEditButton = JButton("...").apply {
            toolTipText = "编辑环境变量"
            addActionListener {
                val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                val dialog = EnvVariablesDialog(currentProject, buttonEnvVariablesField.text)
                if (dialog.showAndGet()) {
                    buttonEnvVariablesField.text = dialog.envVariablesText
                }
            }
        }
        buttonEnvFieldPanel.add(buttonEnvEditButton, BorderLayout.EAST)
        buttonEnvVariablesPanel.add(buttonEnvFieldPanel, BorderLayout.CENTER)
        panel.add(buttonEnvVariablesPanel)

        // 简易模式复选框（仅Command 列表模式时显示）
        simpleModePanel = JPanel(BorderLayout())
        simpleModeCheckbox = JCheckBox("简易模式（弹出菜单仅显示Command名称）").apply {
            addActionListener {
                if (!ignoreUpdate) updateSimpleMode()
            }
        }
        simpleModePanel.add(simpleModeCheckbox, BorderLayout.WEST)
        panel.add(simpleModePanel)

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 创建 Option 面板（列表 + 详情 + 快捷参数表格）
     */
    private fun createOptionPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // Option 列表
        optionListModel = CollectionListModel()
        optionList = JBList(optionListModel).apply {
            cellRenderer = OptionListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { onOptionSelected() }
        }

        val listPanel = BorderLayoutPanel()
        @Suppress("DEPRECATION")
        val decorator = ToolbarDecorator.createDecorator(optionList)
            .setAddActionName("添加")
            .setRemoveActionName("删除")
            .setMoveUpActionName("上移")
            .setMoveDownActionName("下移")
            .setAddAction { anActionButton ->
                // 点击时也显示气泡（兼容键盘操作），使用 AnActionButton 官方 API 获取CommandBar位置
                anActionButton.preferredPopupPoint.let { showAddOptionBalloon(it) }
            }
            .setRemoveAction { removeOption() }
            .setMoveUpAction { moveOptionUp() }
            .setMoveDownAction { moveOptionDown() }
            .addExtraAction(object : com.intellij.ui.AnActionButton("复制", AllIcons.Actions.Copy) {
                override fun actionPerformed(e: AnActionEvent) = copyOption()
                override fun isEnabled(): Boolean = optionList.selectedIndex >= 0
            })
        val decoratorPanel = decorator.createPanel()

        // 在添加CommandBar上设置鼠标悬浮监听
        setupAddButtonHoverListener(decoratorPanel)

        listPanel.addToCenter(decoratorPanel)

        // Option 详情（快捷参数已内嵌在详情面板中）
        optionDetailOuterPanel = createOptionDetailPanel()

        val splitter = OnePixelSplitter(false, 0.25f).apply {
            firstComponent = listPanel
            secondComponent = optionDetailOuterPanel
        }

        panel.add(splitter, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建 Option 详情面板
     */
    private fun createOptionDetailPanel(): JComponent {
        val outerPanel = JPanel(BorderLayout())
        optionDetailTitledBorder = BorderFactory.createTitledBorder("Command 详情")
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = optionDetailTitledBorder
        }

        // 启用复选框（仅普通 Command显示，分割线不显示）
        optionEnabledCheckbox = JCheckBox("启用").apply {
            addActionListener {
                if (!ignoreUpdate) {
                    selectedCommand?.enabled = isSelected
                    optionList.repaint()
                }
            }
        }
        optionEnabledPanel = optionEnabledCheckbox

        // Name（始终显示）+ 启用复选框（同一行）
        val namePanel = JPanel(BorderLayout())
        namePanel.add(JLabel("名称:"), BorderLayout.WEST)
        optionNameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateOptionName()
                override fun removeUpdate(e: DocumentEvent?) = updateOptionName()
                override fun changedUpdate(e: DocumentEvent?) = updateOptionName()
            })
        }
        namePanel.add(optionNameField, BorderLayout.CENTER)
        namePanel.add(optionEnabledCheckbox, BorderLayout.EAST)
        panel.add(namePanel)

        // Icon（仅普通 Command显示）
        optionIconPanel = JPanel(BorderLayout())
        optionIconPanel.add(JLabel("图标:"), BorderLayout.WEST)
        optionIconField = TextFieldWithBrowseButton().apply {
            (textField as? JBTextField)?.emptyText?.text = "builtin:AllIcons.Actions.Execute"
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateOptionIcon()
                override fun removeUpdate(e: DocumentEvent?) = updateOptionIcon()
                override fun changedUpdate(e: DocumentEvent?) = updateOptionIcon()
            })
            addBrowseFolderListener(
                "选择图标文件",
                "选择 SVG、PNG、ICO、JPG、GIF、BMP 图标文件",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor().withFileFilter { file ->
                    val ext = file.extension?.lowercase()
                    ext in listOf("svg", "png", "ico", "jpg", "jpeg", "gif", "bmp")
                }
            )
        }
        val optionBuiltinIconBtn = JButton(AllIcons.General.ArrowDown).apply {
            toolTipText = "选择内置图标"
            isBorderPainted = true
            isFocusPainted = false
            margin = JBUI.insets(0, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val size = preferredSize.height
            preferredSize = Dimension(size, size)
            addActionListener {
                val popup = BuiltinIconSelector.createPopup(
                    onIconSelected = { iconPath ->
                        optionIconField.text = iconPath
                    },
                    currentIconPath = optionIconField.text
                )
                popup.showUnderneathOf(this)
            }
        }
        val optionIconFieldPanel = JPanel(BorderLayout()).apply {
            add(optionIconField, BorderLayout.CENTER)
            add(optionBuiltinIconBtn, BorderLayout.EAST)
        }
        optionIconPanel.add(optionIconFieldPanel, BorderLayout.CENTER)
        panel.add(optionIconPanel)

        // Base Command（仅普通 Command显示）— 紧跟图标后
        optionCommandPanel = JPanel(BorderLayout())
        optionCommandPanel.add(JLabel("基础命令:"), BorderLayout.WEST)
        baseCommandField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateOptionCommand()
                override fun removeUpdate(e: DocumentEvent?) = updateOptionCommand()
                override fun changedUpdate(e: DocumentEvent?) = updateOptionCommand()
            })
        }
        optionCommandPanel.add(baseCommandField, BorderLayout.CENTER)
        panel.add(optionCommandPanel)

        // Default Terminal Name（仅普通 Command显示）— 紧跟图标后
        optionTerminalNamePanel = JPanel(BorderLayout())
        optionTerminalNamePanel.add(JLabel("默认终端窗口名称:"), BorderLayout.WEST)
        defaultTerminalNameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateOptionTerminalName()
                override fun removeUpdate(e: DocumentEvent?) = updateOptionTerminalName()
                override fun changedUpdate(e: DocumentEvent?) = updateOptionTerminalName()
            })
        }
        optionTerminalNamePanel.add(defaultTerminalNameField, BorderLayout.CENTER)
        panel.add(optionTerminalNamePanel)

        // Terminal Mode（仅普通 Command显示）— 紧跟终端名称后
        optionTerminalModePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val optionTerminalModeRow = JPanel(BorderLayout())
        optionTerminalModeCheckbox = JCheckBox("在编辑器中打开").apply {
            addActionListener {
                if (!ignoreUpdate) updateOptionTerminalMode()
            }
        }
        optionTerminalModeRow.add(optionTerminalModeCheckbox, BorderLayout.WEST)
        optionTerminalModePanel.add(optionTerminalModeRow)
        val optionTerminalModeHintPanel = JPanel(BorderLayout())
        val optionCheckboxSpacer = Box.createHorizontalStrut(JCheckBox().preferredSize.width)
        optionTerminalModeHintPanel.add(optionCheckboxSpacer, BorderLayout.WEST)
        optionTerminalModeHintPanel.add(JLabel("默认通过终端工具窗口打开").apply {
            foreground = com.intellij.ui.JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
        optionTerminalModePanel.add(optionTerminalModeHintPanel)
        panel.add(optionTerminalModePanel)

        // Working Directory（仅普通 Command显示）
        optionDirPanel = JPanel(BorderLayout())
        optionDirPanel.add(JLabel("工作目录:"), BorderLayout.WEST)
        val optionProjectPath = getCurrentProjectPath()
        optionWorkDirHintLabel = JLabel("留空时使用项目根路径").apply {
            foreground = com.intellij.ui.JBColor.GRAY
        }
        workingDirectoryField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择工作目录",
                "选择终端的工作目录",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            // 设置 placeholder
            (textField as? JBTextField)?.emptyText?.text = optionProjectPath ?: ""
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateOptionDirectory()
                    updateOptionWorkDirHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateOptionDirectory()
                    updateOptionWorkDirHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateOptionDirectory()
                    updateOptionWorkDirHintVisibility()
                }
            })
        }
        optionDirPanel.add(workingDirectoryField, BorderLayout.CENTER)
        panel.add(optionDirPanel)
        // 添加提示标签（单独一行，与输入框左对齐）
        optionDirHintPanel = JPanel(BorderLayout())
        optionDirHintPanel.add(Box.createHorizontalStrut(JLabel("工作目录:").preferredSize.width), BorderLayout.WEST)
        optionDirHintPanel.add(optionWorkDirHintLabel, BorderLayout.CENTER)
        panel.add(optionDirHintPanel)

        // Environment Variables（仅普通 Command显示）
        optionEnvVariablesPanel = JPanel(BorderLayout())
        optionEnvVariablesPanel.add(JLabel("环境变量:"), BorderLayout.WEST)
        val optionEnvFieldPanel = JPanel(BorderLayout())
        optionEnvVariablesField = JBTextField().apply {
            emptyText.text = "KEY1=val1;KEY2=val2"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateOptionEnvVariables()
                override fun removeUpdate(e: DocumentEvent?) = updateOptionEnvVariables()
                override fun changedUpdate(e: DocumentEvent?) = updateOptionEnvVariables()
            })
        }
        optionEnvFieldPanel.add(optionEnvVariablesField, BorderLayout.CENTER)
        val optionEnvEditButton = JButton("...").apply {
            toolTipText = "编辑环境变量"
            addActionListener {
                val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                val dialog = EnvVariablesDialog(currentProject, optionEnvVariablesField.text)
                if (dialog.showAndGet()) {
                    optionEnvVariablesField.text = dialog.envVariablesText
                }
            }
        }
        optionEnvFieldPanel.add(optionEnvEditButton, BorderLayout.EAST)
        optionEnvVariablesPanel.add(optionEnvFieldPanel, BorderLayout.CENTER)
        panel.add(optionEnvVariablesPanel)

        // QuickParam 列表（单行：标签 + 只读摘要 + 编辑CommandBar）
        quickParamOuterPanel = createQuickParamPanel()
        panel.add(quickParamOuterPanel)

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 创建 快捷参数表格面板
     */
    private fun createQuickParamPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        panel.add(JLabel("快捷参数列表:"), BorderLayout.WEST)

        quickParamSummaryField = JBTextField().apply {
            isEditable = false
        }
        panel.add(quickParamSummaryField, BorderLayout.CENTER)

        val editButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = "编辑快捷参数列表"
            addActionListener { openQuickParamEditDialog() }
        }
        panel.add(editButton, BorderLayout.EAST)

        return panel
    }

    /**
     * 创建操作CommandBar面板（Import/Export/Reset）
     * 系统配置：导入、导出
     * 项目配置：导入、导出、重置为系统配置
     */
    private lateinit var actionButtonsPanelRef: JPanel

    private fun createActionButtonsPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
        }
        actionButtonsPanelRef = panel

        panel.add(Box.createHorizontalGlue())

        val importButton = JButton("导入")
        importButton.addActionListener { importConfig() }
        panel.add(importButton)

        panel.add(Box.createHorizontalStrut(JBUI.scale(8)))

        val exportButton = JButton("导出")
        exportButton.addActionListener { exportConfig() }
        panel.add(exportButton)

        // 仅项目配置模式下显示"重置为系统配置"CommandBar
        if (project != null && currentConfigMode == ConfigMode.PROJECT) {
            panel.add(Box.createHorizontalStrut(JBUI.scale(8)))

            val resetButton = JButton("重置为系统配置")
            resetButton.addActionListener { resetProjectConfigToSystem() }
            panel.add(resetButton)
        }

        return panel
    }

    /**
     * 更新底部CommandBar面板
     */
    private fun updateActionButtonsPanel() {
        if (!::actionButtonsPanelRef.isInitialized) return

        val parent = actionButtonsPanelRef.parent ?: return

        // 创建新的CommandBar面板
        val newPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
        }

        newPanel.add(Box.createHorizontalGlue())

        val importButton = JButton("导入")
        importButton.addActionListener { importConfig() }
        newPanel.add(importButton)

        newPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))

        val exportButton = JButton("导出")
        exportButton.addActionListener { exportConfig() }
        newPanel.add(exportButton)

        // 仅项目配置模式下显示"重置为系统配置"CommandBar
        if (project != null && currentConfigMode == ConfigMode.PROJECT) {
            newPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))

            val resetButton = JButton("重置为系统配置")
            resetButton.addActionListener { resetProjectConfigToSystem() }
            newPanel.add(resetButton)
        }

        // 替换面板
        parent.remove(actionButtonsPanelRef)
        parent.add(newPanel, BorderLayout.SOUTH)
        parent.revalidate()
        parent.repaint()

        actionButtonsPanelRef = newPanel
    }

    // ==================== Button 列表操作 ====================

    private fun addButton() {
        val newButton = CommandBarConfig(
            id = UUID.randomUUID().toString(),
            name = "New CommandBar",
            icon = "builtin:AllIcons.Actions.Execute"
        )
        buttonListModel.add(newButton)
        editingState.commandBars.add(newButton)
        buttonList.selectedIndex = buttonListModel.size - 1
    }

    private fun removeButton() {
        val index = buttonList.selectedIndex
        if (index >= 0) {
            val result = Messages.showYesNoDialog(
                "确定要删除 CommandBar '${buttonListModel.getElementAt(index).name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                buttonListModel.remove(index)
                editingState.commandBars.removeAt(index)
                selectedCommandBar = null
                clearButtonDetail()
            }
        }
    }

    private fun moveButtonUp() {
        val index = buttonList.selectedIndex
        if (index > 0) {
            Collections.swap(editingState.commandBars, index, index - 1)
            val item = buttonListModel.getElementAt(index)
            buttonListModel.remove(index)
            buttonListModel.add(index - 1, item)
            buttonList.selectedIndex = index - 1
        }
    }

    private fun moveButtonDown() {
        val index = buttonList.selectedIndex
        if (index < buttonListModel.size - 1) {
            Collections.swap(editingState.commandBars, index, index + 1)
            val item = buttonListModel.getElementAt(index)
            buttonListModel.remove(index)
            buttonListModel.add(index + 1, item)
            buttonList.selectedIndex = index + 1
        }
    }

    private fun copyButton() {
        val index = buttonList.selectedIndex
        if (index < 0) return
        val source = buttonListModel.getElementAt(index)
        val copy = source.deepCopy().apply {
            id = UUID.randomUUID().toString()
            name = source.name + "-copy"
            commands.forEach { it.id = UUID.randomUUID().toString(); it.quickParams.forEach { sb -> sb.id = UUID.randomUUID().toString() } }
        }
        val insertIndex = index + 1
        editingState.commandBars.add(insertIndex, copy)
        buttonListModel.add(insertIndex, copy)
        buttonList.selectedIndex = insertIndex
    }

    private fun onButtonSelected() {
        if (ignoreUpdate) return

        val index = buttonList.selectedIndex
        if (index >= 0) {
            selectedCommandBar = buttonListModel.getElementAt(index)
            updateButtonDetail()
            updateOptionList()
            showDetailPanel()
        } else {
            selectedCommandBar = null
            clearButtonDetail()
            optionListModel.removeAll()
            showEmptyPanel()
        }
    }

    private fun showEmptyPanel() {
        val layout = rightContainer.layout as CardLayout
        layout.show(rightContainer, CARD_EMPTY)
    }

    private fun showDetailPanel() {
        val layout = rightContainer.layout as CardLayout
        layout.show(rightContainer, CARD_DETAIL)
    }

    private fun updateButtonDetail() {
        ignoreUpdate = true
        try {
            val button = selectedCommandBar ?: return
            buttonEnabledCheckbox.isSelected = button.enabled
            buttonNameField.text = button.name
            buttonIconField.text = button.icon
            buttonCommandField.text = button.command
            buttonEnvVariablesField.text = button.envVariables
            buttonWorkingDirectoryField.text = button.workingDirectory
            buttonTerminalNameField.text = button.defaultTerminalName
            buttonTerminalModeCheckbox.isSelected = button.terminalMode == TerminalMode.EDITOR
            simpleModeCheckbox.isSelected = button.simpleMode
            // 更新直接命令模式相关字段的显示状态
            updateDirectCommandModeVisibility()
            // 更新提示文字状态
            updateCommandHintVisibility()
            updateWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun clearButtonDetail() {
        ignoreUpdate = true
        try {
            buttonEnabledCheckbox.isSelected = true
            buttonNameField.text = ""
            buttonIconField.text = ""
            buttonCommandField.text = ""
            buttonEnvVariablesField.text = ""
            buttonWorkingDirectoryField.text = ""
            buttonTerminalNameField.text = ""
            buttonTerminalModeCheckbox.isSelected = false
            simpleModeCheckbox.isSelected = false
            // 重置提示文字状态
            updateCommandHintVisibility()
            updateWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun updateButtonName() {
        if (ignoreUpdate) return
        selectedCommandBar?.name = buttonNameField.text
        buttonList.repaint()
    }

    private fun updateButtonIcon() {
        if (ignoreUpdate) return
        val text = buttonIconField.text
        selectedCommandBar?.icon = if (text.isBlank()) "builtin:AllIcons.Actions.Execute" else text
        // 同步更新列表中的图标显示
        buttonList.repaint()
    }

    private fun updateButtonCommand() {
        if (ignoreUpdate) return
        selectedCommandBar?.command = buttonCommandField.text
        // 切换直接命令模式相关字段的显示状态
        updateDirectCommandModeVisibility()
    }

    private fun updateButtonWorkingDirectory() {
        if (ignoreUpdate) return
        selectedCommandBar?.workingDirectory = buttonWorkingDirectoryField.text
    }

    private fun updateButtonEnvVariables() {
        if (ignoreUpdate) return
        selectedCommandBar?.envVariables = buttonEnvVariablesField.text
    }

    private fun updateButtonTerminalName() {
        if (ignoreUpdate) return
        selectedCommandBar?.defaultTerminalName = buttonTerminalNameField.text
    }

    private fun updateButtonTerminalMode() {
        if (ignoreUpdate) return
        selectedCommandBar?.terminalMode = if (buttonTerminalModeCheckbox.isSelected) TerminalMode.EDITOR else TerminalMode.TOOL_WINDOW
    }

    private fun updateSimpleMode() {
        if (ignoreUpdate) return
        selectedCommandBar?.simpleMode = simpleModeCheckbox.isSelected
        updateSimpleModeVisibility()
    }

    /**
     * 更新直接命令提示文字的显示状态
     */
    private fun updateCommandHintVisibility() {
        buttonCommandHintLabel.text = if (buttonCommandField.text.isBlank()) {
            "输入直接命令后将不支持绑定Command 列表"
        } else {
            ""
        }
    }

    /**
     * 更新工作目录提示文字的显示状态
     */
    private fun updateWorkDirHintVisibility() {
        buttonWorkDirHintLabel.text = if (buttonWorkingDirectoryField.text.isBlank()) {
            "留空时使用项目根路径"
        } else {
            ""
        }
    }

    /**
     * 更新 Option 工作目录提示文字的显示状态
     */
    private fun updateOptionWorkDirHintVisibility() {
        optionWorkDirHintLabel.text = if (workingDirectoryField.text.isBlank()) {
            "留空时使用项目根路径"
        } else {
            ""
        }
    }

    /**
     * 更新直接命令模式相关字段的显示状态
     * - 终端名称字段：仅在直接命令模式下显示
     * - Options 面板：仅在Command 列表模式下显示
     */
    private fun updateDirectCommandModeVisibility() {
        val isDirectMode = selectedCommandBar?.isDirectCommandMode() == true
        buttonEnvVariablesPanel.isVisible = isDirectMode
        buttonWorkingDirectoryPanel.isVisible = isDirectMode
        buttonTerminalNamePanel.isVisible = isDirectMode
        buttonTerminalModePanel.isVisible = isDirectMode
        simpleModePanel.isVisible = !isDirectMode
        optionPanel.isVisible = !isDirectMode
        if (!isDirectMode) {
            updateSimpleModeVisibility()
        }
    }

    /**
     * 更新简易模式下快捷参数面板的显示状态
     */
    private fun updateSimpleModeVisibility() {
        val isSimple = selectedCommandBar?.simpleMode == true
        quickParamOuterPanel.isVisible = !isSimple
    }

    // ==================== Option 列表操作 ====================

    private fun updateOptionList() {
        optionListModel.removeAll()
        selectedCommandBar?.commands?.let { commands ->
            for (option in commands) {
                optionListModel.add(option)
            }
            if (commands.isNotEmpty()) {
                optionList.selectedIndex = 0
            } else {
                selectedCommand = null
                clearOptionDetail()
                updateQuickParamSummary()
            }
        }
    }

    private fun addOption() {
        val button = selectedCommandBar ?: return
        val newOption = CommandConfig(
            id = UUID.randomUUID().toString(),
            name = "New Command",
            baseCommand = "",
            defaultTerminalName = ""
        )
        button.commands.add(newOption)
        optionListModel.add(newOption)
        optionList.selectedIndex = optionListModel.size - 1
    }

    /**
     * 延迟到面板显示后，在 ActionToolbar 中找到添加CommandBar的视觉组件并绑定鼠标悬浮监听。
     * ActionToolbar 的子组件（ActionButton）是延迟创建的，
     * 必须等面板显示后再查找，否则组件尚不存在。
     */
    private fun setupAddButtonHoverListener(decoratorPanel: JPanel) {
        decoratorPanel.addHierarchyListener {
            if (addOptionButtonRef != null || !decoratorPanel.isShowing) return@addHierarchyListener
            SwingUtilities.invokeLater { findAndBindAddButton(decoratorPanel) }
        }
    }

    /**
     * 通过 IntelliJ 平台 API 在 ActionToolbar 中精确匹配添加CommandBar的视觉组件，
     * 并为其绑定鼠标悬浮事件。
     */
    private fun findAndBindAddButton(decoratorPanel: JPanel) {
        if (addOptionButtonRef != null) return

        // 1. 通过 ToolbarDecorator 官方 API 获取 Add 对应的 AnActionButton（Action 对象）
        val addAction = ToolbarDecorator.findAddButton(decoratorPanel) ?: return

        // 2. 找到 CommonActionsPanel，获取其内部的 ActionToolbar
        val actionsPanel = UIUtil.findComponentOfType(decoratorPanel, CommonActionsPanel::class.java) ?: return
        val toolbarComp = actionsPanel.toolbar.component

        // 3. 遍历 toolbar 子组件，匹配持有 Add Action 的视觉CommandBar
        for (comp in toolbarComp.components) {
            if (comp !is JComponent || comp !is AnActionHolder) continue
            val action = (comp as AnActionHolder).action
            if (action == addAction ||
                (action is ActionWithDelegate<*> && action.delegate == addAction)) {
                addOptionButtonRef = comp
                // 禁用CommandBar的 tooltip，避免与气泡弹窗冲突
                suppressActionButtonTooltip(comp)
                comp.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) {
                        cancelPopupClose()
                        // ActionToolbar 更新时会重新安装 HelpTooltip，需要每次进入时再次清除
                        suppressActionButtonTooltip(comp)
                        val pt = RelativePoint(comp.parent, Point(comp.x, comp.y + comp.height))
                        showAddOptionBalloon(pt)
                    }
                    override fun mouseExited(e: MouseEvent) {
                        schedulePopupClose()
                    }
                })
                return
            }
        }
    }

    /**
     * 显示添加 Command的气泡弹窗
     */
    private fun showAddOptionBalloon(point: RelativePoint) {
        // 如果已有弹窗正在显示，不重复创建
        if (addOptionPopup?.isVisible == true) return

        // 气泡内鼠标进出监听：进入取消关闭计时，离开启动关闭计时
        val hoverListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = cancelPopupClose()
            override fun mouseExited(e: MouseEvent) = schedulePopupClose()
        }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2)
            addMouseListener(hoverListener)

            add(createBalloonItem("添加 Command") {
                addOptionPopup?.cancel()
                addOption()
            }.also {
                it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height)
                it.addMouseListener(hoverListener)
            })
            add(createBalloonItem("添加分割线") {
                addOptionPopup?.cancel()
                addSeparator()
            }.also {
                it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height)
                it.addMouseListener(hoverListener)
            })
        }

        addOptionPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, null)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setBorderColor(java.awt.Color.GRAY)
            .createPopup()

        addOptionPopup?.show(point)
    }

    /**
     * 创建气泡弹窗中的可点击项
     */
    private fun createBalloonItem(text: String, onClick: () -> Unit): JLabel {
        return JLabel(text).apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(6, 12)
            isOpaque = true
            background = UIManager.getColor("Panel.background")
            alignmentX = 0.0f
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    onClick()
                }
                override fun mouseEntered(e: MouseEvent) {
                    background = UIManager.getColor("List.selectionBackground")
                    foreground = UIManager.getColor("List.selectionForeground")
                }
                override fun mouseExited(e: MouseEvent) {
                    background = UIManager.getColor("Panel.background")
                    foreground = UIManager.getColor("Label.foreground")
                }
            })
        }
    }

    /**
     * 延迟关闭气泡弹窗（给用户从CommandBar移动到气泡的时间）
     */
    private fun schedulePopupClose() {
        popupCloseTimer?.stop()
        popupCloseTimer = javax.swing.Timer(300) {
            addOptionPopup?.cancel()
        }.apply { isRepeats = false; start() }
    }

    /**
     * 取消气泡弹窗的延迟关闭
     */
    private fun cancelPopupClose() {
        popupCloseTimer?.stop()
    }

    /**
     * 清除 ActionButton 上的 tooltip（包括 IntelliJ 的 HelpTooltip 和 Swing 标准 tooltip）
     */
    private fun suppressActionButtonTooltip(comp: JComponent) {
        comp.toolTipText = null
        ToolTipManager.sharedInstance().unregisterComponent(comp)
        com.intellij.ide.HelpTooltip.dispose(comp)
    }

    /**
     * 添加分割线
     */
    private fun addSeparator() {
        val button = selectedCommandBar ?: return
        val newSeparator = CommandConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            type = CommandType.SEPARATOR
        )
        button.commands.add(newSeparator)
        optionListModel.add(newSeparator)
        optionList.selectedIndex = optionListModel.size - 1
    }

    private fun removeOption() {
        val button = selectedCommandBar ?: return
        val index = optionList.selectedIndex
        if (index >= 0) {
            val option = optionListModel.getElementAt(index)
            val typeName = if (option.isSeparator()) "分割线" else "Command"
            val result = Messages.showYesNoDialog(
                "确定要删除${typeName} '${option.name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                button.commands.removeAt(index)
                optionListModel.remove(index)
                selectedCommand = null
                clearOptionDetail()
                updateQuickParamSummary()
            }
        }
    }

    private fun moveOptionUp() {
        val button = selectedCommandBar ?: return
        val index = optionList.selectedIndex
        if (index > 0) {
            Collections.swap(button.commands, index, index - 1)
            val item = optionListModel.getElementAt(index)
            optionListModel.remove(index)
            optionListModel.add(index - 1, item)
            optionList.selectedIndex = index - 1
        }
    }

    private fun moveOptionDown() {
        val button = selectedCommandBar ?: return
        val index = optionList.selectedIndex
        if (index < optionListModel.size - 1) {
            Collections.swap(button.commands, index, index + 1)
            val item = optionListModel.getElementAt(index)
            optionListModel.remove(index)
            optionListModel.add(index + 1, item)
            optionList.selectedIndex = index + 1
        }
    }

    private fun copyOption() {
        val button = selectedCommandBar ?: return
        val index = optionList.selectedIndex
        if (index < 0) return
        val source = optionListModel.getElementAt(index)
        val copy = source.deepCopy().apply {
            id = UUID.randomUUID().toString()
            name = source.name + "-copy"
            quickParams.forEach { sb -> sb.id = UUID.randomUUID().toString() }
        }
        val insertIndex = index + 1
        button.commands.add(insertIndex, copy)
        optionListModel.add(insertIndex, copy)
        optionList.selectedIndex = insertIndex
    }

    private fun onOptionSelected() {
        if (ignoreUpdate) return

        val index = optionList.selectedIndex
        if (index >= 0 && selectedCommandBar != null) {
            selectedCommand = selectedCommandBar!!.commands[index]

            // 先更新表单数据
            updateOptionDetail()

            if (selectedCommand!!.isSeparator()) {
                // 分割线：隐藏不需要的字段
                hideOptionDetailForSeparator()
            } else {
                // 普通 Command：显示所有字段
                showOptionDetail()
                updateQuickParamSummary()
            }
        } else {
            selectedCommand = null
            clearOptionDetail()
            updateQuickParamSummary()
            showOptionDetail()
        }
    }

    /**
     * 隐藏分割线选中时的详情面板（只显示名称字段）
     */
    private fun hideOptionDetailForSeparator() {
        // 只在需要时更新可见性
        if (!optionDetailOuterPanel.isVisible) {
            optionDetailOuterPanel.isVisible = true
        }
        if (optionEnabledPanel.isVisible) {
            optionEnabledPanel.isVisible = false
        }
        if (optionIconPanel.isVisible) {
            optionIconPanel.isVisible = false
        }
        if (optionCommandPanel.isVisible) {
            optionCommandPanel.isVisible = false
        }
        if (optionEnvVariablesPanel.isVisible) {
            optionEnvVariablesPanel.isVisible = false
        }
        if (optionDirPanel.isVisible) {
            optionDirPanel.isVisible = false
        }
        if (optionDirHintPanel.isVisible) {
            optionDirHintPanel.isVisible = false
        }
        if (optionTerminalNamePanel.isVisible) {
            optionTerminalNamePanel.isVisible = false
        }
        if (optionTerminalModePanel.isVisible) {
            optionTerminalModePanel.isVisible = false
        }
        if (quickParamOuterPanel.isVisible) {
            quickParamOuterPanel.isVisible = false
        }
        // 更新标题
        optionDetailTitledBorder.title = "分割线详情"
    }

    /**
     * 显示普通 Command的详情面板（显示所有字段）
     */
    private fun showOptionDetail() {
        // 只在需要时更新可见性，避免不必要的面板刷新
        if (!optionDetailOuterPanel.isVisible) {
            optionDetailOuterPanel.isVisible = true
        }
        if (!optionEnabledPanel.isVisible) {
            optionEnabledPanel.isVisible = true
        }
        if (!optionIconPanel.isVisible) {
            optionIconPanel.isVisible = true
        }
        if (!optionCommandPanel.isVisible) {
            optionCommandPanel.isVisible = true
        }
        if (!optionEnvVariablesPanel.isVisible) {
            optionEnvVariablesPanel.isVisible = true
        }
        if (!optionDirPanel.isVisible) {
            optionDirPanel.isVisible = true
        }
        if (!optionDirHintPanel.isVisible) {
            optionDirHintPanel.isVisible = true
        }
        if (!optionTerminalNamePanel.isVisible) {
            optionTerminalNamePanel.isVisible = true
        }
        if (!optionTerminalModePanel.isVisible) {
            optionTerminalModePanel.isVisible = true
        }
        // 简易模式下隐藏快捷参数面板
        val shouldShowQuickParams = selectedCommandBar?.simpleMode != true
        if (quickParamOuterPanel.isVisible != shouldShowQuickParams) {
            quickParamOuterPanel.isVisible = shouldShowQuickParams
        }
        // 更新标题
        optionDetailTitledBorder.title = "Command 详情"
    }

    private fun updateOptionDetail() {
        ignoreUpdate = true
        try {
            val option = selectedCommand ?: return
            optionNameField.text = option.name
            optionEnabledCheckbox.isSelected = option.enabled
            optionIconField.text = option.icon
            baseCommandField.text = option.baseCommand
            optionEnvVariablesField.text = option.envVariables
            workingDirectoryField.text = option.workingDirectory
            defaultTerminalNameField.text = option.defaultTerminalName
            optionTerminalModeCheckbox.isSelected = option.terminalMode == TerminalMode.EDITOR
            // 更新提示文字状态
            updateOptionWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun clearOptionDetail() {
        ignoreUpdate = true
        try {
            optionNameField.text = ""
            optionEnabledCheckbox.isSelected = true
            optionIconField.text = ""
            baseCommandField.text = ""
            optionEnvVariablesField.text = ""
            workingDirectoryField.text = ""
            defaultTerminalNameField.text = ""
            optionTerminalModeCheckbox.isSelected = false
            // 重置提示文字状态
            updateOptionWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun updateOptionName() {
        if (ignoreUpdate) return
        selectedCommand?.name = optionNameField.text
        optionList.repaint()
    }

    private fun updateOptionIcon() {
        if (ignoreUpdate) return
        val text = optionIconField.text
        selectedCommand?.icon = if (text.isBlank()) "builtin:AllIcons.Actions.Execute" else text
        optionList.repaint()
    }

    private fun updateOptionCommand() {
        if (ignoreUpdate) return
        selectedCommand?.baseCommand = baseCommandField.text
    }

    private fun updateOptionDirectory() {
        if (ignoreUpdate) return
        selectedCommand?.workingDirectory = workingDirectoryField.text
    }

    private fun updateOptionEnvVariables() {
        if (ignoreUpdate) return
        selectedCommand?.envVariables = optionEnvVariablesField.text
    }

    private fun updateOptionTerminalName() {
        if (ignoreUpdate) return
        selectedCommand?.defaultTerminalName = defaultTerminalNameField.text
    }

    private fun updateOptionTerminalMode() {
        if (ignoreUpdate) return
        selectedCommand?.terminalMode = if (optionTerminalModeCheckbox.isSelected) TerminalMode.EDITOR else TerminalMode.TOOL_WINDOW
    }

    // ==================== QuickParam 操作 ====================

    private fun updateQuickParamSummary() {
        if (::quickParamSummaryField.isInitialized) {
            val names = selectedCommand?.quickParams?.filter { it.enabled }?.map { it.name } ?: emptyList()
            quickParamSummaryField.text = if (names.isEmpty()) "" else names.joinToString(" | ")
        }
    }

    private fun openQuickParamEditDialog() {
        val option = selectedCommand ?: return
        val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        val deepCopy = option.quickParams.map { it.deepCopy() }
        val dialog = QuickParamEditDialog(currentProject, deepCopy)
        if (dialog.showAndGet()) {
            val edited = dialog.getEditedQuickParams()
            option.quickParams.clear()
            option.quickParams.addAll(edited)
            updateQuickParamSummary()
        }
    }

    // ==================== 配置操作 ====================

    private fun importConfig() {
        val fileChooser = FileChooserDescriptorFactory
            .createSingleFileDescriptor("json")
            .withTitle("导入配置")
            .withDescription("选择要导入的 JSON 配置文件")
        // 强制使用 IntelliJ 内置文件选择器，避免 macOS 原生对话框在模态窗口中的焦点问题
        fileChooser.isForcedToUseIdeaFileChooser = true

        val file = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            fileChooser,
            mainPanelRef,
            project,
            null
        )

        if (file != null) {
            try {
                val content = String(file.contentsToByteArray(), Charsets.UTF_8)
                val gson = com.google.gson.Gson()

                // 解析 JSON
                val importedButtons = gson.fromJson(content, Array<CommandBarConfig>::class.java)

                if (importedButtons != null && importedButtons.isNotEmpty()) {
                    val result = Messages.showYesNoDialog(
                        "导入将覆盖当前所有配置，是否继续？",
                        "确认导入",
                        null
                    )
                    if (result == Messages.YES) {
                        // 直接修改对应的配置状态，而不是通过 editingState getter（项目配置模式下会返回临时对象）
                        val importedList = importedButtons.toMutableList()
                        if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
                            editingProjectState.commandBars = importedList
                        } else {
                            editingSystemState.commandBars = importedList
                        }

                        buttonListModel.removeAll()
                        for (btn in importedList) {
                            buttonListModel.add(btn)
                        }
                        selectedCommandBar = null
                        selectedCommand = null
                        clearButtonDetail()
                        clearOptionDetail()
                        optionListModel.removeAll()
                        updateQuickParamSummary()

                        Messages.showInfoMessage("配置导入成功！", "成功")
                    }
                } else {
                    Messages.showErrorDialog("配置文件为空或格式无效", "导入失败")
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "解析配置文件失败: ${e.message}",
                    "导入失败"
                )
            }
        }
    }

    private fun exportConfig() {
        val fileSaverDescriptor = com.intellij.openapi.fileChooser.FileSaverDescriptor(
            "导出配置",
            "选择保存位置",
            "json"
        )
        // 强制使用 IntelliJ 内置文件选择器，避免 macOS 原生对话框在模态窗口中的焦点问题
        fileSaverDescriptor.isForcedToUseIdeaFileChooser = true

        val dialog = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createSaveFileDialog(fileSaverDescriptor, mainPanelRef)

        val result = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "ccbar-config.json")

        if (result != null) {
            try {
                val gson = com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()

                val json = gson.toJson(editingState.commandBars)

                result.file.writeText(json, Charsets.UTF_8)

                Messages.showInfoMessage("配置已导出到: ${result.file.absolutePath}", "导出成功")
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    "导出配置失败: ${e.message}",
                    "导出失败"
                )
            }
        }
    }

    private fun resetConfig() {
        val result = Messages.showYesNoDialog(
            "确定要重置为默认配置吗？当前修改将丢失。",
            "确认重置",
            null
        )
        if (result == Messages.YES) {
            // 直接修改对应的配置状态，而不是通过 editingState getter（项目配置模式下会返回临时对象）
            val defaultButtons = CCBarSettings.createDefaultCommandBars()
            if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
                editingProjectState.commandBars = defaultButtons
            } else {
                editingSystemState.commandBars = defaultButtons
            }

            buttonListModel.removeAll()
            for (btn in defaultButtons) {
                buttonListModel.add(btn)
            }
            selectedCommandBar = null
            selectedCommand = null
            clearButtonDetail()
            clearOptionDetail()
            optionListModel.removeAll()
            updateQuickParamSummary()
        }
    }

    // ==================== Configurable 接口实现 ====================

    fun isModified(): Boolean {
        // 检查系统配置是否修改
        val systemSettings = CCBarSettings.getInstance()
        val systemModified = editingSystemState.commandBars != systemSettings.state.commandBars

        // 检查项目配置是否修改（如果有项目）
        var projectModified = false
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            projectModified = editingProjectState != projectSettings.state
        }

        return systemModified || projectModified
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        for ((buttonIndex, button) in editingState.commandBars.withIndex()) {
            if (button.name.isBlank()) {
                errors.add("CommandBar ${buttonIndex + 1}: 名称不能为空")
            }
            if (editingState.commandBars.count { it.name == button.name } > 1) {
                errors.add("CommandBar '${button.name}': 名称重复")
            }

            // 禁用的 CommandBar 跳过内容验证
            if (!button.enabled) continue

            // 直接命令模式验证
            if (button.isDirectCommandMode()) {
                if (button.defaultTerminalName.isBlank()) {
                    errors.add("CommandBar '${button.name}': 直接命令模式下，终端名称不能为空")
                }
                // 直接命令模式下不验证 Options
            } else {
                // Command 列表模式验证
                // 过滤掉分割线，只计算启用的普通 Command
                val enabledNormalOptions = button.commands.filter { !it.isSeparator() && it.enabled }
                if (enabledNormalOptions.isEmpty()) {
                    errors.add("CommandBar '${button.name}': 未配置直接命令时，必须至少有一个启用的普通 Command")
                }

                for ((optionIndex, option) in button.commands.withIndex()) {
                    // 跳过分割线类型的验证
                    if (option.isSeparator()) continue

                    // 禁用的 Command 跳过必填字段验证
                    if (!option.enabled) continue

                    if (option.name.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command ${optionIndex + 1}: 名称不能为空")
                    }
                    // 只检查启用的普通 Command的名称重复
                    if (enabledNormalOptions.count { it.name == option.name } > 1) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 名称重复")
                    }
                    if (option.baseCommand.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 基础命令不能为空")
                    }
                    if (option.defaultTerminalName.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 终端名称不能为空")
                    }

                    for ((quickParamIndex, quickParam) in option.quickParams.withIndex()) {
                        // 禁用的 QuickParam 跳过验证
                        if (!quickParam.enabled) continue

                        if (quickParam.name.isBlank()) {
                            errors.add("CommandBar '${button.name}' Command '${option.name}' QuickParam ${quickParamIndex + 1}: 名称不能为空")
                        }
                        if (option.quickParams.filter { it.enabled }.count { it.name == quickParam.name } > 1) {
                            errors.add("CommandBar '${button.name}' Command '${option.name}' QuickParam '${quickParam.name}': 名称重复")
                        }
                    }
                }
            }
        }

        return errors
    }

    fun apply() {
        // 保存系统配置
        val systemSettings = CCBarSettings.getInstance()
        systemSettings.loadState(editingSystemState.deepCopy())

        // 保存项目配置（如果有项目）
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            projectSettings.loadState(editingProjectState.deepCopy())
        }
    }

    fun reset() {
        // 重置系统配置（深拷贝，确保编辑状态和原始状态独立）
        val systemSettings = CCBarSettings.getInstance()
        editingSystemState = systemSettings.state.deepCopy()

        // 重置项目配置（如果有项目）
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            editingProjectState = projectSettings.state.deepCopy()
            isProjectConfigEnabled = editingProjectState.enabled

            if (isProjectConfigEnabled) {
                currentConfigMode = ConfigMode.PROJECT
            } else {
                currentConfigMode = ConfigMode.SYSTEM
            }
        }

        // 刷新CommandBar列表
        buttonListModel.removeAll()
        for (btn in editingState.commandBars) {
            buttonListModel.add(btn)
        }

        selectedCommandBar = null
        selectedCommand = null
        clearButtonDetail()
        clearOptionDetail()
        optionListModel.removeAll()
        updateQuickParamSummary()
    }

    // ==================== 列表渲染器 ====================

    private class ButtonListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): JComponent {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is CommandBarConfig) {
                text = value.name
                icon = CCBarIcons.loadIcon(value.icon)
                if (!value.enabled && !isSelected) {
                    foreground = com.intellij.ui.JBColor.GRAY
                }
            }
            return component as JComponent
        }
    }

    private class OptionListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): JComponent {
            // 处理分割线类型
            if (value is CommandConfig && value.isSeparator()) {
                return createSeparatorRenderer(list, value, isSelected)
            }

            // 普通 Command渲染
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is CommandConfig) {
                text = value.name
                icon = if (value.icon.isNotBlank()) CCBarIcons.loadIcon(value.icon) else null
                if (!value.enabled && !isSelected) {
                    foreground = com.intellij.ui.JBColor.GRAY
                }
            }
            return component as JComponent
        }

        private fun createSeparatorRenderer(
            list: JList<*>?,
            option: CommandConfig,
            isSelected: Boolean
        ): JComponent {
            val panel = JPanel(BorderLayout()).apply {
                isOpaque = true
                background = if (isSelected) {
                    list?.selectionBackground ?: com.intellij.ui.JBColor.PanelBackground
                } else {
                    list?.background ?: com.intellij.ui.JBColor.PanelBackground
                }
                border = JBUI.Borders.empty(4, 8)
            }

            if (option.name.isNotBlank()) {
                // 带标题的分割线：──── 标题 ────
                // 使用自定义绘制
                val innerPanel = object : JPanel() {
                    override fun paintComponent(g: java.awt.Graphics) {
                        super.paintComponent(g)
                        val g2d = g as java.awt.Graphics2D
                        g2d.color = com.intellij.ui.JBColor.GRAY
                        g2d.stroke = java.awt.BasicStroke(1f)

                        val labelWidth = graphics.getFontMetrics(font).stringWidth(option.name) + 16
                        val centerY = height / 2
                        val leftEnd = (width - labelWidth) / 2
                        val rightStart = leftEnd + labelWidth

                        // 左侧线
                        if (leftEnd > 0) {
                            g2d.drawLine(0, centerY, leftEnd, centerY)
                        }
                        // 右侧线
                        if (rightStart < width) {
                            g2d.drawLine(rightStart, centerY, width, centerY)
                        }
                    }
                }
                innerPanel.layout = BorderLayout()
                innerPanel.isOpaque = false

                val label = JLabel(option.name).apply {
                    foreground = com.intellij.ui.JBColor.GRAY
                    horizontalAlignment = SwingConstants.CENTER
                }
                innerPanel.add(label, BorderLayout.CENTER)
                innerPanel.preferredSize = Dimension(innerPanel.preferredSize.width, 24)
                panel.add(innerPanel, BorderLayout.CENTER)
            } else {
                // 无标题分割线：使用自定义绘制确保垂直居中
                val separatorPanel = object : JPanel() {
                    override fun paintComponent(g: java.awt.Graphics) {
                        super.paintComponent(g)
                        val g2d = g as java.awt.Graphics2D
                        g2d.color = com.intellij.ui.JBColor.GRAY
                        g2d.stroke = java.awt.BasicStroke(1f)
                        val centerY = height / 2
                        g2d.drawLine(0, centerY, width, centerY)
                    }
                }
                separatorPanel.isOpaque = false
                separatorPanel.preferredSize = Dimension(separatorPanel.preferredSize.width, 24)
                panel.add(separatorPanel, BorderLayout.CENTER)
            }

            panel.preferredSize = Dimension(panel.preferredSize.width, 24)
            return panel
        }
    }
}
