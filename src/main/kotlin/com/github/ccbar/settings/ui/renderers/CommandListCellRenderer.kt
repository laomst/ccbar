package com.github.ccbar.settings.ui.renderers

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.CommandConfig
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Command 列表渲染器
 * 支持普通 Command 和分割线的渲染
 */
class CommandListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        // 处理分割线类型
        if (value is CommandConfig && value.isSeparator()) {
            return createSeparatorRenderer(list, value, isSelected)
        }

        // 普通 Command 渲染
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CommandConfig) {
            text = value.name
            icon = if (value.icon.isNotBlank()) CCBarIcons.loadIcon(value.icon) else null
            if (!value.enabled && !isSelected) {
                foreground = JBColor.GRAY
            }
        }
        return component
    }

    private fun createSeparatorRenderer(
        list: JList<*>?,
        command: CommandConfig,
        isSelected: Boolean
    ): Component {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = if (isSelected) {
                list?.selectionBackground ?: JBColor.PanelBackground
            } else {
                list?.background ?: JBColor.PanelBackground
            }
            border = JBUI.Borders.empty(4, 8)
        }

        if (command.name.isNotBlank()) {
            // 带标题的分割线：──── 标题 ────
            val innerPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.color = JBColor.GRAY
                    g2d.stroke = java.awt.BasicStroke(1f)

                    val labelWidth = graphics.getFontMetrics(font).stringWidth(command.name) + 16
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

            val label = JLabel(command.name).apply {
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.CENTER
            }
            innerPanel.add(label, BorderLayout.CENTER)
            innerPanel.preferredSize = Dimension(innerPanel.preferredSize.width, 24)
            panel.add(innerPanel, BorderLayout.CENTER)
        } else {
            // 无标题分割线：使用自定义绘制确保垂直居中
            val separatorPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.color = JBColor.GRAY
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
