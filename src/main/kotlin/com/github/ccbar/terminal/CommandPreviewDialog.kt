package com.github.ccbar.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager

/**
 * 命令预览与参数配置对话框
 * 三行布局：
 * - 第一行：[图标+前缀+名称一体化输入框] + 在编辑器中打开复选框
 * - 第二行：环境变量输入框 + [...] CommandBar
 * - 第三行：命令输入框（可自由编辑）
 */
class CommandPreviewDialog(
    private val project: Project?,
    private val baseCommand: String,
    private val defaultTerminalName: String,
    private val defaultOpenInEditor: Boolean = false,
    private val defaultEnvVariables: String = "",
    private val icon: Icon? = null,
    private val terminalTabPrefix: String = "",
    private val showPrefixInEditor: Boolean = true,
    private val showPrefixInTerminal: Boolean = true
) : DialogWrapper(project) {

    // 图标标签（仅编辑器模式显示）
    private val iconLabel = JLabel(icon).apply {
        preferredSize = Dimension(16, 16)
        minimumSize = Dimension(16, 16)
        isVisible = false
    }

    // 前缀标签（根据开关状态显示，使用次要色与输入文字区分）
    private val prefixLabel = JLabel(terminalTabPrefix).apply {
        isVisible = false
        foreground = UIManager.getColor("TextField.placeholderForeground")
            ?: UIManager.getColor("Label.disabledForeground")
            ?: java.awt.Color(150, 150, 150)
    }

    // 裸文本输入框（无边框，嵌入复合容器中）
    private val terminalNameField = JTextField(defaultTerminalName)

    // 复合输入框容器（图标 + 前缀 + 输入框，共用一个边框）
    private val terminalNameComposite: JPanel = buildCompositeField()

    // 在编辑器中打开复选框
    private val editorModeCheckBox = JCheckBox("在编辑器中打开").apply {
        isSelected = defaultOpenInEditor
    }

    // 环境变量输入框
    private val envVariablesField = JBTextField(defaultEnvVariables).apply {
        preferredSize = Dimension(350, preferredSize.height)
        (this as? JBTextField)?.emptyText?.text = "KEY1=val1;KEY2=val2"
    }

    // 命令输入框（基础命令 + 末尾空格，用户可自由编辑）
    private val commandField = JBTextField("$baseCommand ").apply {
        preferredSize = Dimension(350, preferredSize.height)
    }

    /**
     * 构建图标+前缀+输入框一体化的复合控件。
     * 外层面板使用和 JTextField 相同的边框，内部去掉输入框自身边框，
     * 聚焦/失焦时切换高亮边框，营造整体输入框的视觉效果。
     */
    private fun buildCompositeField(): JPanel {
        // 去掉输入框自身边框和背景，融入容器；去掉左内边距使其与前缀标签无缝衔接
        terminalNameField.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        terminalNameField.margin = Insets(0, 0, 0, 0)
        terminalNameField.isOpaque = false

        // 前缀区域：图标 + 前缀文字，水平排列
        // 整体左侧留 2px 内边距，与下方标准输入框的文字起始位置对齐
        val prefixPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 2, 0, 0)
            val g = GridBagConstraints()
            g.fill = GridBagConstraints.VERTICAL
            g.anchor = GridBagConstraints.WEST

            g.gridx = 0
            g.insets = Insets(0, 0, 0, 2)  // 图标右侧 2px 间距
            add(iconLabel, g)

            g.gridx = 1
            g.insets = Insets(0, 0, 0, 0)  // 前缀紧贴输入框，无右间距
            add(prefixLabel, g)
        }

        // 外层容器：左放前缀区域，中放输入框，使用 TextField 的标准边框
        val composite = object : JPanel(BorderLayout()) {
            // 同步背景色为输入框背景
            override fun updateUI() {
                super.updateUI()
                background = UIManager.getColor("TextField.background")
            }
        }.apply {
            background = UIManager.getColor("TextField.background")
            // 使用标准 TextField 边框
            border = UIManager.getBorder("TextField.border")
                ?: BorderFactory.createLineBorder(UIManager.getColor("TextField.borderColor") ?: java.awt.Color.GRAY)
        }

        composite.add(prefixPanel, BorderLayout.WEST)
        composite.add(terminalNameField, BorderLayout.CENTER)

        // 聚焦时切换高亮边框（模拟 IDE 边框高亮效果）
        val normalBorder = composite.border
        val focusBorder = UIManager.getBorder("TextField.focusBorder") ?: normalBorder

        terminalNameField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                composite.border = focusBorder
                composite.repaint()
            }
            override fun focusLost(e: FocusEvent) {
                composite.border = normalBorder
                composite.repaint()
            }
        })

        // 点击前缀区域时把焦点转给输入框
        val clickToFocus = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { terminalNameField.requestFocusInWindow() }
        }
        composite.addMouseListener(clickToFocus)
        prefixPanel.addMouseListener(clickToFocus)
        iconLabel.addMouseListener(clickToFocus)
        prefixLabel.addMouseListener(clickToFocus)

        return composite
    }

    val fullCommand: String get() = commandField.text.trim()
    val terminalName: String get() = terminalNameField.text.trim()
    val openInEditor: Boolean get() = editorModeCheckBox.isSelected
    val envVariables: String get() = envVariablesField.text.trim()

    init {
        title = "命令预览与参数配置"
        setOKButtonText("执行")
        setCancelButtonText("取消")
        editorModeCheckBox.addItemListener { updatePrefixAndIconVisibility() }
        init()
    }

    /**
     * 根据打开模式动态更新图标和前缀的显示状态
     */
    private fun updatePrefixAndIconVisibility() {
        val openInEditor = editorModeCheckBox.isSelected

        iconLabel.isVisible = openInEditor && icon != null

        val showPrefix = if (openInEditor) showPrefixInEditor else showPrefixInTerminal
        prefixLabel.isVisible = showPrefix && terminalTabPrefix.isNotBlank()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(10)
        }

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        // 第一行：标签名称（一体化复合输入框）+ 在编辑器中打开复选框
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        gbc.anchor = GridBagConstraints.EAST
        panel.add(JBLabel("标签名称:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.gridwidth = 3
        gbc.anchor = GridBagConstraints.WEST
        panel.add(terminalNameComposite, gbc)

        gbc.gridx = 4
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        panel.add(editorModeCheckBox, gbc)

        // 第二行：环境变量
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        gbc.anchor = GridBagConstraints.EAST
        panel.add(JBLabel("环境变量:"), gbc)

        val envPanel = JPanel(GridBagLayout())
        val envGbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 0, 0)
        }
        envGbc.gridx = 0
        envGbc.weightx = 1.0
        envPanel.add(envVariablesField, envGbc)

        envGbc.gridx = 1
        envGbc.weightx = 0.0
        envGbc.insets = Insets(0, 4, 0, 0)
        val editEnvButton = JButton("...").apply {
            toolTipText = "编辑环境变量"
            addActionListener {
                val dialog = EnvVariablesDialog(project, envVariablesField.text)
                if (dialog.showAndGet()) {
                    envVariablesField.text = dialog.envVariablesText
                }
            }
        }
        envPanel.add(editEnvButton, envGbc)

        gbc.gridx = 1
        gbc.gridy = 1
        gbc.weightx = 1.0
        gbc.gridwidth = 4
        gbc.anchor = GridBagConstraints.WEST
        panel.add(envPanel, gbc)

        // 第三行：命令输入框
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        gbc.anchor = GridBagConstraints.EAST
        panel.add(JBLabel("命令详情:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 2
        gbc.weightx = 1.0
        gbc.gridwidth = 4
        gbc.anchor = GridBagConstraints.WEST
        panel.add(commandField, gbc)

        // 初始化图标和前缀的显示状态
        updatePrefixAndIconVisibility()

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = terminalNameField

    override fun doOKAction() {
        if (terminalName.isBlank()) {
            terminalNameField.requestFocus()
            return
        }
        super.doOKAction()
    }
}
