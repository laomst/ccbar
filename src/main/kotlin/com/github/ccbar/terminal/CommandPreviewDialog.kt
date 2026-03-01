package com.github.ccbar.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 命令预览与参数配置对话框
 * 两行布局：
 * - 第一行：终端标签名称输入框
 * - 第二行：基础命令文本（只读）+ 追加参数输入框
 */
class CommandPreviewDialog(
    project: Project?,
    private val baseCommand: String,
    private val defaultTerminalName: String,
    private val defaultOpenInEditor: Boolean = false
) : DialogWrapper(project) {

    // 终端名称输入框
    private val terminalNameField = JBTextField(defaultTerminalName).apply {
        preferredSize = Dimension(350, preferredSize.height)
    }

    // 在编辑器中打开复选框
    private val editorModeCheckBox = JCheckBox("在编辑器中打开").apply {
        isSelected = defaultOpenInEditor
    }

    // 追加参数输入框
    private val additionalParamsField = JBTextField().apply {
        emptyText.text = "追加参数..."
        preferredSize = Dimension(200, preferredSize.height)
    }

    /**
     * 获取完整命令（基础命令 + 追加参数）
     */
    val fullCommand: String
        get() {
            val additional = additionalParamsField.text.trim()
            return if (additional.isNotEmpty()) {
                "$baseCommand $additional"
            } else {
                baseCommand
            }
        }

    /**
     * 获取终端名称
     */
    val terminalName: String
        get() = terminalNameField.text.trim()

    /**
     * 获取是否在编辑器中打开
     */
    val openInEditor: Boolean
        get() = editorModeCheckBox.isSelected

    init {
        title = "命令预览与参数配置"
        setOKButtonText("执行")
        setCancelButtonText("取消")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(10)
        }

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        // 第一行：终端标签名称 + 在编辑器中打开复选框
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JBLabel("终端标签名称:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.gridwidth = 1
        panel.add(terminalNameField, gbc)

        gbc.gridx = 2
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        panel.add(editorModeCheckBox, gbc)

        // 第二行：基础命令 + 追加参数输入框
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.gridwidth = 1
        panel.add(JBLabel("命令:"), gbc)

        // 基础命令文本（只读）
        gbc.gridx = 1
        gbc.gridy = 1
        gbc.weightx = 0.0
        val baseCommandLabel = JBLabel(baseCommand).apply {
            border = JBUI.Borders.emptyRight(8)
        }
        panel.add(baseCommandLabel, gbc)

        // 追加参数输入框
        gbc.gridx = 2
        gbc.gridy = 1
        gbc.weightx = 1.0
        panel.add(additionalParamsField, gbc)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return terminalNameField
    }

    override fun doOKAction() {
        if (terminalName.isBlank()) {
            terminalNameField.requestFocus()
            return
        }
        super.doOKAction()
    }
}
