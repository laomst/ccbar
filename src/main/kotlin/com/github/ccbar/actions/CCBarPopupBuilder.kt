package com.github.ccbar.actions

import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.settings.OptionConfig
import com.github.ccbar.settings.OptionType
import com.github.ccbar.settings.SubButtonConfig
import com.github.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.BoxLayout
import javax.swing.SwingConstants
import javax.swing.UIManager

/**
 * CCBar 弹出菜单构建器
 * 构建包含 Option 行和内联 SubButton 的弹出面板
 * 两行布局：第一行 命令预览 | 名称，第二行 子按钮列表（小号文字）
 */
object CCBarPopupBuilder {

    // 命令预览最大宽度
    private const val MAX_PREVIEW_WIDTH = 400

    // 第一行高度
    private const val ROW_HEIGHT = 28

    // 第二行高度（子按钮行，小号文字）
    private const val SUB_ROW_HEIGHT = 22

    // 悬浮高亮圆角半径
    private const val HOVER_ARC = 8

    // 命令预览文字颜色
    private val PREVIEW_FOREGROUND: Color
        get() = JBColor(Color(80, 80, 80), Color(180, 180, 180))

    // 子按钮文字颜色（绿色调）
    private val SUB_BUTTON_FOREGROUND: Color
        get() = JBColor(Color(0, 120, 0), Color(0, 170, 0))

    // 子按钮分隔符颜色
    private val SUB_BUTTON_SEPARATOR_COLOR: Color
        get() = JBColor(Color(180, 180, 180), Color(100, 100, 100))

    // 标签文字颜色
    private val LABEL_FOREGROUND: Color
        get() = JBColor(Color(100, 100, 100), Color(160, 160, 160))

    // 悬浮高亮背景色（蓝色系）
    private val HOVER_BACKGROUND: Color
        get() = UIManager.getColor("List.selectionBackground")
            ?: JBColor(Color(47, 101, 202, 40), Color(75, 110, 175, 60))

    /**
     * 构建弹出菜单
     */
    fun buildPopup(project: Project, buttonConfig: ButtonConfig): JBPopup {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            // 使用工具栏/头部区域的背景色
            background = UIManager.getColor("MainToolbar.background") ?: JBColor.PanelBackground
        }

        val simpleMode = buttonConfig.simpleMode

        // 计算所有选项标题的最大宽度
        val labelWidth = calculateMaxLabelWidth(buttonConfig)

        // 计算所有选项命令预览的最大宽度（简易模式下不需要）
        val previewWidth = if (simpleMode) 0 else calculateMaxPreviewWidth(buttonConfig)

        // 先创建 popup，后续在回调中使用
        lateinit var popup: JBPopup

        // 为每个 Option 创建一行
        for (option in buttonConfig.options) {
            if (option.isSeparator()) {
                mainPanel.add(createSeparatorRow(option))
            } else if (simpleMode) {
                val row = createSimpleOptionRow(project, option, labelWidth) { popup.closeOk(null) }
                mainPanel.add(row)
            } else {
                val optionBlock = createOptionBlock(project, option, labelWidth, previewWidth) { popup.closeOk(null) }
                mainPanel.add(optionBlock)
            }
            // 添加行间距
            mainPanel.add(Box.createVerticalStrut(6))
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setMovable(false)
            .setResizable(false)
            .setShowBorder(true)
            .createPopup()

        return popup
    }

    /**
     * 创建支持悬浮高亮的圆角面板容器
     * 鼠标进入时绘制圆角矩形背景，离开时恢复透明
     */
    private fun createHoverPanel(onClick: () -> Unit): JPanel {
        var hovered = false

        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                if (hovered) {
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.color = HOVER_BACKGROUND
                    g2d.fillRoundRect(0, 0, width, height, HOVER_ARC, HOVER_ARC)
                }
                super.paintComponent(g)
            }
        }

        panel.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(4, 8)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    onClick()
                }

                override fun mouseEntered(e: MouseEvent?) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent?) {
                    hovered = false
                    repaint()
                }
            })
        }

        return panel
    }

    /**
     * 计算所有选项标题的最大宽度
     */
    private fun calculateMaxLabelWidth(buttonConfig: ButtonConfig): Int {
        val fontMetrics = JBLabel().getFontMetrics(JBLabel().font)
        var maxWidth = 60 // 最小宽度

        for (option in buttonConfig.options) {
            if (!option.isSeparator()) {
                val textWidth = fontMetrics.stringWidth(option.name)
                val totalWidth = textWidth + 16
                if (totalWidth > maxWidth) {
                    maxWidth = totalWidth
                }
            }
        }

        return maxWidth
    }

    /**
     * 计算所有选项命令预览的最大宽度
     */
    private fun calculateMaxPreviewWidth(buttonConfig: ButtonConfig): Int {
        val fontMetrics = JBLabel().getFontMetrics(JBLabel().font)
        var maxWidth = 0

        for (option in buttonConfig.options) {
            if (!option.isSeparator()) {
                // 计算基础命令宽度
                var textWidth = fontMetrics.stringWidth(option.baseCommand)
                // 同时考虑带参数的完整命令宽度
                for (subButton in option.subButtons) {
                    val fullCommand = buildFullCommand(option.baseCommand, subButton.params)
                    val fullWidth = fontMetrics.stringWidth(fullCommand)
                    if (fullWidth > textWidth) {
                        textWidth = fullWidth
                    }
                }
                val totalWidth = textWidth + 16
                if (totalWidth > maxWidth) {
                    maxWidth = totalWidth
                }
            }
        }

        return maxWidth.coerceAtMost(MAX_PREVIEW_WIDTH)
    }

    /**
     * 创建分割线行
     */
    private fun createSeparatorRow(option: OptionConfig): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
        }

        if (option.name.isNotBlank()) {
            val innerPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.color = JBColor.GRAY
                    g2d.stroke = java.awt.BasicStroke(1f)

                    val labelWidth = graphics.getFontMetrics(font).stringWidth(option.name) + 16
                    val centerY = height / 2
                    val leftEnd = (width - labelWidth) / 2
                    val rightStart = leftEnd + labelWidth

                    if (leftEnd > 0) {
                        g2d.drawLine(0, centerY, leftEnd, centerY)
                    }
                    if (rightStart < width) {
                        g2d.drawLine(rightStart, centerY, width, centerY)
                    }
                }
            }
            innerPanel.layout = BorderLayout()
            innerPanel.isOpaque = false

            val label = JLabel(option.name).apply {
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.CENTER
            }
            innerPanel.add(label, BorderLayout.CENTER)
            innerPanel.preferredSize = Dimension(innerPanel.preferredSize.width, 24)
            panel.add(innerPanel, BorderLayout.CENTER)
        } else {
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

        return panel
    }

    /**
     * 创建简易模式的选项行（仅显示名称，整行悬浮高亮）
     */
    private fun createSimpleOptionRow(project: Project, option: OptionConfig, labelWidth: Int, onClose: () -> Unit): JPanel {
        val hoverPanel = createHoverPanel {
            onClose()
            CCBarTerminalService.openTerminal(project, option, null)
        }
        hoverPanel.toolTipText = option.baseCommand

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        val label = JBLabel(option.name).apply {
            preferredSize = Dimension(labelWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = JBColor.foreground()
        }

        row.add(label)
        hoverPanel.add(row)
        return hoverPanel
    }

    /**
     * 创建 Option 块（两行布局，整块悬浮高亮）
     * 第一行：命令预览 | 名称
     * 第二行：子按钮列表（小号文字）
     */
    private fun createOptionBlock(project: Project, option: OptionConfig, labelWidth: Int, previewWidth: Int, onClose: () -> Unit): JPanel {
        val hoverPanel = createHoverPanel {
            onClose()
            CCBarTerminalService.openTerminal(project, option, null)
        }

        // 命令预览标签（需要被子按钮 hover 更新）
        val commandPreview = JBLabel(option.baseCommand).apply {
            preferredSize = Dimension(previewWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = PREVIEW_FOREGROUND
        }

        // 第一行：命令预览 | 名称
        val firstRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }
        val optionLabel = JBLabel(option.name).apply {
            preferredSize = Dimension(labelWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = LABEL_FOREGROUND
            border = JBUI.Borders.emptyLeft(8)
        }
        firstRow.add(commandPreview)
        firstRow.add(optionLabel)
        hoverPanel.add(firstRow)

        // 第二行：子按钮列表（仅在有子按钮时显示）
        if (option.subButtons.isNotEmpty()) {
            val secondRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(4)
            }

            for ((index, subButton) in option.subButtons.withIndex()) {
                val btn = createSubButtonLabel(project, option, subButton, commandPreview, onClose)
                secondRow.add(btn)
                if (index < option.subButtons.size - 1) {
                    val separator = JBLabel("|").apply {
                        foreground = SUB_BUTTON_SEPARATOR_COLOR
                        font = font.deriveFont(font.size2D - 2f)
                        preferredSize = Dimension(preferredSize.width, SUB_ROW_HEIGHT)
                    }
                    secondRow.add(separator)
                }
            }

            hoverPanel.add(secondRow)
        }

        return hoverPanel
    }

    /**
     * 创建 SubButton 标签（小号纯文本）
     * 点击子按钮会消费事件，不触发 hoverPanel 的点击
     */
    private fun createSubButtonLabel(
        project: Project,
        option: OptionConfig,
        subButton: SubButtonConfig,
        commandPreview: JBLabel,
        onClose: () -> Unit
    ): JBLabel {
        val fullCommand = buildFullCommand(option.baseCommand, subButton.params)

        return JBLabel(subButton.name).apply {
            font = font.deriveFont(font.size2D - 2f)
            foreground = SUB_BUTTON_FOREGROUND
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "执行: $fullCommand"
            border = JBUI.Borders.empty(0, 4)
            preferredSize = Dimension(preferredSize.width, SUB_ROW_HEIGHT)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    e?.consume()
                    onClose()
                    CCBarTerminalService.openTerminal(project, option, subButton)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor.BLUE
                    commandPreview.text = fullCommand
                }

                override fun mouseExited(e: MouseEvent?) {
                    foreground = SUB_BUTTON_FOREGROUND
                    commandPreview.text = option.baseCommand
                }
            })
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
