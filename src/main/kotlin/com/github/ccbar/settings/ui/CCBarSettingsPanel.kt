package com.github.ccbar.settings.ui

import com.github.ccbar.settings.*
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel

/**
 * CCBar 设置面板
 * 使用 JBSplitter 构建左右分栏布局
 */
class CCBarSettingsPanel {

    // 编辑状态（深拷贝）
    private var editingState: CCBarSettings.State = CCBarSettings.State()

    // UI 组件
    private lateinit var buttonListModel: CollectionListModel<ButtonConfig>
    private lateinit var buttonList: JBList<ButtonConfig>
    private lateinit var optionListModel: CollectionListModel<OptionConfig>
    private lateinit var optionList: JBList<OptionConfig>
    private lateinit var subButtonTableModel: DefaultTableModel
    private lateinit var subButtonTable: JTable

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

    // Button 终端名称面板（用于控制显示/隐藏）
    private lateinit var buttonTerminalNamePanel: JComponent

    // Options 面板引用（用于控制显示/隐藏）
    private lateinit var optionPanel: JComponent

    // 右侧详情容器（用于切换空状态和详情面板）
    private lateinit var rightContainer: JPanel

    // Option 详情字段
    private lateinit var optionNameField: JBTextField
    private lateinit var baseCommandField: JBTextField
    private lateinit var workingDirectoryField: TextFieldWithBrowseButton
    private lateinit var defaultTerminalNameField: JBTextField

    // Option 提示标签（用于动态更新文字）
    private lateinit var optionWorkDirHintLabel: JLabel

    // Option 详情面板和 SubButton 面板（用于控制显示/隐藏）
    private lateinit var optionDetailOuterPanel: JComponent
    private lateinit var subButtonOuterPanel: JComponent

    // Option 详情面板中各个字段的容器（用于控制分割线时只显示名称）
    private lateinit var optionCommandPanel: JPanel
    private lateinit var optionDirPanel: JPanel
    private lateinit var optionDirHintPanel: JPanel
    private lateinit var optionTerminalNamePanel: JPanel

    // Option 详情面板的边框（用于动态更新标题）
    private lateinit var optionDetailTitledBorder: javax.swing.border.TitledBorder

    // 当前选中的 Button 和 Option
    private var selectedButton: ButtonConfig? = null
    private var selectedOption: OptionConfig? = null

    // 忽略更新标志（用于批量更新时避免循环）
    private var ignoreUpdate = false

    // 卡片布局常量
    private companion object {
        const val CARD_EMPTY = "empty"
        const val CARD_DETAIL = "detail"
    }

    /**
     * 获取当前打开项目的根路径
     */
    private fun getCurrentProjectPath(): String? {
        return ProjectManager.getInstance().openProjects.firstOrNull()?.basePath
    }

    /**
     * 创建主面板
     */
    fun createPanel(): JComponent {
        // 初始化编辑状态
        val settings = CCBarSettings.getInstance()
        editingState = settings.state.deepCopy()

        // 创建主面板
        val mainPanel = JPanel(BorderLayout())

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

        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(createActionButtonsPanel(), BorderLayout.SOUTH)

        return mainPanel
    }

    /**
     * 创建空状态面板（未选中按钮时显示）
     */
    private fun createEmptyPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val label = JLabel("请从左侧选择一个按钮", SwingConstants.CENTER)
        label.foreground = com.intellij.ui.JBColor.GRAY
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建 Button 列表面板
     */
    private fun createButtonListPanel(): JComponent {
        buttonListModel = CollectionListModel(editingState.buttons)
        buttonList = JBList(buttonListModel).apply {
            cellRenderer = ButtonListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            addListSelectionListener { onButtonSelected() }
        }

        val panel = BorderLayoutPanel().withBorder(JBUI.Borders.empty(8))
        val decorator = com.intellij.ui.ToolbarDecorator.createDecorator(buttonList)
            .setAddAction { addButton() }
            .setRemoveAction { removeButton() }
            .setMoveUpAction { moveButtonUp() }
            .setMoveDownAction { moveButtonDown() }

        panel.addToCenter(decorator.createPanel())
        return panel
    }

    /**
     * 创建详情面板（右侧）
     */
    private fun createDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout())

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
            border = BorderFactory.createTitledBorder("Button 详情")
        }

        // Name 字段
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
        panel.add(namePanel)

        // Icon 字段
        val iconPanel = JPanel(BorderLayout())
        iconPanel.add(JLabel("图标:"), BorderLayout.WEST)
        buttonIconField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择图标文件",
                "选择 SVG 或 PNG 图标文件",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateButtonIcon()
                override fun removeUpdate(e: DocumentEvent?) = updateButtonIcon()
                override fun changedUpdate(e: DocumentEvent?) = updateButtonIcon()
            })
        }
        iconPanel.add(buttonIconField, BorderLayout.CENTER)
        panel.add(iconPanel)

        // Command 字段（新增）
        val commandPanel = JPanel(BorderLayout())
        commandPanel.add(JLabel("直接命令:"), BorderLayout.WEST)
        buttonCommandHintLabel = JLabel("输入直接命令后将不支持绑定选项列表").apply {
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

        // Terminal Name 字段（仅直接命令模式时显示）
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

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 创建 Option 面板（列表 + 详情 + SubButton 表格）
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
        val decorator = com.intellij.ui.ToolbarDecorator.createDecorator(optionList)
            .setAddAction {
                // 显示下拉菜单，让用户选择添加选项或分割线
                showAddOptionPopup()
            }
            .setRemoveAction { removeOption() }
            .setMoveUpAction { moveOptionUp() }
            .setMoveDownAction { moveOptionDown() }
        listPanel.addToCenter(decorator.createPanel())

        // Option 详情
        optionDetailOuterPanel = createOptionDetailPanel()

        // SubButton 表格
        subButtonOuterPanel = createSubButtonPanel()

        // 组合布局
        val rightPanel = JPanel(BorderLayout())
        rightPanel.add(optionDetailOuterPanel, BorderLayout.NORTH)
        rightPanel.add(subButtonOuterPanel, BorderLayout.CENTER)

        val splitter = OnePixelSplitter(false, 0.25f).apply {
            firstComponent = listPanel
            secondComponent = rightPanel
        }

        panel.add(splitter, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建 Option 详情面板
     */
    private fun createOptionDetailPanel(): JComponent {
        val outerPanel = JPanel(BorderLayout())
        optionDetailTitledBorder = BorderFactory.createTitledBorder("Option 详情")
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = optionDetailTitledBorder
        }

        // Name（始终显示）
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
        panel.add(namePanel)

        // Base Command（仅普通选项显示）
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

        // Working Directory（仅普通选项显示）
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

        // Default Terminal Name（仅普通选项显示）
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

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 创建 SubButton 表格面板
     */
    private fun createSubButtonPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder("SubButton 列表")

        subButtonTableModel = object : DefaultTableModel(arrayOf("名称", "参数"), 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = true
        }
        subButtonTable = JBTable(subButtonTableModel)

        // 监听表格数据变更，实时同步到数据模型
        subButtonTableModel.addTableModelListener { e ->
            if (ignoreUpdate) return@addTableModelListener
            if (e.type == TableModelEvent.UPDATE) {
                syncSubButtonTableToModel()
            }
        }

        val decorator = com.intellij.ui.ToolbarDecorator.createDecorator(subButtonTable)
            .setAddAction { addSubButton() }
            .setRemoveAction { removeSubButton() }
            .setMoveUpAction { moveSubButtonUp() }
            .setMoveDownAction { moveSubButtonDown() }

        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建操作按钮面板（Import/Export/Reset）
     */
    private fun createActionButtonsPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(8)
        }

        panel.add(Box.createHorizontalGlue())

        val importButton = JButton("导入")
        importButton.addActionListener { importConfig() }
        panel.add(importButton)

        panel.add(Box.createHorizontalStrut(JBUI.scale(8)))

        val exportButton = JButton("导出")
        exportButton.addActionListener { exportConfig() }
        panel.add(exportButton)

        panel.add(Box.createHorizontalStrut(JBUI.scale(8)))

        val resetButton = JButton("重置")
        resetButton.addActionListener { resetConfig() }
        panel.add(resetButton)

        return panel
    }

    // ==================== Button 列表操作 ====================

    private fun addButton() {
        val newButton = ButtonConfig(
            id = UUID.randomUUID().toString(),
            name = "New Button",
            icon = "builtin:AllIcons.Actions.Execute"
        )
        buttonListModel.add(newButton)
        editingState.buttons.add(newButton)
        buttonList.selectedIndex = buttonListModel.size - 1
    }

    private fun removeButton() {
        val index = buttonList.selectedIndex
        if (index >= 0) {
            val result = Messages.showYesNoDialog(
                "确定要删除按钮 '${buttonListModel.getElementAt(index).name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                buttonListModel.remove(index)
                editingState.buttons.removeAt(index)
                selectedButton = null
                clearButtonDetail()
            }
        }
    }

    private fun moveButtonUp() {
        val index = buttonList.selectedIndex
        if (index > 0) {
            Collections.swap(editingState.buttons, index, index - 1)
            val item = buttonListModel.getElementAt(index)
            buttonListModel.remove(index)
            buttonListModel.add(index - 1, item)
            buttonList.selectedIndex = index - 1
        }
    }

    private fun moveButtonDown() {
        val index = buttonList.selectedIndex
        if (index < buttonListModel.size - 1) {
            Collections.swap(editingState.buttons, index, index + 1)
            val item = buttonListModel.getElementAt(index)
            buttonListModel.remove(index)
            buttonListModel.add(index + 1, item)
            buttonList.selectedIndex = index + 1
        }
    }

    private fun onButtonSelected() {
        if (ignoreUpdate) return

        val index = buttonList.selectedIndex
        if (index >= 0) {
            selectedButton = buttonListModel.getElementAt(index)
            updateButtonDetail()
            updateOptionList()
            showDetailPanel()
        } else {
            selectedButton = null
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
            val button = selectedButton ?: return
            buttonNameField.text = button.name
            buttonIconField.text = button.icon
            buttonCommandField.text = button.command
            buttonWorkingDirectoryField.text = button.workingDirectory
            buttonTerminalNameField.text = button.defaultTerminalName
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
            buttonNameField.text = ""
            buttonIconField.text = ""
            buttonCommandField.text = ""
            buttonWorkingDirectoryField.text = ""
            buttonTerminalNameField.text = ""
            // 重置提示文字状态
            updateCommandHintVisibility()
            updateWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun updateButtonName() {
        if (ignoreUpdate) return
        selectedButton?.name = buttonNameField.text
        buttonList.repaint()
    }

    private fun updateButtonIcon() {
        if (ignoreUpdate) return
        selectedButton?.icon = buttonIconField.text
    }

    private fun updateButtonCommand() {
        if (ignoreUpdate) return
        selectedButton?.command = buttonCommandField.text
        // 切换直接命令模式相关字段的显示状态
        updateDirectCommandModeVisibility()
    }

    private fun updateButtonWorkingDirectory() {
        if (ignoreUpdate) return
        selectedButton?.workingDirectory = buttonWorkingDirectoryField.text
    }

    private fun updateButtonTerminalName() {
        if (ignoreUpdate) return
        selectedButton?.defaultTerminalName = buttonTerminalNameField.text
    }

    /**
     * 更新直接命令提示文字的显示状态
     */
    private fun updateCommandHintVisibility() {
        buttonCommandHintLabel.text = if (buttonCommandField.text.isBlank()) {
            "输入直接命令后将不支持绑定选项列表"
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
     * - Options 面板：仅在选项列表模式下显示
     */
    private fun updateDirectCommandModeVisibility() {
        val isDirectMode = selectedButton?.isDirectCommandMode() == true
        buttonWorkingDirectoryPanel.isVisible = isDirectMode
        buttonTerminalNamePanel.isVisible = isDirectMode
        optionPanel.isVisible = !isDirectMode
    }

    // ==================== Option 列表操作 ====================

    private fun updateOptionList() {
        optionListModel.removeAll()
        selectedButton?.options?.let { options ->
            for (option in options) {
                optionListModel.add(option)
            }
            if (options.isNotEmpty()) {
                optionList.selectedIndex = 0
            } else {
                selectedOption = null
                clearOptionDetail()
                updateSubButtonTable()
            }
        }
    }

    private fun addOption() {
        val button = selectedButton ?: return
        val newOption = OptionConfig(
            id = UUID.randomUUID().toString(),
            name = "New Option",
            baseCommand = "",
            defaultTerminalName = ""
        )
        button.options.add(newOption)
        optionListModel.add(newOption)
        optionList.selectedIndex = optionListModel.size - 1
    }

    /**
     * 显示添加 Option 的下拉菜单
     */
    private fun showAddOptionPopup() {
        val options = arrayOf("添加选项", "添加分割线")
        val selected = Messages.showEditableChooseDialog(
            "请选择要添加的类型：",
            "添加",
            Messages.getQuestionIcon(),
            options,
            options[0],
            null
        )
        when (selected) {
            "添加选项" -> addOption()
            "添加分割线" -> addSeparator()
        }
    }

    /**
     * 添加分割线
     */
    private fun addSeparator() {
        val button = selectedButton ?: return
        val newSeparator = OptionConfig(
            id = UUID.randomUUID().toString(),
            name = "",
            type = OptionType.SEPARATOR
        )
        button.options.add(newSeparator)
        optionListModel.add(newSeparator)
        optionList.selectedIndex = optionListModel.size - 1
    }

    private fun removeOption() {
        val button = selectedButton ?: return
        val index = optionList.selectedIndex
        if (index >= 0) {
            val result = Messages.showYesNoDialog(
                "确定要删除选项 '${optionListModel.getElementAt(index).name}' 吗？",
                "确认删除",
                null
            )
            if (result == Messages.YES) {
                button.options.removeAt(index)
                optionListModel.remove(index)
                selectedOption = null
                clearOptionDetail()
                updateSubButtonTable()
            }
        }
    }

    private fun moveOptionUp() {
        val button = selectedButton ?: return
        val index = optionList.selectedIndex
        if (index > 0) {
            Collections.swap(button.options, index, index - 1)
            val item = optionListModel.getElementAt(index)
            optionListModel.remove(index)
            optionListModel.add(index - 1, item)
            optionList.selectedIndex = index - 1
        }
    }

    private fun moveOptionDown() {
        val button = selectedButton ?: return
        val index = optionList.selectedIndex
        if (index < optionListModel.size - 1) {
            Collections.swap(button.options, index, index + 1)
            val item = optionListModel.getElementAt(index)
            optionListModel.remove(index)
            optionListModel.add(index + 1, item)
            optionList.selectedIndex = index + 1
        }
    }

    private fun onOptionSelected() {
        if (ignoreUpdate) return

        val index = optionList.selectedIndex
        if (index >= 0 && selectedButton != null) {
            selectedOption = selectedButton!!.options[index]

            // 先更新表单数据
            updateOptionDetail()

            if (selectedOption!!.isSeparator()) {
                // 分割线：隐藏不需要的字段
                hideOptionDetailForSeparator()
            } else {
                // 普通选项：显示所有字段
                showOptionDetail()
                updateSubButtonTable()
            }
        } else {
            selectedOption = null
            clearOptionDetail()
            updateSubButtonTable()
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
        if (optionCommandPanel.isVisible) {
            optionCommandPanel.isVisible = false
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
        if (subButtonOuterPanel.isVisible) {
            subButtonOuterPanel.isVisible = false
        }
        // 更新标题
        optionDetailTitledBorder.title = "分割线详情"
    }

    /**
     * 显示普通选项的详情面板（显示所有字段）
     */
    private fun showOptionDetail() {
        // 只在需要时更新可见性，避免不必要的面板刷新
        if (!optionDetailOuterPanel.isVisible) {
            optionDetailOuterPanel.isVisible = true
        }
        if (!optionCommandPanel.isVisible) {
            optionCommandPanel.isVisible = true
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
        if (!subButtonOuterPanel.isVisible) {
            subButtonOuterPanel.isVisible = true
        }
        // 更新标题
        optionDetailTitledBorder.title = "Option 详情"
    }

    private fun updateOptionDetail() {
        ignoreUpdate = true
        try {
            val option = selectedOption ?: return
            optionNameField.text = option.name
            baseCommandField.text = option.baseCommand
            workingDirectoryField.text = option.workingDirectory
            defaultTerminalNameField.text = option.defaultTerminalName
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
            baseCommandField.text = ""
            workingDirectoryField.text = ""
            defaultTerminalNameField.text = ""
            // 重置提示文字状态
            updateOptionWorkDirHintVisibility()
        } finally {
            ignoreUpdate = false
        }
    }

    private fun updateOptionName() {
        if (ignoreUpdate) return
        selectedOption?.name = optionNameField.text
        optionList.repaint()
    }

    private fun updateOptionCommand() {
        if (ignoreUpdate) return
        selectedOption?.baseCommand = baseCommandField.text
    }

    private fun updateOptionDirectory() {
        if (ignoreUpdate) return
        selectedOption?.workingDirectory = workingDirectoryField.text
    }

    private fun updateOptionTerminalName() {
        if (ignoreUpdate) return
        selectedOption?.defaultTerminalName = defaultTerminalNameField.text
    }

    // ==================== SubButton 表格操作 ====================

    private fun updateSubButtonTable() {
        ignoreUpdate = true
        try {
            subButtonTableModel.rowCount = 0
            selectedOption?.subButtons?.forEach { subButton ->
                subButtonTableModel.addRow(arrayOf(subButton.name, subButton.params))
            }
        } finally {
            ignoreUpdate = false
        }
    }

    /**
     * 将 SubButton 表格中的数据同步回数据模型
     */
    private fun syncSubButtonTableToModel() {
        val option = selectedOption ?: return
        for (i in 0 until subButtonTableModel.rowCount) {
            if (i < option.subButtons.size) {
                option.subButtons[i].name = subButtonTableModel.getValueAt(i, 0) as? String ?: ""
                option.subButtons[i].params = subButtonTableModel.getValueAt(i, 1) as? String ?: ""
            }
        }
    }

    private fun addSubButton() {
        val option = selectedOption ?: return
        val newSubButton = SubButtonConfig(
            id = UUID.randomUUID().toString(),
            name = "New SubButton",
            params = ""
        )
        option.subButtons.add(newSubButton)
        subButtonTableModel.addRow(arrayOf(newSubButton.name, newSubButton.params))
    }

    private fun removeSubButton() {
        val option = selectedOption ?: return
        val index = subButtonTable.selectedRow
        if (index >= 0) {
            option.subButtons.removeAt(index)
            subButtonTableModel.removeRow(index)
        }
    }

    private fun moveSubButtonUp() {
        val option = selectedOption ?: return
        val index = subButtonTable.selectedRow
        if (index > 0) {
            Collections.swap(option.subButtons, index, index - 1)
            val name = subButtonTableModel.getValueAt(index, 0)
            val params = subButtonTableModel.getValueAt(index, 1)
            subButtonTableModel.setValueAt(subButtonTableModel.getValueAt(index - 1, 0), index, 0)
            subButtonTableModel.setValueAt(subButtonTableModel.getValueAt(index - 1, 1), index, 1)
            subButtonTableModel.setValueAt(name, index - 1, 0)
            subButtonTableModel.setValueAt(params, index - 1, 1)
            subButtonTable.setRowSelectionInterval(index - 1, index - 1)
        }
    }

    private fun moveSubButtonDown() {
        val option = selectedOption ?: return
        val index = subButtonTable.selectedRow
        if (index < subButtonTableModel.rowCount - 1) {
            Collections.swap(option.subButtons, index, index + 1)
            val name = subButtonTableModel.getValueAt(index, 0)
            val params = subButtonTableModel.getValueAt(index, 1)
            subButtonTableModel.setValueAt(subButtonTableModel.getValueAt(index + 1, 0), index, 0)
            subButtonTableModel.setValueAt(subButtonTableModel.getValueAt(index + 1, 1), index, 1)
            subButtonTableModel.setValueAt(name, index + 1, 0)
            subButtonTableModel.setValueAt(params, index + 1, 1)
            subButtonTable.setRowSelectionInterval(index + 1, index + 1)
        }
    }

    // ==================== 配置操作 ====================

    private fun importConfig() {
        val fileChooser = FileChooserDescriptorFactory
            .createSingleFileDescriptor()
            .withTitle("导入配置")
            .withDescription("选择要导入的 JSON 配置文件")

        val file = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            fileChooser,
            null,
            null
        )

        if (file != null) {
            try {
                val content = String(file.contentsToByteArray(), Charsets.UTF_8)
                val gson = com.google.gson.Gson()

                // 解析 JSON
                val importedButtons = gson.fromJson(content, Array<ButtonConfig>::class.java)

                if (importedButtons != null && importedButtons.isNotEmpty()) {
                    val result = Messages.showYesNoDialog(
                        "导入将覆盖当前所有配置，是否继续？",
                        "确认导入",
                        null
                    )
                    if (result == Messages.YES) {
                        editingState.buttons = importedButtons.toMutableList()
                        buttonListModel.removeAll()
                        for (btn in editingState.buttons) {
                            buttonListModel.add(btn)
                        }
                        selectedButton = null
                        selectedOption = null
                        clearButtonDetail()
                        clearOptionDetail()
                        optionListModel.removeAll()
                        subButtonTableModel.rowCount = 0

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

        val dialog = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createSaveFileDialog(fileSaverDescriptor, null as com.intellij.openapi.project.Project?)

        val result = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "ccbar-config.json")

        if (result != null) {
            try {
                val gson = com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()

                val json = gson.toJson(editingState.buttons)

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
            editingState.buttons = CCBarSettings.createDefaultButtons()
            buttonListModel.removeAll()
            for (btn in editingState.buttons) {
                buttonListModel.add(btn)
            }
            selectedButton = null
            selectedOption = null
            clearButtonDetail()
            clearOptionDetail()
            optionListModel.removeAll()
            subButtonTableModel.rowCount = 0
        }
    }

    // ==================== Configurable 接口实现 ====================

    fun isModified(): Boolean {
        // 如果表格正在编辑中，不停止编辑，直接返回 true（假设有修改）
        // 这样可以避免在用户编辑过程中被定期调用的 isModified() 干扰编辑
        if (subButtonTable.isEditing) {
            return true
        }

        val settings = CCBarSettings.getInstance()
        val originalState = settings.state
        return editingState.buttons != originalState.buttons
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        for ((buttonIndex, button) in editingState.buttons.withIndex()) {
            if (button.name.isBlank()) {
                errors.add("Button ${buttonIndex + 1}: 名称不能为空")
            }
            if (editingState.buttons.count { it.name == button.name } > 1) {
                errors.add("Button '${button.name}': 名称重复")
            }

            // 直接命令模式验证
            if (button.isDirectCommandMode()) {
                if (button.defaultTerminalName.isBlank()) {
                    errors.add("Button '${button.name}': 直接命令模式下，终端名称不能为空")
                }
                // 直接命令模式下不验证 Options
            } else {
                // 选项列表模式验证
                // 过滤掉分割线，只计算普通选项
                val normalOptions = button.options.filter { !it.isSeparator() }
                if (normalOptions.isEmpty()) {
                    errors.add("Button '${button.name}': 未配置直接命令时，必须至少有一个普通选项")
                }

                for ((optionIndex, option) in button.options.withIndex()) {
                    // 跳过分割线类型的验证
                    if (option.isSeparator()) continue

                    if (option.name.isBlank()) {
                        errors.add("Button '${button.name}' Option ${optionIndex + 1}: 名称不能为空")
                    }
                    // 只检查普通选项的名称重复
                    if (normalOptions.count { it.name == option.name } > 1) {
                        errors.add("Button '${button.name}' Option '${option.name}': 名称重复")
                    }
                    if (option.baseCommand.isBlank()) {
                        errors.add("Button '${button.name}' Option '${option.name}': 基础命令不能为空")
                    }
                    if (option.defaultTerminalName.isBlank()) {
                        errors.add("Button '${button.name}' Option '${option.name}': 终端名称不能为空")
                    }

                    for ((subButtonIndex, subButton) in option.subButtons.withIndex()) {
                        if (subButton.name.isBlank()) {
                            errors.add("Button '${button.name}' Option '${option.name}' SubButton ${subButtonIndex + 1}: 名称不能为空")
                        }
                        if (option.subButtons.count { it.name == subButton.name } > 1) {
                            errors.add("Button '${button.name}' Option '${option.name}' SubButton '${subButton.name}': 名称重复")
                        }
                    }
                }
            }
        }

        return errors
    }

    fun apply() {
        // 提交正在编辑中的单元格
        subButtonTable.cellEditor?.stopCellEditing()
        // 同步 SubButton 表格编辑到数据模型
        syncSubButtonTableToModel()

        // 保存到持久化层
        val settings = CCBarSettings.getInstance()
        settings.loadState(editingState.deepCopy())
    }

    fun reset() {
        val settings = CCBarSettings.getInstance()
        editingState = settings.state.deepCopy()
        buttonListModel.removeAll()
        for (btn in editingState.buttons) {
            buttonListModel.add(btn)
        }
        selectedButton = null
        selectedOption = null
        clearButtonDetail()
        clearOptionDetail()
        optionListModel.removeAll()
        subButtonTableModel.rowCount = 0
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
            if (value is ButtonConfig) {
                text = value.name
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
            if (value is OptionConfig && value.isSeparator()) {
                return createSeparatorRenderer(list, value, isSelected)
            }

            // 普通选项渲染
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is OptionConfig) {
                text = value.name
            }
            return component as JComponent
        }

        private fun createSeparatorRenderer(
            list: JList<*>?,
            option: OptionConfig,
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
