package com.github.ccbar.actions

import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.settings.OptionConfig
import com.github.ccbar.settings.SubButtonConfig
import com.github.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.BoxLayout
import javax.swing.SwingConstants

/**
 * CCBar 弹出菜单构建器
 * 构建包含 Option 行和内联 SubButton 的弹出面板
 * 三列布局：选项名称 | 命令预览输入框 | 子按钮列表
 */
object CCBarPopupBuilder {

    // 输入框背景色
    private val FIELD_BACKGROUND: Color
        get() = JBColor(Color(255, 255, 255), Color(60, 63, 65))

    // 输入框边框颜色
    private val FIELD_BORDER_COLOR: Color
        get() = JBColor(Color(200, 200, 200), Color(85, 85, 85))

    // 按钮背景色
    private val BUTTON_BACKGROUND: Color
        get() = JBColor(Color(240, 240, 240), Color(75, 75, 75))

    // 按钮悬浮背景色
    private val BUTTON_HOVER_BACKGROUND: Color
        get() = JBColor(Color(220, 220, 220), Color(90, 90, 90))

    // 按钮文字颜色
    private val BUTTON_FOREGROUND: Color
        get() = JBColor(Color(60, 60, 60), Color(200, 200, 200))

    // 标签文字颜色
    private val LABEL_FOREGROUND: Color
        get() = JBColor(Color(100, 100, 100), Color(160, 160, 160))

    /**
     * 构建弹出菜单
     */
    fun buildPopup(project: Project, buttonConfig: ButtonConfig): JBPopup {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            background = JBColor.PanelBackground
        }

        // 为每个 Option 创建一行
        for (option in buttonConfig.options) {
            val optionRow = createOptionRow(project, option)
            mainPanel.add(optionRow)
            // 添加行间距
            mainPanel.add(Box.createVerticalStrut(8))
        }

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setMovable(false)
            .setResizable(false)
            .setShowBorder(true)
            .setTitle(buttonConfig.name)
            .createPopup()
    }

    /**
     * 创建 Option 行（表单样式）
     * 三列布局：选项名称 | 命令预览输入框 | 子按钮列表
     */
    private fun createOptionRow(project: Project, option: OptionConfig): JPanel {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        // 第一列：选项名称标签
        val optionLabel = createOptionLabel(project, option)
        rowPanel.add(optionLabel)

        // 第二列：命令预览输入框
        val commandPreview = createCommandPreviewField(project, option)
        rowPanel.add(commandPreview)

        // 第三列：子按钮
        for (subButton in option.subButtons) {
            val button = createSubButton(project, option, subButton, commandPreview)
            rowPanel.add(button)
        }

        return rowPanel
    }

    // 行高度
    private const val ROW_HEIGHT = 32

    /**
     * 创建选项名称标签
     */
    private fun createOptionLabel(project: Project, option: OptionConfig): JBLabel {
        return JBLabel(option.name).apply {
            preferredSize = Dimension(80, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = LABEL_FOREGROUND
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "点击执行: ${option.baseCommand}"
            border = JBUI.Borders.emptyRight(12)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    CCBarTerminalService.openTerminal(project, option, null)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor.BLUE
                }

                override fun mouseExited(e: MouseEvent?) {
                    foreground = LABEL_FOREGROUND
                }
            })
        }
    }

    /**
     * 创建命令预览输入框
     */
    private fun createCommandPreviewField(project: Project, option: OptionConfig): JTextField {
        return JTextField(option.baseCommand).apply {
            isEditable = false
            preferredSize = Dimension(200, ROW_HEIGHT)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "点击执行: ${option.baseCommand}"
            background = FIELD_BACKGROUND
            foreground = JBColor.foreground()
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FIELD_BORDER_COLOR, 1),
                JBUI.Borders.empty(6, 10)
            )

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    CCBarTerminalService.openTerminal(project, option, null)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.BLUE, 1),
                        JBUI.Borders.empty(6, 10)
                    )
                }

                override fun mouseExited(e: MouseEvent?) {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(FIELD_BORDER_COLOR, 1),
                        JBUI.Borders.empty(6, 10)
                    )
                }
            })
        }
    }

    /**
     * 创建 SubButton 按钮
     */
    private fun createSubButton(
        project: Project,
        option: OptionConfig,
        subButton: SubButtonConfig,
        commandPreview: JTextField
    ): JButton {
        val fullCommand = buildFullCommand(option.baseCommand, subButton.params)

        return JButton(subButton.name).apply {
            preferredSize = Dimension(getPreferredSize().width + 24, ROW_HEIGHT)
            margin = JBUI.insets(4, 12)
            background = BUTTON_BACKGROUND
            foreground = BUTTON_FOREGROUND
            isOpaque = true
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "执行: $fullCommand"

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    background = BUTTON_HOVER_BACKGROUND
                    commandPreview.text = fullCommand
                }

                override fun mouseExited(e: MouseEvent?) {
                    background = BUTTON_BACKGROUND
                    commandPreview.text = option.baseCommand
                }
            })

            addActionListener {
                CCBarTerminalService.openTerminal(project, option, subButton)
            }
        }
    }

    /**
     * 构建完整命令
     */
    private fun buildFullCommand(baseCommand: String, params: String?): String {
        return if (params.isNullOrBlank()) {
            baseCommand
        } else {
            "$baseCommand $params"
        }
    }
}
