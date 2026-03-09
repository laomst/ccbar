package site.laomst.ccbar.actions

import site.laomst.ccbar.icons.CCBarIcons
import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.CommandConfig
import site.laomst.ccbar.settings.QuickParamConfig
import site.laomst.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.BasicStroke
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
 * 构建包含 Command 行和内联 QuickParam 的弹出面板
 * 两行布局：第一行 命令预览 | 名称，第二行 快捷参数列表（小号文字）
 */
object CCBarPopupBuilder {

    // 命令预览最大宽度
    private const val MAX_PREVIEW_WIDTH = 400

    // 标题最小宽度
    private const val MIN_LABEL_WIDTH = 200

    // 第一行高度
    private const val ROW_HEIGHT = 28

    // 第二行高度（快捷参数行，小号文字）
    private const val QUICK_PARAM_ROW_HEIGHT = 18

    // 悬浮高亮圆角半径
    private const val HOVER_ARC = 8

    // 命令预览文字颜色
    private val PREVIEW_FOREGROUND: Color
        get() = JBColor(Color(80, 80, 80), Color(180, 180, 180))

    // 快捷参数文字颜色（绿色调）
    private val QUICK_PARAM_FOREGROUND: Color
        get() = JBColor(Color(0, 120, 0), Color(0, 170, 0))

    // 快捷参数分隔符颜色
    private val QUICK_PARAM_SEPARATOR_COLOR: Color
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
    fun buildPopup(project: Project, commandBarConfig: CommandBarConfig): JBPopup {
        val mainPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            // 使用工具栏/头部区域的背景色
            background = UIManager.getColor("MainToolbar.background") ?: JBColor.PanelBackground
        }

        val simpleMode = commandBarConfig.simpleMode

        // 计算所有Command标题的最大宽度
        val labelWidth = calculateMaxLabelWidth(commandBarConfig)

        // 计算所有Command命令预览的最大宽度（简易模式下不需要）
        val previewWidth = if (simpleMode) 0 else calculateMaxPreviewWidth(commandBarConfig)

        // 先创建 popup，后续在回调中使用
        lateinit var popup: JBPopup

        val commonEnvVars = commandBarConfig.commonEnvVariables
        val commonQuickParams = commandBarConfig.commonQuickParams

        // 收集使用网络图标的 label，用于异步加载完成后更新
        val urlIconLabels = mutableListOf<Pair<JBLabel, String>>()

        // 为每个 Command 创建一行
        for (command in commandBarConfig.commands) {
            if (command.isSeparator()) {
                mainPanel.add(createSeparatorRow(command))
            } else if (!command.enabled) {
                continue
            } else if (simpleMode) {
                val row = createSimpleCommandRow(project, command, labelWidth, commonEnvVars, urlIconLabels) { popup.closeOk(null) }
                mainPanel.add(row)
            } else {
                val commandBlock = createCommandBlock(project, command, labelWidth, previewWidth, commonEnvVars, commonQuickParams, urlIconLabels) { popup.closeOk(null) }
                mainPanel.add(commandBlock)
            }
            // 添加行间距
            mainPanel.add(Box.createVerticalStrut(6))
        }

        // 注册网络图标加载完成监听器，图标下载完成后更新对应 label 的图标
        val removeListener = if (urlIconLabels.isNotEmpty()) {
            CCBarIcons.addIconLoadedListener { url ->
                urlIconLabels.filter { it.second == url }.forEach { (label, iconPath) ->
                    label.icon = CCBarIcons.loadIcon(iconPath)
                }
            }
        } else null

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setMovable(false)
            .setResizable(false)
            .setShowBorder(true)
            .addListener(object : com.intellij.openapi.ui.popup.JBPopupListener {
                override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                    removeListener?.invoke()
                }
            })
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
     * 计算所有Command标题的最大宽度
     */
    private fun calculateMaxLabelWidth(commandBarConfig: CommandBarConfig): Int {
        val fontMetrics = JBLabel().getFontMetrics(JBLabel().font)
        var maxWidth = MIN_LABEL_WIDTH // 最小宽度

        for (command in commandBarConfig.commands) {
            if (!command.isSeparator() && command.enabled) {
                val textWidth = fontMetrics.stringWidth(command.name)
                val iconWidth = if (command.icon.isNotBlank()) 16 + 4 else 0  // 图标宽度 + 间距
                val totalWidth = textWidth + iconWidth + 16
                if (totalWidth > maxWidth) {
                    maxWidth = totalWidth
                }
            }
        }

        return maxWidth
    }

    /**
     * 计算所有Command命令预览的最大宽度
     */
    private fun calculateMaxPreviewWidth(commandBarConfig: CommandBarConfig): Int {
        val fontMetrics = JBLabel().getFontMetrics(JBLabel().font)
        var maxWidth = 0
        val commonQuickParams = commandBarConfig.commonQuickParams

        for (command in commandBarConfig.commands) {
            if (!command.isSeparator() && command.enabled) {
                // 计算基础命令宽度
                var textWidth = fontMetrics.stringWidth(command.baseCommand)
                // 同时考虑带参数的完整命令宽度（使用合并后的快捷参数）
                val mergedQuickParams = mergeQuickParams(commonQuickParams, command.quickParams)
                for (quickParam in mergedQuickParams) {
                    val fullCommand = buildFullCommand(command.baseCommand, quickParam.params)
                    val fullWidth = fontMetrics.stringWidth(fullCommand)
                    if (fullWidth > textWidth) {
                        textWidth = fullWidth
                    }
                }
                val totalWidth = textWidth + 16 + 16 + 4  // 16+4 为终端图标宽度+间距
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
    private fun createSeparatorRow(command: CommandConfig): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
        }

        if (command.name.isNotBlank()) {
            val innerPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.color = JBColor.GRAY
                    g2d.stroke = BasicStroke(1f)

                    val labelWidth = graphics.getFontMetrics(font).stringWidth(command.name) + 16
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

            val label = JLabel(command.name).apply {
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
                    g2d.stroke = BasicStroke(1f)
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
     * 创建简易模式的Command行（仅显示名称，整行悬浮高亮）
     */
    private fun createSimpleCommandRow(project: Project, command: CommandConfig, labelWidth: Int, commonEnvVars: String, urlIconLabels: MutableList<Pair<JBLabel, String>>, onClose: () -> Unit): JPanel {
        val hoverPanel = createHoverPanel {
            onClose()
            CCBarTerminalService.openTerminal(project, command, null, commonEnvVars)
        }
        hoverPanel.toolTipText = command.baseCommand

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }

        val label = JBLabel(command.name).apply {
            preferredSize = Dimension(labelWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = JBColor.foreground()
            if (command.icon.isNotBlank()) {
                icon = CCBarIcons.loadIcon(command.icon)
                if (command.icon.startsWith("http://") || command.icon.startsWith("https://")) {
                    urlIconLabels.add(this to command.icon)
                }
            }
        }

        row.add(label)
        hoverPanel.add(row)
        return hoverPanel
    }

    /**
     * 创建 Command 块（两行布局，整块悬浮高亮）
     * 第一行：命令预览 | 名称
     * 第二行：快捷参数列表（小号文字）
     */
    private fun createCommandBlock(project: Project, command: CommandConfig, labelWidth: Int, previewWidth: Int, commonEnvVars: String, commonQuickParams: List<QuickParamConfig>, urlIconLabels: MutableList<Pair<JBLabel, String>>, onClose: () -> Unit): JPanel {
        val hoverPanel = createHoverPanel {
            onClose()
            CCBarTerminalService.openTerminal(project, command, null, commonEnvVars)
        }

        // 命令预览标签（需要被快捷参数 hover 更新）
        val commandPreview = JBLabel(command.baseCommand).apply {
            icon = CCBarIcons.loadIcon("builtin:AllIcons.Debugger.ExecuteCurrentStatement")
            preferredSize = Dimension(previewWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = PREVIEW_FOREGROUND
        }

        // 第一行：命令预览 | 名称
        val firstRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
        }
        val commandLabel = JBLabel(command.name).apply {
            preferredSize = Dimension(labelWidth, ROW_HEIGHT)
            horizontalAlignment = SwingConstants.LEFT
            foreground = LABEL_FOREGROUND
            border = JBUI.Borders.emptyLeft(8)
            if (command.icon.isNotBlank()) {
                icon = CCBarIcons.loadIcon(command.icon)
                if (command.icon.startsWith("http://") || command.icon.startsWith("https://")) {
                    urlIconLabels.add(this to command.icon)
                }
            }
        }
        firstRow.add(commandPreview)
        firstRow.add(commandLabel)
        hoverPanel.add(firstRow)

        // 第二行：快捷参数列表（仅在有启用的快捷参数时显示）
        // 使用合并后的快捷参数
        val mergedQuickParams = mergeQuickParams(commonQuickParams, command.quickParams)
        if (mergedQuickParams.isNotEmpty()) {
            val secondRow = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(-4)
            }

            for ((index, quickParam) in mergedQuickParams.withIndex()) {
                val btn = createQuickParamLabel(project, command, quickParam, commandPreview, commonEnvVars, onClose)
                secondRow.add(btn)
                if (index < mergedQuickParams.size - 1) {
                    val separator = JBLabel("|").apply {
                        foreground = QUICK_PARAM_SEPARATOR_COLOR
                        font = font.deriveFont(font.size2D - 1f)
                        preferredSize = Dimension(preferredSize.width, QUICK_PARAM_ROW_HEIGHT)
                    }
                    secondRow.add(separator)
                }
            }

            hoverPanel.add(secondRow)
        }

        return hoverPanel
    }

    /**
     * 创建 快捷参数标签（小号纯文本）
     * 点击快捷参数会消费事件，不触发 hoverPanel 的点击
     */
    private fun createQuickParamLabel(
        project: Project,
        command: CommandConfig,
        quickParam: QuickParamConfig,
        commandPreview: JBLabel,
        commonEnvVars: String,
        onClose: () -> Unit
    ): JBLabel {
        val fullCommand = buildFullCommand(command.baseCommand, quickParam.params)

        return JBLabel(quickParam.name).apply {
            font = font.deriveFont(font.size2D - 1f)
            foreground = QUICK_PARAM_FOREGROUND
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "执行: $fullCommand"
            border = JBUI.Borders.empty(0, 4)
            preferredSize = Dimension(preferredSize.width, QUICK_PARAM_ROW_HEIGHT)

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    e?.consume()
                    onClose()
                    CCBarTerminalService.openTerminal(project, command, quickParam, commonEnvVars)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor.BLUE
                    commandPreview.text = fullCommand
                }

                override fun mouseExited(e: MouseEvent?) {
                    foreground = QUICK_PARAM_FOREGROUND
                    commandPreview.text = command.baseCommand
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

    /**
     * 合并公共快捷参数和 Command 自身的快捷参数
     * 规则：公共快捷参数（未被覆盖的）在前，Command 自身的快捷参数在后
     * 按 name 判断同名，Command 同名参数覆盖公共参数
     */
    private fun mergeQuickParams(
        commonParams: List<QuickParamConfig>,
        commandParams: List<QuickParamConfig>
    ): List<QuickParamConfig> {
        val commandNames = commandParams.filter { it.enabled }.map { it.name }.toSet()
        val filteredCommon = commonParams.filter { it.enabled && it.name !in commandNames }
        return filteredCommon + commandParams.filter { it.enabled }
    }
}
