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
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.BoxLayout

/**
 * CCBar 弹出菜单构建器
 * 构建包含 Option 行和内联 SubButton 的弹出面板
 */
object CCBarPopupBuilder {

    /**
     * 构建弹出菜单
     */
    fun buildPopup(project: Project, buttonConfig: ButtonConfig): JBPopup {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            background = JBColor.PanelBackground
        }

        // 为每个 Option 创建一行
        for (option in buttonConfig.options) {
            val optionRow = createOptionRow(project, option)
            mainPanel.add(optionRow)
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
     * 创建 Option 行
     * 左侧 Option 名称（可点击），右侧内联 SubButton
     */
    private fun createOptionRow(project: Project, option: OptionConfig): JPanel {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            border = JBUI.Borders.empty(4, 0)
            isOpaque = false
        }

        // Option 名称标签（可点击）
        val optionLabel = JBLabel(option.name).apply {
            border = JBUI.Borders.emptyRight(8)
            foreground = JBColor.BLUE
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            toolTipText = "点击执行: ${option.baseCommand}"

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    // 点击 Option 名称执行 baseCommand（不带参数）
                    CCBarTerminalService.openTerminal(project, option, null)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor.RED
                }

                override fun mouseExited(e: MouseEvent?) {
                    foreground = JBColor.BLUE
                }
            })
        }
        rowPanel.add(optionLabel)

        // SubButton 按钮（内联显示）
        for (subButton in option.subButtons) {
            val subButtonComponent = createSubButton(project, option, subButton)
            rowPanel.add(subButtonComponent)
        }

        return rowPanel
    }

    /**
     * 创建 SubButton 按钮
     */
    private fun createSubButton(
        project: Project,
        option: OptionConfig,
        subButton: SubButtonConfig
    ): JButton {
        return JButton(subButton.name).apply {
            margin = JBUI.insets(2, 8)
            toolTipText = "执行: ${option.baseCommand} ${subButton.params}"

            addActionListener {
                // 点击 SubButton 执行 baseCommand + params
                CCBarTerminalService.openTerminal(project, option, subButton)
            }
        }
    }
}
