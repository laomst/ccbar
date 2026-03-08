package site.laomst.ccbar.settings.ui.panels

import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.TerminalMode
import site.laomst.ccbar.settings.ui.BuiltinIconSelector
import site.laomst.ccbar.settings.ui.shared.CommandBarDetailListener
import site.laomst.ccbar.settings.ui.shared.EditingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import site.laomst.ccbar.terminal.EnvVariablesDialog
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * CommandBar 详情面板
 * 负责显示和编辑 CommandBar 的详细信息
 */
class CommandBarDetailPanel(
    private val context: EditingContext,
    private val project: Project?,
    private val listener: CommandBarDetailListener
) {
    // UI 组件
    private lateinit var enabledCheckbox: JCheckBox
    private lateinit var nameField: JBTextField
    private lateinit var iconField: TextFieldWithBrowseButton
    private lateinit var commandField: JBTextField
    private lateinit var commandHintLabel: JLabel
    private lateinit var terminalNameField: JBTextField
    private lateinit var terminalModeCheckbox: JCheckBox
    private lateinit var terminalTabPrefixField: JBTextField
    private lateinit var showPrefixInEditorCheckbox: JCheckBox
    private lateinit var showPrefixInTerminalCheckbox: JCheckBox
    private lateinit var workingDirField: TextFieldWithBrowseButton
    private lateinit var workDirHintLabel: JLabel
    private lateinit var commonEnvVariablesField: JBTextField
    private lateinit var envVariablesField: JBTextField
    private lateinit var simpleModeCheckbox: JCheckBox

    // 面板引用（用于控制显示/隐藏）
    private lateinit var terminalNamePanel: JComponent
    private lateinit var terminalModePanel: JComponent
    private lateinit var terminalTabPrefixPanel: JComponent
    private lateinit var workingDirPanel: JComponent
    private lateinit var envVariablesPanel: JComponent
    private lateinit var simpleModePanel: JComponent
    private lateinit var commandPanel: JComponent
    private lateinit var commonQuickParamsPanel: JComponent
    private lateinit var commonQuickParamsSummaryField: JBTextField

    /**
     * 创建面板
     */
    fun createPanel(): JComponent {
        val outerPanel = JPanel(BorderLayout())
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder("CommandBar 详情")
        }

        // 启用复选框
        enabledCheckbox = JCheckBox("启用").apply {
            addActionListener {
                if (!context.ignoreUpdate) {
                    context.selectedCommandBar?.enabled = isSelected
                    context.notifyCommandBarListChanged()
                }
            }
        }

        // Name 字段 + 启用复选框（同一行）
        val namePanel = JPanel(BorderLayout())
        namePanel.add(JLabel("名称:"), BorderLayout.WEST)
        nameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateName()
                override fun removeUpdate(e: DocumentEvent?) = updateName()
                override fun changedUpdate(e: DocumentEvent?) = updateName()
            })
        }
        namePanel.add(nameField, BorderLayout.CENTER)
        namePanel.add(enabledCheckbox, BorderLayout.EAST)
        panel.add(namePanel)

        // Icon 字段
        panel.add(createIconFieldPanel())

        // Command 字段
        panel.add(createDirectCommandPanel())
        panel.add(createCommandHintPanel())

        // Terminal Name 字段（仅直接命令模式时显示）
        terminalNamePanel = createTerminalNamePanel()
        panel.add(terminalNamePanel)

        // Terminal Tab Prefix 字段（仅直接命令模式时显示）
        terminalTabPrefixPanel = createTerminalTabPrefixPanel()
        panel.add(terminalTabPrefixPanel)

        // Terminal Mode 字段（仅直接命令模式时显示）
        terminalModePanel = createTerminalModePanel()
        panel.add(terminalModePanel)

        // Working Directory 字段（仅直接命令模式时显示）
        workingDirPanel = createWorkingDirPanel()
        panel.add(workingDirPanel)

        // Common Environment Variables 字段（始终显示）
        panel.add(createCommonEnvVariablesPanel())

        // Environment Variables 字段（仅直接命令模式时显示）
        envVariablesPanel = createEnvVariablesPanel()
        panel.add(envVariablesPanel)

        // 简易模式复选框（仅 Command 列表模式时显示）
        simpleModePanel = createSimpleModePanel()
        panel.add(simpleModePanel)

        // Common QuickParams 面板（仅 Command 列表模式且非简易模式时显示）
        commonQuickParamsPanel = createCommonQuickParamsPanel()
        panel.add(commonQuickParamsPanel)

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 设置 Command 面板引用（用于控制显示/隐藏）
     */
    fun setCommandPanelRef(panel: JComponent) {
        commandPanel = panel
    }

    /**
     * 加载 CommandBar 数据到表单
     */
    fun loadData(commandBar: CommandBarConfig?) {
        if (commandBar == null) {
            clearData()
            return
        }

        context.ignoreUpdate = true
        try {
            enabledCheckbox.isSelected = commandBar.enabled
            nameField.text = commandBar.name
            iconField.text = commandBar.icon
            commandField.text = commandBar.command
            commonEnvVariablesField.text = commandBar.commonEnvVariables
            envVariablesField.text = commandBar.envVariables
            workingDirField.text = commandBar.workingDirectory
            terminalNameField.text = commandBar.defaultTerminalName
            terminalModeCheckbox.isSelected = commandBar.terminalMode == TerminalMode.EDITOR
            terminalTabPrefixField.text = commandBar.terminalTabPrefix
            showPrefixInEditorCheckbox.isSelected = commandBar.showPrefixInEditor
            showPrefixInTerminalCheckbox.isSelected = commandBar.showPrefixInTerminal
            simpleModeCheckbox.isSelected = commandBar.simpleMode

            // 更新公共快捷参数摘要
            updateCommonQuickParamsSummary(commandBar)

            // 更新直接命令模式相关字段的显示状态
            updateDirectCommandModeVisibility(commandBar)
        } finally {
            context.ignoreUpdate = false
        }
    }

    /**
     * 清空表单数据
     */
    fun clearData() {
        context.ignoreUpdate = true
        try {
            enabledCheckbox.isSelected = true
            nameField.text = ""
            iconField.text = ""
            commandField.text = ""
            commonEnvVariablesField.text = ""
            envVariablesField.text = ""
            workingDirField.text = ""
            terminalNameField.text = ""
            terminalModeCheckbox.isSelected = false
            terminalTabPrefixField.text = ""
            showPrefixInEditorCheckbox.isSelected = true
            showPrefixInTerminalCheckbox.isSelected = true
            simpleModeCheckbox.isSelected = false

            if (::commonQuickParamsSummaryField.isInitialized) {
                commonQuickParamsSummaryField.text = ""
            }

            updateCommandHintVisibility()
            updateWorkDirHintVisibility()
        } finally {
            context.ignoreUpdate = false
        }
    }

    private fun createIconFieldPanel(): JPanel {
        val iconPanel = JPanel(BorderLayout())
        iconPanel.add(JLabel("图标:"), BorderLayout.WEST)

        iconField = TextFieldWithBrowseButton().apply {
            (textField as? JBTextField)?.emptyText?.text = "builtin:AllIcons.Actions.Execute"
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateIcon()
                override fun removeUpdate(e: DocumentEvent?) = updateIcon()
                override fun changedUpdate(e: DocumentEvent?) = updateIcon()
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

        val builtinIconBtn = JButton(AllIcons.General.ArrowDown).apply {
            toolTipText = "选择内置图标"
            isBorderPainted = true
            isFocusPainted = false
            margin = JBUI.insets(0, 2)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val size = preferredSize.height
            preferredSize = Dimension(size, size)
            addActionListener {
                val popup = BuiltinIconSelector.createPopup(
                    onIconSelected = { iconPath -> iconField.text = iconPath },
                    currentIconPath = iconField.text
                )
                popup.showUnderneathOf(this)
            }
        }

        val iconFieldPanel = JPanel(BorderLayout()).apply {
            add(iconField, BorderLayout.CENTER)
            add(builtinIconBtn, BorderLayout.EAST)
        }

        iconPanel.add(iconFieldPanel, BorderLayout.CENTER)
        return iconPanel
    }

    private fun createDirectCommandPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("直接命令:"), BorderLayout.WEST)
        commandHintLabel = JLabel("输入直接命令后将不支持绑定Command 列表").apply {
            foreground = JBColor.GRAY
        }
        commandField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateCommand()
                    updateCommandHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateCommand()
                    updateCommandHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateCommand()
                    updateCommandHintVisibility()
                }
            })
        }
        panel.add(commandField, BorderLayout.CENTER)
        return panel
    }

    private fun createCommandHintPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(Box.createHorizontalStrut(JLabel("直接命令:").preferredSize.width), BorderLayout.WEST)
        panel.add(commandHintLabel, BorderLayout.CENTER)
        return panel
    }

    private fun createTerminalNamePanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("默认终端窗口名称:"), BorderLayout.WEST)
        terminalNameField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateTerminalName()
                override fun removeUpdate(e: DocumentEvent?) = updateTerminalName()
                override fun changedUpdate(e: DocumentEvent?) = updateTerminalName()
            })
        }
        panel.add(terminalNameField, BorderLayout.CENTER)
        return panel
    }

    private fun createTerminalTabPrefixPanel(): JPanel {
        val outerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 主行：标签 + 输入框 + 复选框
        val mainRow = JPanel(BorderLayout())
        mainRow.add(JLabel("终端标签页前缀:"), BorderLayout.WEST)

        val fieldPanel = JPanel(BorderLayout())
        terminalTabPrefixField = JBTextField().apply {
            (this as? JBTextField)?.emptyText?.text = "如: [CC] 或 🤖"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateTerminalTabPrefix()
                override fun removeUpdate(e: DocumentEvent?) = updateTerminalTabPrefix()
                override fun changedUpdate(e: DocumentEvent?) = updateTerminalTabPrefix()
            })
        }
        fieldPanel.add(terminalTabPrefixField, BorderLayout.CENTER)

        // 复选框面板
        val checkboxPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
        showPrefixInEditorCheckbox = JCheckBox("编辑器").apply {
            toolTipText = "在编辑器标签页显示前缀"
            isSelected = true
            addActionListener {
                if (!context.ignoreUpdate) updateShowPrefixInEditor()
            }
        }
        showPrefixInTerminalCheckbox = JCheckBox("终端窗口").apply {
            toolTipText = "在终端工具窗口标签页显示前缀"
            isSelected = true
            addActionListener {
                if (!context.ignoreUpdate) updateShowPrefixInTerminal()
            }
        }
        checkboxPanel.add(showPrefixInEditorCheckbox)
        checkboxPanel.add(Box.createHorizontalStrut(4))
        checkboxPanel.add(showPrefixInTerminalCheckbox)

        fieldPanel.add(checkboxPanel, BorderLayout.EAST)
        mainRow.add(fieldPanel, BorderLayout.CENTER)
        outerPanel.add(mainRow)

        // 提示行
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("终端标签页前缀:").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(JLabel("前缀显示在终端标签页名称之前").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
        outerPanel.add(hintPanel)

        return outerPanel
    }

    private fun createTerminalModePanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val row = JPanel(BorderLayout())
        terminalModeCheckbox = JCheckBox("在编辑器中打开").apply {
            addActionListener {
                if (!context.ignoreUpdate) updateTerminalMode()
            }
        }
        row.add(terminalModeCheckbox, BorderLayout.WEST)
        panel.add(row)

        val hintPanel = JPanel(BorderLayout())
        val spacer = Box.createHorizontalStrut(JCheckBox().preferredSize.width)
        hintPanel.add(spacer, BorderLayout.WEST)
        hintPanel.add(JLabel("默认通过终端工具窗口打开").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
        panel.add(hintPanel)
        return panel
    }

    private fun createWorkingDirPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val row = JPanel(BorderLayout())
        row.add(JLabel("工作目录:"), BorderLayout.WEST)
        workDirHintLabel = JLabel("留空时使用项目根路径").apply {
            foreground = JBColor.GRAY
        }
        workingDirField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择工作目录",
                "选择终端的工作目录",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            (textField as? JBTextField)?.emptyText?.text = context.projectPath ?: ""
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateWorkingDir()
                    updateWorkDirHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateWorkingDir()
                    updateWorkDirHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateWorkingDir()
                    updateWorkDirHintVisibility()
                }
            })
        }
        row.add(workingDirField, BorderLayout.CENTER)
        panel.add(row)

        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("工作目录:").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(workDirHintLabel, BorderLayout.CENTER)
        panel.add(hintPanel)
        return panel
    }

    private fun createCommonEnvVariablesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("环境变量(公共):"), BorderLayout.WEST)

        val fieldPanel = JPanel(BorderLayout())
        commonEnvVariablesField = JBTextField().apply {
            emptyText.text = "KEY1=val1;KEY2=val2"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateCommonEnvVariables()
                override fun removeUpdate(e: DocumentEvent?) = updateCommonEnvVariables()
                override fun changedUpdate(e: DocumentEvent?) = updateCommonEnvVariables()
            })
        }
        fieldPanel.add(commonEnvVariablesField, BorderLayout.CENTER)

        val editButton = JButton("...").apply {
            toolTipText = "编辑公共环境变量"
            addActionListener {
                val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                val dialog = EnvVariablesDialog(currentProject, commonEnvVariablesField.text)
                if (dialog.showAndGet()) {
                    commonEnvVariablesField.text = dialog.envVariablesText
                }
            }
        }
        fieldPanel.add(editButton, BorderLayout.EAST)
        panel.add(fieldPanel, BorderLayout.CENTER)

        // 提示标签
        val outerPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        outerPanel.add(panel)
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("环境变量(公共):").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(JLabel("对直接命令和命令列表中的每个命令都生效").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)
        outerPanel.add(hintPanel)
        return outerPanel
    }

    private fun createEnvVariablesPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("环境变量:"), BorderLayout.WEST)

        val fieldPanel = JPanel(BorderLayout())
        envVariablesField = JBTextField().apply {
            emptyText.text = "KEY1=val1;KEY2=val2"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateEnvVariables()
                override fun removeUpdate(e: DocumentEvent?) = updateEnvVariables()
                override fun changedUpdate(e: DocumentEvent?) = updateEnvVariables()
            })
        }
        fieldPanel.add(envVariablesField, BorderLayout.CENTER)

        val editButton = JButton("...").apply {
            toolTipText = "编辑环境变量"
            addActionListener {
                val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                val dialog = EnvVariablesDialog(currentProject, envVariablesField.text)
                if (dialog.showAndGet()) {
                    envVariablesField.text = dialog.envVariablesText
                }
            }
        }
        fieldPanel.add(editButton, BorderLayout.EAST)
        panel.add(fieldPanel, BorderLayout.CENTER)

        // 提示标签
        val outerPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        outerPanel.add(panel)
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("环境变量:").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(JLabel("若和公共环境变量同名，则覆盖公共环境变量").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)
        outerPanel.add(hintPanel)
        return outerPanel
    }

    private fun createSimpleModePanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val row = JPanel(BorderLayout())
        simpleModeCheckbox = JCheckBox("简易模式").apply {
            addActionListener {
                if (!context.ignoreUpdate) {
                    updateSimpleMode()
                    listener.onSimpleModeChanged(isSelected)
                }
            }
        }
        row.add(simpleModeCheckbox, BorderLayout.WEST)
        panel.add(row)

        val hintPanel = JPanel(BorderLayout())
        val spacer = Box.createHorizontalStrut(JCheckBox().preferredSize.width)
        hintPanel.add(spacer, BorderLayout.WEST)
        hintPanel.add(JLabel("简易模式下弹出菜单仅展示Command的图标和名称").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(font.size2D - 1f)
        }, BorderLayout.CENTER)
        panel.add(hintPanel)
        return panel
    }

    private fun createCommonQuickParamsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        val row = JPanel(BorderLayout())
        row.add(JLabel("快捷参数(公共):"), BorderLayout.WEST)

        val fieldPanel = JPanel(BorderLayout())
        commonQuickParamsSummaryField = JBTextField().apply {
            isEditable = false
        }
        fieldPanel.add(commonQuickParamsSummaryField, BorderLayout.CENTER)

        val editButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = "编辑公共快捷参数"
            addActionListener { openCommonQuickParamEditDialog() }
        }
        fieldPanel.add(editButton, BorderLayout.EAST)
        row.add(fieldPanel, BorderLayout.CENTER)
        panel.add(row)

        // 提示标签
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("快捷参数(公共):").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(JLabel("对命令列表中的所有命令生效，对直接命令无效").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)
        panel.add(hintPanel)
        return panel
    }

    // 数据更新方法
    private fun updateName() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.name = nameField.text
        context.notifyCommandBarListChanged()
    }

    private fun updateIcon() {
        if (context.ignoreUpdate) return
        val text = iconField.text
        context.selectedCommandBar?.icon = if (text.isBlank()) "builtin:AllIcons.Actions.Execute" else text
        context.notifyCommandBarListChanged()
    }

    private fun updateCommand() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.command = commandField.text
        updateDirectCommandModeVisibility(context.selectedCommandBar)
        listener.onDirectCommandModeChanged(context.selectedCommandBar?.isDirectCommandMode() == true)
    }

    private fun updateTerminalName() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.defaultTerminalName = terminalNameField.text
    }

    private fun updateTerminalMode() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.terminalMode =
            if (terminalModeCheckbox.isSelected) TerminalMode.EDITOR else TerminalMode.TOOL_WINDOW
    }

    private fun updateTerminalTabPrefix() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.terminalTabPrefix = terminalTabPrefixField.text
    }

    private fun updateShowPrefixInEditor() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.showPrefixInEditor = showPrefixInEditorCheckbox.isSelected
    }

    private fun updateShowPrefixInTerminal() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.showPrefixInTerminal = showPrefixInTerminalCheckbox.isSelected
    }

    private fun updateWorkingDir() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.workingDirectory = workingDirField.text
    }

    private fun updateCommonEnvVariables() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.commonEnvVariables = commonEnvVariablesField.text
    }

    private fun updateEnvVariables() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.envVariables = envVariablesField.text
    }

    private fun updateSimpleMode() {
        if (context.ignoreUpdate) return
        context.selectedCommandBar?.simpleMode = simpleModeCheckbox.isSelected
        updateSimpleModeVisibility(context.selectedCommandBar)
    }

    // 可见性控制方法
    private fun updateCommandHintVisibility() {
        commandHintLabel.text = if (commandField.text.isBlank()) {
            "输入直接命令后将不支持绑定Command 列表"
        } else {
            ""
        }
    }

    private fun updateWorkDirHintVisibility() {
        workDirHintLabel.text = if (workingDirField.text.isBlank()) {
            "留空时使用项目根路径"
        } else {
            ""
        }
    }

    private fun updateDirectCommandModeVisibility(commandBar: CommandBarConfig?) {
        val isDirectMode = commandBar?.isDirectCommandMode() == true
        envVariablesPanel.isVisible = isDirectMode
        workingDirPanel.isVisible = isDirectMode
        terminalNamePanel.isVisible = isDirectMode
        terminalModePanel.isVisible = isDirectMode
        terminalTabPrefixPanel.isVisible = isDirectMode
        simpleModePanel.isVisible = !isDirectMode
        if (::commandPanel.isInitialized) {
            commandPanel.isVisible = !isDirectMode
        }
        if (!isDirectMode) {
            updateSimpleModeVisibility(commandBar)
        }
    }

    private fun updateSimpleModeVisibility(commandBar: CommandBarConfig?) {
        val isSimple = commandBar?.simpleMode == true
        commonQuickParamsPanel.isVisible = !isSimple
    }

    private fun updateCommonQuickParamsSummary(commandBar: CommandBarConfig?) {
        if (::commonQuickParamsSummaryField.isInitialized) {
            val names = commandBar?.commonQuickParams?.filter { it.enabled }?.map { it.name } ?: emptyList()
            commonQuickParamsSummaryField.text = if (names.isEmpty()) "" else names.joinToString(" | ")
        }
    }

    private fun openCommonQuickParamEditDialog() {
        val commandBar = context.selectedCommandBar ?: return
        val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        val deepCopy = commandBar.commonQuickParams.map { it.deepCopy() }
        val dialog = site.laomst.ccbar.settings.ui.QuickParamEditDialog(currentProject, deepCopy)
        if (dialog.showAndGet()) {
            val edited = dialog.getEditedQuickParams()
            commandBar.commonQuickParams.clear()
            commandBar.commonQuickParams.addAll(edited)
            updateCommonQuickParamsSummary(commandBar)
        }
    }
}
