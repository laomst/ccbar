package com.github.ccbar.settings.ui.panels

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.CommandBarConfig
import com.github.ccbar.settings.CommandConfig
import com.github.ccbar.settings.TerminalMode
import com.github.ccbar.settings.ui.BuiltinIconSelector
import com.github.ccbar.settings.ui.QuickParamEditDialog
import com.github.ccbar.settings.ui.shared.CommandDetailListener
import com.github.ccbar.settings.ui.shared.EditingContext
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.github.ccbar.terminal.EnvVariablesDialog
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
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Command 详情面板
 * 负责显示和编辑 Command 的详细信息
 */
class CommandDetailPanel(
    private val context: EditingContext,
    private val project: Project?,
    private val listener: CommandDetailListener
) {
    // UI 组件
    private lateinit var outerPanel: JPanel
    private lateinit var titledBorder: TitledBorder
    private lateinit var enabledCheckbox: JCheckBox
    private lateinit var nameField: JBTextField
    private lateinit var iconField: TextFieldWithBrowseButton
    private lateinit var baseCommandField: JBTextField
    private lateinit var terminalNameField: JBTextField
    private lateinit var terminalModeCheckbox: JCheckBox
    private lateinit var workingDirectoryField: TextFieldWithBrowseButton
    private lateinit var workDirHintLabel: JLabel
    private lateinit var envVariablesField: JBTextField
    private lateinit var quickParamSummaryField: JBTextField

    // 面板引用（用于控制显示/隐藏）
    private lateinit var enabledPanel: JComponent
    private lateinit var iconPanel: JPanel
    private lateinit var baseCommandPanel: JPanel
    private lateinit var terminalNamePanel: JPanel
    private lateinit var terminalModePanel: JPanel
    private lateinit var dirPanel: JPanel
    private lateinit var dirHintPanel: JPanel
    private lateinit var envVariablesPanel: JPanel
    private lateinit var quickParamOuterPanel: JComponent

    // 当前 CommandBar 引用（用于判断简易模式）
    private var currentCommandBar: CommandBarConfig? = null

    /**
     * 创建面板
     */
    fun createPanel(): JComponent {
        outerPanel = JPanel(BorderLayout())
        titledBorder = BorderFactory.createTitledBorder("Command 详情")
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = titledBorder
        }

        // 启用复选框
        enabledCheckbox = JCheckBox("启用").apply {
            addActionListener {
                if (!context.ignoreUpdate) {
                    context.selectedCommand?.enabled = isSelected
                    context.notifyCommandListChanged()
                }
            }
        }
        enabledPanel = enabledCheckbox

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
        iconPanel = createIconFieldPanel()
        panel.add(iconPanel)

        // Base Command 字段
        baseCommandPanel = createBaseCommandPanel()
        panel.add(baseCommandPanel)

        // Terminal Name 字段
        terminalNamePanel = createTerminalNamePanel()
        panel.add(terminalNamePanel)

        // Terminal Mode 字段
        terminalModePanel = createTerminalModePanel()
        panel.add(terminalModePanel)

        // Working Directory 字段
        dirPanel = createWorkingDirPanel()
        panel.add(dirPanel)
        dirHintPanel = createDirHintPanel()
        panel.add(dirHintPanel)

        // Environment Variables 字段
        envVariablesPanel = createEnvVariablesPanel()
        panel.add(envVariablesPanel)

        // QuickParam 列表
        quickParamOuterPanel = createQuickParamPanel()
        panel.add(quickParamOuterPanel)

        outerPanel.add(panel, BorderLayout.NORTH)
        return outerPanel
    }

    /**
     * 设置当前 CommandBar 引用
     */
    fun setCurrentCommandBar(commandBar: CommandBarConfig?) {
        currentCommandBar = commandBar
    }

    /**
     * 加载 Command 数据到表单
     */
    fun loadData(command: CommandConfig?) {
        if (command == null) {
            hidePanel()
            return
        }

        outerPanel.isVisible = true

        if (command.isSeparator()) {
            // 分割线：隐藏不需要的字段
            hideFieldsForSeparator()
        } else {
            // 普通 Command：显示所有字段
            showFieldsForCommand()
            loadDataToFields(command)
            updateQuickParamSummary(command)
        }
    }

    /**
     * 清空表单数据
     */
    fun clearData() {
        context.ignoreUpdate = true
        try {
            nameField.text = ""
            enabledCheckbox.isSelected = true
            iconField.text = ""
            baseCommandField.text = ""
            envVariablesField.text = ""
            workingDirectoryField.text = ""
            terminalNameField.text = ""
            terminalModeCheckbox.isSelected = false
            updateWorkDirHintVisibility()
        } finally {
            context.ignoreUpdate = false
        }
    }

    /**
     * 隐藏面板
     */
    fun hidePanel() {
        outerPanel.isVisible = false
    }

    /**
     * 更新快捷参数摘要
     */
    fun updateQuickParamSummary(command: CommandConfig?) {
        if (::quickParamSummaryField.isInitialized) {
            val names = command?.quickParams?.filter { it.enabled }?.map { it.name } ?: emptyList()
            quickParamSummaryField.text = if (names.isEmpty()) "" else names.joinToString(" | ")
        }
    }

    private fun loadDataToFields(command: CommandConfig) {
        context.ignoreUpdate = true
        try {
            nameField.text = command.name
            enabledCheckbox.isSelected = command.enabled
            iconField.text = command.icon
            baseCommandField.text = command.baseCommand
            envVariablesField.text = command.envVariables
            workingDirectoryField.text = command.workingDirectory
            terminalNameField.text = command.defaultTerminalName
            terminalModeCheckbox.isSelected = command.terminalMode == TerminalMode.EDITOR
            updateWorkDirHintVisibility()
        } finally {
            context.ignoreUpdate = false
        }
    }

    private fun hideFieldsForSeparator() {
        enabledPanel.isVisible = false
        iconPanel.isVisible = false
        baseCommandPanel.isVisible = false
        envVariablesPanel.isVisible = false
        dirPanel.isVisible = false
        dirHintPanel.isVisible = false
        terminalNamePanel.isVisible = false
        terminalModePanel.isVisible = false
        quickParamOuterPanel.isVisible = false
        titledBorder.title = "分割线详情"
    }

    private fun showFieldsForCommand() {
        enabledPanel.isVisible = true
        iconPanel.isVisible = true
        baseCommandPanel.isVisible = true
        envVariablesPanel.isVisible = true
        dirPanel.isVisible = true
        dirHintPanel.isVisible = true
        terminalNamePanel.isVisible = true
        terminalModePanel.isVisible = true

        // 简易模式下隐藏快捷参数面板
        val shouldShowQuickParams = currentCommandBar?.simpleMode != true
        quickParamOuterPanel.isVisible = shouldShowQuickParams

        titledBorder.title = "Command 详情"
    }

    private fun createIconFieldPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("图标:"), BorderLayout.WEST)

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

        panel.add(iconFieldPanel, BorderLayout.CENTER)
        return panel
    }

    private fun createBaseCommandPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("基础命令:"), BorderLayout.WEST)
        baseCommandField = JBTextField().apply {
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updateBaseCommand()
                override fun removeUpdate(e: DocumentEvent?) = updateBaseCommand()
                override fun changedUpdate(e: DocumentEvent?) = updateBaseCommand()
            })
        }
        panel.add(baseCommandField, BorderLayout.CENTER)
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
        val panel = JPanel(BorderLayout())
        panel.add(JLabel("工作目录:"), BorderLayout.WEST)
        workDirHintLabel = JLabel("留空时使用项目根路径").apply {
            foreground = JBColor.GRAY
        }
        workingDirectoryField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "选择工作目录",
                "选择终端的工作目录",
                null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            (textField as? JBTextField)?.emptyText?.text = context.projectPath ?: ""
            textField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) {
                    updateDirectory()
                    updateWorkDirHintVisibility()
                }
                override fun removeUpdate(e: DocumentEvent?) {
                    updateDirectory()
                    updateWorkDirHintVisibility()
                }
                override fun changedUpdate(e: DocumentEvent?) {
                    updateDirectory()
                    updateWorkDirHintVisibility()
                }
            })
        }
        panel.add(workingDirectoryField, BorderLayout.CENTER)
        return panel
    }

    private fun createDirHintPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(Box.createHorizontalStrut(JLabel("工作目录:").preferredSize.width), BorderLayout.WEST)
        panel.add(workDirHintLabel, BorderLayout.CENTER)
        return panel
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

    private fun createQuickParamPanel(): JComponent {
        val outerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(JLabel("快捷参数:"), BorderLayout.WEST)

        quickParamSummaryField = JBTextField().apply {
            isEditable = false
        }
        mainPanel.add(quickParamSummaryField, BorderLayout.CENTER)

        val editButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = "编辑快捷参数"
            addActionListener { openQuickParamEditDialog() }
        }
        mainPanel.add(editButton, BorderLayout.EAST)

        outerPanel.add(mainPanel)

        // 添加提示标签
        val hintPanel = JPanel(BorderLayout())
        hintPanel.add(Box.createHorizontalStrut(JLabel("快捷参数:").preferredSize.width), BorderLayout.WEST)
        hintPanel.add(JLabel("若和公共参数同名，则覆盖公共参数").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)
        outerPanel.add(hintPanel)

        return outerPanel
    }

    // 数据更新方法
    private fun updateName() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.name = nameField.text
        context.notifyCommandListChanged()
    }

    private fun updateIcon() {
        if (context.ignoreUpdate) return
        val text = iconField.text
        context.selectedCommand?.icon = if (text.isBlank()) "builtin:AllIcons.Actions.Execute" else text
        context.notifyCommandListChanged()
    }

    private fun updateBaseCommand() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.baseCommand = baseCommandField.text
    }

    private fun updateTerminalName() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.defaultTerminalName = terminalNameField.text
    }

    private fun updateTerminalMode() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.terminalMode =
            if (terminalModeCheckbox.isSelected) TerminalMode.EDITOR else TerminalMode.TOOL_WINDOW
    }

    private fun updateDirectory() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.workingDirectory = workingDirectoryField.text
    }

    private fun updateEnvVariables() {
        if (context.ignoreUpdate) return
        context.selectedCommand?.envVariables = envVariablesField.text
    }

    private fun updateWorkDirHintVisibility() {
        workDirHintLabel.text = if (workingDirectoryField.text.isBlank()) {
            "留空时使用项目根路径"
        } else {
            ""
        }
    }

    private fun openQuickParamEditDialog() {
        val command = context.selectedCommand ?: return
        val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        val deepCopy = command.quickParams.map { it.deepCopy() }
        val dialog = QuickParamEditDialog(currentProject, deepCopy)
        if (dialog.showAndGet()) {
            val edited = dialog.getEditedQuickParams()
            command.quickParams.clear()
            command.quickParams.addAll(edited)
            updateQuickParamSummary(command)
            listener.onQuickParamsUpdated()
        }
    }
}
