package site.laomst.ccbar.settings.ui

import site.laomst.ccbar.icons.CCBarIcons
import site.laomst.ccbar.settings.CCBarProjectSettings
import site.laomst.ccbar.settings.CCBarSettings
import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.CommandConfig
import site.laomst.ccbar.settings.deepCopy
import site.laomst.ccbar.settings.ui.panels.CommandBarDetailPanel
import site.laomst.ccbar.settings.ui.panels.CommandBarListPanel
import site.laomst.ccbar.settings.ui.panels.CommandDetailPanel
import site.laomst.ccbar.settings.ui.panels.CommandListPanel
import site.laomst.ccbar.settings.ui.shared.CommandBarDetailListener
import site.laomst.ccbar.settings.ui.shared.CommandBarListListener
import site.laomst.ccbar.settings.ui.shared.CommandDetailListener
import site.laomst.ccbar.settings.ui.shared.CommandListListener
import site.laomst.ccbar.settings.ui.shared.ConfigMode
import site.laomst.ccbar.settings.ui.shared.EditingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.JTabbedPane

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

    // 项目配置是否启用
    private var isProjectConfigEnabled: Boolean = false

    // 编辑上下文
    private lateinit var editingContext: EditingContext

    // 当前编辑状态（根据模式动态切换）
    private val editingState: CCBarSettings.State
        get() = if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
            CCBarSettings.State(editingProjectState.commandBars)
        } else {
            editingSystemState
        }

    // UI 组件
    private lateinit var mainPanelRef: JPanel
    private lateinit var rightContainer: JPanel
    private lateinit var commandPanel: JComponent
    private lateinit var tabbedPaneRef: JTabbedPane
    private lateinit var sharedConfigPanel: JComponent
    private lateinit var actionButtonsPanelRef: JPanel

    // 子面板
    private lateinit var commandBarListPanel: CommandBarListPanel
    private lateinit var commandBarDetailPanel: CommandBarDetailPanel
    private lateinit var commandListPanel: CommandListPanel
    private lateinit var commandDetailPanel: CommandDetailPanel

    // 网络图标加载完成监听器的取消注册函数
    private var removeIconLoadedListener: (() -> Unit)? = null

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
        // 初始化系统配置编辑状态（深拷贝）
        val systemSettings = CCBarSettings.getInstance()
        editingSystemState = systemSettings.state.deepCopy()

        // 初始化项目配置编辑状态（如果有项目）
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            editingProjectState = projectSettings.state.deepCopy()
            isProjectConfigEnabled = editingProjectState.enabled

            if (isProjectConfigEnabled) {
                currentConfigMode = ConfigMode.PROJECT
            }
        }

        // 创建编辑上下文
        editingContext = createEditingContext()

        // 创建主面板
        val mainPanel = JPanel(BorderLayout())
        mainPanelRef = mainPanel

        // 如果有项目，添加项目配置控制区域
        if (project != null) {
            mainPanel.add(createProjectConfigControlPanel(), BorderLayout.NORTH)
        }

        // 创建配置内容区域
        val contentPanel = if (project != null && isProjectConfigEnabled) {
            createTabbedConfigPanel()
        } else {
            createSingleConfigPanel()
        }
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // 底部操作按钮
        mainPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH)

        // 注册网络图标加载完成监听器
        removeIconLoadedListener?.invoke()
        removeIconLoadedListener = CCBarIcons.addIconLoadedListener {
            commandBarListPanel.repaintList()
            commandListPanel.repaintList()
        }

        return mainPanel
    }

    /**
     * 创建编辑上下文
     */
    private fun createEditingContext(): EditingContext {
        return object : EditingContext {
            override val configMode: ConfigMode
                get() = this@CCBarSettingsPanel.currentConfigMode

            override val currentState: CCBarSettings.State
                get() = this@CCBarSettingsPanel.editingState

            override var selectedCommandBar: CommandBarConfig?
                get() = this@CCBarSettingsPanel.selectedCommandBar
                set(value) {
                    this@CCBarSettingsPanel.selectedCommandBar = value
                }

            override var selectedCommand: CommandConfig?
                get() = this@CCBarSettingsPanel.selectedCommand
                set(value) {
                    this@CCBarSettingsPanel.selectedCommand = value
                }

            override var ignoreUpdate: Boolean
                get() = this@CCBarSettingsPanel.ignoreUpdate
                set(value) {
                    this@CCBarSettingsPanel.ignoreUpdate = value
                }

            override val projectPath: String?
                get() = this@CCBarSettingsPanel.getCurrentProjectPath()

            override fun notifyCommandBarListChanged() {
                commandBarListPanel.repaintList()
            }

            override fun notifyCommandListChanged() {
                commandListPanel.repaintList()
            }

            override fun notifyCommandBarDetailChanged() {
                // CommandBar 详情变更处理
            }

            override fun notifyCommandDetailChanged() {
                // Command 详情变更处理
            }
        }
    }

    // 选中状态
    private var selectedCommandBar: CommandBarConfig? = null
    private var selectedCommand: CommandConfig? = null
    private var ignoreUpdate = false

    /**
     * 创建项目配置控制面板（头部提示区域）
     */
    private fun createProjectConfigControlPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8, 8, 0, 8)

        val infoLabel = JLabel().apply {
            icon = AllIcons.General.Information
            horizontalTextPosition = SwingConstants.RIGHT
            iconTextGap = JBUI.scale(4)
        }
        panel.add(infoLabel, BorderLayout.CENTER)

        val actionButton = JButton()
        panel.add(actionButton, BorderLayout.EAST)

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
    private fun createTabbedConfigPanel(): JComponent {
        val tabbedPane = JTabbedPane()
        tabbedPaneRef = tabbedPane

        sharedConfigPanel = createConfigContentPanel()

        val emptyPanel1 = JPanel()
        val emptyPanel2 = JPanel()

        tabbedPane.addTab("系统配置", emptyPanel1)
        tabbedPane.addTab("项目配置", emptyPanel2)

        val initialIndex = if (currentConfigMode == ConfigMode.PROJECT) 1 else 0
        tabbedPane.setComponentAt(initialIndex, sharedConfigPanel)
        tabbedPane.selectedIndex = initialIndex

        tabbedPane.addChangeListener { e ->
            val source = e.source as JTabbedPane
            val newIndex = source.selectedIndex
            val newMode = if (newIndex == 0) ConfigMode.SYSTEM else ConfigMode.PROJECT

            if (newMode != currentConfigMode) {
                saveCurrentEditingState()
                currentConfigMode = newMode

                val oldIndex = if (newMode == ConfigMode.PROJECT) 0 else 1
                source.setComponentAt(oldIndex, JPanel())
                source.setComponentAt(newIndex, sharedConfigPanel)

                refreshConfigPanel()
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
        // 创建子面板
        commandBarListPanel = CommandBarListPanel(editingContext, createCommandBarListListener())
        commandBarDetailPanel = CommandBarDetailPanel(editingContext, project, createCommandBarDetailListener())
        commandListPanel = CommandListPanel(editingContext, createCommandListListener())
        commandDetailPanel = CommandDetailPanel(editingContext, project, createCommandDetailListener())

        // 创建 Command 面板（列表 + 详情）
        commandPanel = createCommandPanel()

        // 设置 CommandBar 详情面板中的 Command 面板引用
        commandBarDetailPanel.setCommandPanelRef(commandPanel)

        // 创建左右分割面板
        val splitter = OnePixelSplitter(false, 0.2f).apply {
            firstComponent = commandBarListPanel.createPanel()
            rightContainer = JPanel(CardLayout())
            rightContainer.add(createEmptyPanel(), CARD_EMPTY)
            rightContainer.add(createDetailPanel(), CARD_DETAIL)
            secondComponent = rightContainer
        }

        showEmptyPanel()
        return splitter
    }

    /**
     * 创建详情面板（右侧）
     */
    private fun createDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)

        panel.add(commandBarDetailPanel.createPanel(), BorderLayout.NORTH)
        panel.add(commandPanel, BorderLayout.CENTER)

        return panel
    }

    /**
     * 创建 Command 面板（列表 + 详情）
     */
    private fun createCommandPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val listPanel = commandListPanel.createPanel()
        val detailPanel = commandDetailPanel.createPanel()

        val splitter = OnePixelSplitter(false, 0.25f).apply {
            firstComponent = listPanel
            secondComponent = detailPanel
        }

        panel.add(splitter, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建空状态面板
     */
    private fun createEmptyPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JLabel("请从左侧选择一个 CommandBar", SwingConstants.CENTER)
        label.foreground = com.intellij.ui.JBColor.GRAY
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    // ==================== 监听器创建 ====================

    private fun createCommandBarListListener() = object : CommandBarListListener {
        override fun onCommandBarSelected(commandBar: CommandBarConfig?) {
            if (commandBar == null) {
                commandBarDetailPanel.clearData()
                commandListPanel.clearData()
                commandDetailPanel.hidePanel()
                showEmptyPanel()
            } else {
                commandBarDetailPanel.loadData(commandBar)
                commandListPanel.refreshData(commandBar)
                commandDetailPanel.setCurrentCommandBar(commandBar)
                showDetailPanel()
            }
        }
    }

    private fun createCommandBarDetailListener() = object : CommandBarDetailListener {
        override fun onDirectCommandModeChanged(isDirectMode: Boolean) {
            commandPanel.isVisible = !isDirectMode
            if (!isDirectMode) {
                commandListPanel.refreshData(selectedCommandBar)
            }
        }

        override fun onSimpleModeChanged(isSimpleMode: Boolean) {
            // 简易模式变更时更新 Command 详情面板中的快捷参数显示
            if (selectedCommand?.isSeparator() == false) {
                commandDetailPanel.loadData(selectedCommand)
            }
        }
    }

    private fun createCommandListListener() = object : CommandListListener {
        override fun onCommandSelected(command: CommandConfig?) {
            commandDetailPanel.setCurrentCommandBar(selectedCommandBar)
            if (command == null) {
                commandDetailPanel.clearData()
                commandDetailPanel.hidePanel()
            } else {
                commandDetailPanel.loadData(command)
            }
        }

        override fun onAddCommand() {
            // 由 AddCommandPopup 处理
        }

        override fun onAddSeparator() {
            // 由 AddCommandPopup 处理
        }
    }

    private fun createCommandDetailListener() = object : CommandDetailListener {
        override fun onQuickParamsUpdated() {
            // 快捷参数更新后的处理
        }
    }

    // ==================== 面板切换 ====================

    private fun showEmptyPanel() {
        val layout = rightContainer.layout as CardLayout
        layout.show(rightContainer, CARD_EMPTY)
    }

    private fun showDetailPanel() {
        val layout = rightContainer.layout as CardLayout
        layout.show(rightContainer, CARD_DETAIL)
    }

    // ==================== 配置操作 ====================

    private fun saveCurrentEditingState() {
        // 快捷参数数据已通过对话框直接同步到数据模型
    }

    private fun refreshConfigPanel() {
        commandBarListPanel.refreshData()
        selectedCommandBar = null
        selectedCommand = null
        commandBarDetailPanel.clearData()
        commandDetailPanel.clearData()
        commandListPanel.clearData()
        showEmptyPanel()
    }

    private fun enableProjectConfig() {
        if (editingProjectState.commandBars.isEmpty()) {
            editingProjectState.commandBars = editingSystemState.deepCopy().commandBars
        }

        editingProjectState.enabled = true
        isProjectConfigEnabled = true
        currentConfigMode = ConfigMode.PROJECT

        rebuildMainPanel()
    }

    private fun disableProjectConfig() {
        editingProjectState.enabled = false
        isProjectConfigEnabled = false
        currentConfigMode = ConfigMode.SYSTEM

        rebuildMainPanel()
    }

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

    private fun rebuildMainPanel() {
        val parent = mainPanelRef.parent ?: return

        val newMainPanel = JPanel(BorderLayout())

        if (project != null) {
            newMainPanel.add(createProjectConfigControlPanel(), BorderLayout.NORTH)
        }

        val contentPanel = if (isProjectConfigEnabled) {
            createTabbedConfigPanel()
        } else {
            createSingleConfigPanel()
        }
        newMainPanel.add(contentPanel, BorderLayout.CENTER)
        newMainPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH)

        parent.layout = BorderLayout()
        parent.removeAll()
        parent.add(newMainPanel, BorderLayout.CENTER)
        parent.revalidate()
        parent.repaint()

        mainPanelRef = newMainPanel
    }

    // ==================== 底部操作按钮 ====================

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

        if (project != null && currentConfigMode == ConfigMode.PROJECT) {
            panel.add(Box.createHorizontalStrut(JBUI.scale(8)))

            val resetButton = JButton("重置为系统配置")
            resetButton.addActionListener { resetProjectConfigToSystem() }
            panel.add(resetButton)
        }

        return panel
    }

    private fun updateActionButtonsPanel() {
        if (!::actionButtonsPanelRef.isInitialized) return

        val parent = actionButtonsPanelRef.parent ?: return

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

        if (project != null && currentConfigMode == ConfigMode.PROJECT) {
            newPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))

            val resetButton = JButton("重置为系统配置")
            resetButton.addActionListener { resetProjectConfigToSystem() }
            newPanel.add(resetButton)
        }

        parent.remove(actionButtonsPanelRef)
        parent.add(newPanel, BorderLayout.SOUTH)
        parent.revalidate()
        parent.repaint()

        actionButtonsPanelRef = newPanel
    }

    // ==================== 导入/导出配置 ====================

    private fun importConfig() {
        val fileChooser = FileChooserDescriptorFactory
            .createSingleFileDescriptor("json")
            .withTitle("导入配置")
            .withDescription("选择要导入的 JSON 配置文件")
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

                val importedButtons = gson.fromJson(content, Array<CommandBarConfig>::class.java)

                if (importedButtons != null && importedButtons.isNotEmpty()) {
                    val result = Messages.showYesNoDialog(
                        "导入将覆盖当前所有配置，是否继续？",
                        "确认导入",
                        null
                    )
                    if (result == Messages.YES) {
                        val importedList = importedButtons.toMutableList()
                        if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
                            editingProjectState.commandBars = importedList
                        } else {
                            editingSystemState.commandBars = importedList
                        }

                        commandBarListPanel.refreshData()
                        selectedCommandBar = null
                        selectedCommand = null
                        commandBarDetailPanel.clearData()
                        commandDetailPanel.clearData()
                        commandListPanel.clearData()

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
        val fileSaverDescriptor = FileSaverDescriptor(
            "导出配置",
            "选择保存位置",
            "json"
        )
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
            val defaultButtons = CCBarSettings.createDefaultCommandBars()
            if (currentConfigMode == ConfigMode.PROJECT && editingProjectState.enabled) {
                editingProjectState.commandBars = defaultButtons
            } else {
                editingSystemState.commandBars = defaultButtons
            }

            commandBarListPanel.refreshData()
            selectedCommandBar = null
            selectedCommand = null
            commandBarDetailPanel.clearData()
            commandDetailPanel.clearData()
            commandListPanel.clearData()
        }
    }

    // ==================== Configurable 接口实现 ====================

    fun isModified(): Boolean {
        val systemSettings = CCBarSettings.getInstance()
        val systemModified = editingSystemState.commandBars != systemSettings.state.commandBars

        var projectModified = false
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            projectModified = editingProjectState != projectSettings.state
        }

        return systemModified || projectModified
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        for ((commandBarIndex, button) in editingState.commandBars.withIndex()) {
            if (button.name.isBlank()) {
                errors.add("CommandBar ${commandBarIndex + 1}: 名称不能为空")
            }
            if (editingState.commandBars.count { it.name == button.name } > 1) {
                errors.add("CommandBar '${button.name}': 名称重复")
            }

            if (!button.enabled) continue

            if (button.isDirectCommandMode()) {
                if (button.defaultTerminalName.isBlank()) {
                    errors.add("CommandBar '${button.name}': 直接命令模式下，终端名称不能为空")
                }
            } else {
                val enabledNormalCommands = button.commands.filter { !it.isSeparator() && it.enabled }
                if (enabledNormalCommands.isEmpty()) {
                    errors.add("CommandBar '${button.name}': 未配置直接命令时，必须至少有一个启用的普通 Command")
                }

                for ((quickParamIndex, quickParam) in button.commonQuickParams.withIndex()) {
                    if (!quickParam.enabled) continue

                    if (quickParam.name.isBlank()) {
                        errors.add("CommandBar '${button.name}' 公共快捷参数 ${quickParamIndex + 1}: 名称不能为空")
                    }
                    if (button.commonQuickParams.filter { it.enabled }.count { it.name == quickParam.name } > 1) {
                        errors.add("CommandBar '${button.name}' 公共快捷参数 '${quickParam.name}': 名称重复")
                    }
                }

                for ((commandIndex, option) in button.commands.withIndex()) {
                    if (option.isSeparator()) continue

                    if (!option.enabled) continue

                    if (option.name.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command ${commandIndex + 1}: 名称不能为空")
                    }
                    if (enabledNormalCommands.count { it.name == option.name } > 1) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 名称重复")
                    }
                    if (option.baseCommand.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 基础命令不能为空")
                    }
                    if (option.defaultTerminalName.isBlank()) {
                        errors.add("CommandBar '${button.name}' Command '${option.name}': 终端名称不能为空")
                    }

                    for ((quickParamIndex, quickParam) in option.quickParams.withIndex()) {
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
        val systemSettings = CCBarSettings.getInstance()
        systemSettings.loadState(editingSystemState.deepCopy())

        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            projectSettings.loadState(editingProjectState.deepCopy())
        }
    }

    fun reset() {
        val systemSettings = CCBarSettings.getInstance()
        editingSystemState = systemSettings.state.deepCopy()

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

        commandBarListPanel.refreshData()
        selectedCommandBar = null
        selectedCommand = null
        commandBarDetailPanel.clearData()
        commandDetailPanel.clearData()
        commandListPanel.clearData()
    }
}
