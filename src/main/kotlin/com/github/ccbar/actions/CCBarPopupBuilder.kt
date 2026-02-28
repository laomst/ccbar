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
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextField
import javax.swing.BoxLayout
import javax.swing.SwingConstants

/**
 * CCBar 弹出菜单构建器
 * 构建包含 Option 行和内联 SubButton 的弹出面板
 * 三列布局：选项名称 | 子按钮列表 | 命令预览输入框
 */
object CCBarPopupBuilder {

    // 命令预览框最大宽度
    private const val MAX_PREVIEW_WIDTH = 400

    // 行高度
    private const val ROW_HEIGHT = 32

    // 输入框背景色
    private val FIELD_BACKGROUND: Color
        get() = JBColor(Color(255, 255, 255), Color(60, 63, 65))

    // 输入框边框颜色
    private val FIELD_BORDER_COLOR: Color
        get() = JBColor(Color(200, 200, 200), Color(85, 85, 85))

    // 按钮绿色半透明叠加层 - 中心色（透明度低）
    private val BUTTON_OVERLAY_CENTER: Color
        get() = JBColor(Color(0, 140, 0, 8), Color(0, 180, 0, 10))

    // 按钮绿色半透明叠加层 - 边缘色（透明度高）
    private val BUTTON_OVERLAY_EDGE: Color
        get() = JBColor(Color(0, 140, 0, 35), Color(0, 180, 0, 45))

    // 按钮绿色半透明叠加层 - 悬浮中心色
    private val BUTTON_HOVER_OVERLAY_CENTER: Color
        get() = JBColor(Color(0, 140, 0, 20), Color(0, 180, 0, 25))

    // 按钮绿色半透明叠加层 - 悬浮边缘色
    private val BUTTON_HOVER_OVERLAY_EDGE: Color
        get() = JBColor(Color(0, 140, 0, 60), Color(0, 180, 0, 75))

    // 按钮边框颜色（绿色调半透明）
    private val BUTTON_BORDER_COLOR: Color
        get() = JBColor(Color(0, 140, 0, 60), Color(0, 180, 0, 70))

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

        // 计算所有选项标题的最大宽度
        val labelWidth = calculateMaxLabelWidth(buttonConfig)

        // 计算所有选项命令预览的最大宽度
        val previewWidth = calculateMaxPreviewWidth(buttonConfig)

        // 计算所有选项中按钮总宽度的最大值
        val maxButtonsWidth = calculateMaxButtonsWidth(buttonConfig)

        // 先创建 popup，后续在回调中使用
        lateinit var popup: JBPopup

        // 为每个 Option 创建一行
        for (option in buttonConfig.options) {
            if (option.isSeparator()) {
                // 分割线渲染
                mainPanel.add(createSeparatorRow(option))
            } else {
                // 普通选项渲染
                val optionRow = createOptionRow(project, option, labelWidth, previewWidth, maxButtonsWidth) { popup.closeOk(null) }
                mainPanel.add(optionRow)
            }
            // 添加行间距
            mainPanel.add(Box.createVerticalStrut(8))
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setMovable(false)
            .setResizable(false)
            .setShowBorder(true)
            .setTitle(buttonConfig.name)
            .createPopup()

        return popup
    }

    /**
     * 计算所有选项标题的最大宽度
     */
    private fun calculateMaxLabelWidth(buttonConfig: ButtonConfig): Int {
        val fontMetrics = JBLabel().getFontMetrics(JBLabel().font)
        var maxWidth = 80 // 最小宽度

        for (option in buttonConfig.options) {
            if (!option.isSeparator()) {
                val textWidth = fontMetrics.stringWidth(option.name)
                // 加上左右边距
                val totalWidth = textWidth + 24
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
        val textField = JTextField()
        val fontMetrics = textField.getFontMetrics(textField.font)
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
                // 加上左右边距
                val totalWidth = textWidth + 32
                if (totalWidth > maxWidth) {
                    maxWidth = totalWidth
                }
            }
        }

        // 限制在最大宽度范围内
        return maxWidth.coerceAtMost(MAX_PREVIEW_WIDTH)
    }

    /**
     * 创建一个用于测量宽度的临时按钮，样式与实际 SubButton 保持一致
     */
    private fun createMeasureButton(name: String): JButton {
        return JButton(name).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER_COLOR, 1),
                JBUI.Borders.empty(4, 12)
            )
        }
    }

    /**
     * 计算所有选项中按钮总宽度的最大值
     */
    private fun calculateMaxButtonsWidth(buttonConfig: ButtonConfig): Int {
        var maxWidth = 0

        for (option in buttonConfig.options) {
            if (!option.isSeparator()) {
                var rowWidth = 0
                for (subButton in option.subButtons) {
                    val btn = createMeasureButton(subButton.name)
                    rowWidth += btn.preferredSize.width
                }
                if (rowWidth > maxWidth) {
                    maxWidth = rowWidth
                }
            }
        }
        return maxWidth
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
            // 带标题的分割线：──── 标题 ────
            // 使用自定义绘制
            val innerPanel = object : JPanel() {
                override fun paintComponent(g: java.awt.Graphics) {
                    super.paintComponent(g)
                    val g2d = g as java.awt.Graphics2D
                    g2d.color = JBColor.GRAY
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
                foreground = JBColor.GRAY
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
     * 创建 Option 行（表单样式）
     * 三列布局：命令预览输入框 | 子按钮列表 | 选项名称
     */
    private fun createOptionRow(project: Project, option: OptionConfig, labelWidth: Int, basePreviewWidth: Int, maxButtonsWidth: Int, onClose: () -> Unit): JPanel {
        val rowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        val subButtons = option.subButtons

        // 计算当前行按钮总宽度（使用与实际按钮一致的样式测量）
        var currentButtonsWidth = 0
        for (subButton in subButtons) {
            val btn = createMeasureButton(subButton.name)
            currentButtonsWidth += btn.preferredSize.width
        }

        // 预览框 + 按钮区域的目标总宽度固定为 basePreviewWidth + maxButtonsWidth
        // 预览框宽度 = 目标总宽度 - 当前行按钮宽度，确保各行右边缘对齐
        val targetTotalWidth = basePreviewWidth + maxButtonsWidth
        val previewWidth = (targetTotalWidth - currentButtonsWidth)
            .coerceAtLeast(100)

        // 第一列：命令预览输入框
        val commandPreview = createCommandPreviewField(project, option, previewWidth, onClose)
        rowPanel.add(commandPreview)

        // 第二列：子按钮
        for (subButton in subButtons) {
            val btn = createSubButton(project, option, subButton, commandPreview, onClose)
            rowPanel.add(btn)
        }

        // 第三列：选项名称标签
        val optionLabel = createOptionLabel(project, option, labelWidth, onClose)
        rowPanel.add(optionLabel)

        return rowPanel
    }

    /**
     * 创建选项名称标签
     * @param labelWidth 标签宽度（根据最长标题动态计算）
     */
    private fun createOptionLabel(project: Project, option: OptionConfig, labelWidth: Int, onClose: () -> Unit): JBLabel {
        return JBLabel(option.name).apply {
            preferredSize = Dimension(labelWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = LABEL_FOREGROUND
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "点击执行: ${option.baseCommand}"
            border = JBUI.Borders.emptyLeft(12)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    onClose()
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
     * @param previewWidth 预览框宽度（根据按钮数量动态计算）
     */
    private fun createCommandPreviewField(project: Project, option: OptionConfig, previewWidth: Int, onClose: () -> Unit): JTextField {
        return JTextField(option.baseCommand).apply {
            isEditable = false
            preferredSize = Dimension(previewWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
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
                    onClose()
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
        commandPreview: JTextField,
        onClose: () -> Unit
    ): JButton {
        val fullCommand = buildFullCommand(option.baseCommand, subButton.params)

        var hovered = false

        return object : JButton(subButton.name) {
            override fun paintComponent(g: java.awt.Graphics) {
                val g2d = g as java.awt.Graphics2D
                g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                // 先绘制面板背景
                g2d.color = parent?.background ?: JBColor.PanelBackground
                g2d.fillRect(0, 0, width, height)
                // 叠加径向渐变半透明绿色：中心浅 → 边缘深
                val cx = width / 2f
                val cy = height / 2f
                val radius = Math.max(width, height) / 2f
                val centerColor = if (hovered) BUTTON_HOVER_OVERLAY_CENTER else BUTTON_OVERLAY_CENTER
                val edgeColor = if (hovered) BUTTON_HOVER_OVERLAY_EDGE else BUTTON_OVERLAY_EDGE
                g2d.paint = java.awt.RadialGradientPaint(
                    cx, cy, radius,
                    floatArrayOf(0f, 1f),
                    arrayOf(centerColor, edgeColor)
                )
                g2d.fillRect(0, 0, width, height)
                // 绘制文字
                super.paintComponent(g)
            }
        }.apply {
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "执行: $fullCommand"
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER_COLOR, 1),
                JBUI.Borders.empty(4, 12)
            )

            // 使用按钮的自然宽度，确保能完整显示文字
            val naturalWidth = preferredSize.width
            preferredSize = Dimension(naturalWidth, ROW_HEIGHT)

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    hovered = true
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JBColor.BLUE, 1),
                        JBUI.Borders.empty(4, 12)
                    )
                    repaint()
                    commandPreview.text = fullCommand
                }

                override fun mouseExited(e: MouseEvent?) {
                    hovered = false
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BUTTON_BORDER_COLOR, 1),
                        JBUI.Borders.empty(4, 12)
                    )
                    repaint()
                    commandPreview.text = option.baseCommand
                }
            })

            addActionListener {
                onClose()
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
