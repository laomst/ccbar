package com.github.ccbar.settings.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.JScrollPane

/**
 * IDEA 内置图标选择器
 * 通过反射动态获取所有 AllIcons 图标，供用户选择
 */
object BuiltinIconSelector {

    private val LOG = Logger.getInstance(BuiltinIconSelector::class.java)

    // 缓存所有图标列表
    private var allIconsCache: List<Triple<String, String, Icon>>? = null

    // 图标悬浮背景色
    private val HOVER_BACKGROUND: Color
        get() = JBColor(Color(220, 220, 220), Color(70, 70, 70))

    // 图标选中背景色
    private val SELECTED_BACKGROUND: Color
        get() = JBColor(Color(180, 210, 255), Color(60, 90, 140))

    // 图标选中边框色
    private val SELECTED_BORDER: Color
        get() = JBColor(Color(30, 120, 255), Color(80, 140, 255))

    /**
     * 通过反射获取所有 AllIcons 图标
     */
    private fun loadAllIcons(): List<Triple<String, String, Icon>> {
        val icons = mutableListOf<Triple<String, String, Icon>>()

        fun collectIcons(clazz: Class<*>, prefix: String) {
            try {
                // 获取当前类的所有字段（图标）
                for (field in clazz.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) && Icon::class.java.isAssignableFrom(field.type)) {
                        try {
                            field.isAccessible = true
                            val icon = field.get(null) as? Icon ?: continue
                            val path = "builtin:$prefix${field.name}"
                            icons.add(Triple(field.name, path, icon))
                        } catch (e: Exception) {
                            // 忽略无法访问的字段
                        }
                    }
                }

                // 递归获取嵌套类
                for (nestedClass in clazz.declaredClasses) {
                    collectIcons(nestedClass, "$prefix${nestedClass.simpleName}.")
                }
            } catch (e: Exception) {
                LOG.warn("CCBar: 加载图标类失败: ${clazz.name}", e)
            }
        }

        collectIcons(AllIcons::class.java, "AllIcons.")

        // 按路径排序
        icons.sortBy { it.second }

        return icons
    }

    /**
     * 获取所有图标列表（带缓存）
     */
    private fun getAllIcons(): List<Triple<String, String, Icon>> {
        return allIconsCache ?: loadAllIcons().also { allIconsCache = it }
    }

    /**
     * 清除图标缓存
     */
    fun clearCache() {
        allIconsCache = null
    }

    /**
     * 创建图标选择弹出面板
     * @param onIconSelected 图标选中回调，参数为图标路径
     * @param currentIconPath 当前选中的图标路径（用于高亮显示）
     */
    fun createPopup(onIconSelected: (String) -> Unit, currentIconPath: String? = null): JBPopup {
        val icons = getAllIcons()

        // 计算网格布局：每行 10 个图标
        val columns = 10

        // 创建图标网格面板
        val gridPanel = JPanel(GridLayout(0, columns, 2, 2)).apply {
            border = JBUI.Borders.empty(4)
            background = JBColor.PanelBackground
        }

        // 存储所有图标标签及其路径，用于动态更新选中状态
        val iconLabels = mutableListOf<Pair<String, JLabel>>()

        // 当前选中路径（可变）
        var selectedPath = currentIconPath

        // 更新所有标签的选中状态
        fun updateSelectionState(newSelectedPath: String) {
            selectedPath = newSelectedPath
            for ((path, label) in iconLabels) {
                val isSelected = path == newSelectedPath
                label.border = if (isSelected) {
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(SELECTED_BORDER, 2),
                        JBUI.Borders.empty(3)
                    )
                } else {
                    JBUI.Borders.empty(5)
                }
                label.background = if (isSelected) SELECTED_BACKGROUND else JBColor.PanelBackground
            }
        }

        for ((name, path, icon) in icons) {
            val iconLabel = createIconLabel(name, path, icon, currentIconPath) { selectedPath ->
                onIconSelected(selectedPath)
                updateSelectionState(selectedPath)  // 动态更新选中状态
            }
            iconLabels.add(path to iconLabel)
            gridPanel.add(iconLabel)
        }

        // 包装在滚动面板中
        val scrollPane = JScrollPane(gridPanel).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            preferredSize = Dimension(520, 400)
            border = null
            background = JBColor.PanelBackground
        }

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scrollPane, null)
            .setTitle("选择内置图标 (${icons.size} 个)")
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnWindowDeactivation(true)
            .setResizable(true)
            .setMovable(false)
            .setMinSize(Dimension(400, 300))
            .createPopup()
    }

    /**
     * 创建单个图标标签
     */
    private fun createIconLabel(
        name: String,
        path: String,
        icon: Icon,
        currentIconPath: String?,
        onIconSelected: (String) -> Unit
    ): JLabel {
        val isSelected = path == currentIconPath

        return JLabel(icon, JLabel.CENTER).apply {
            toolTipText = "<html><b>$name</b><br><small>$path</small></html>"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            horizontalAlignment = JLabel.CENTER
            verticalAlignment = JLabel.CENTER
            preferredSize = Dimension(32, 32)

            border = if (isSelected) {
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(SELECTED_BORDER, 2),
                    JBUI.Borders.empty(3)
                )
            } else {
                JBUI.Borders.empty(5)
            }

            background = if (isSelected) SELECTED_BACKGROUND else JBColor.PanelBackground
            isOpaque = true

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    if (!isSelected) {
                        background = HOVER_BACKGROUND
                    }
                }

                override fun mouseExited(e: MouseEvent?) {
                    background = if (isSelected) SELECTED_BACKGROUND else JBColor.PanelBackground
                }

                override fun mouseClicked(e: MouseEvent?) {
                    onIconSelected(path)
                }
            })
        }
    }

    /**
     * 获取所有图标列表
     */
    fun getIcons(): List<Triple<String, String, Icon>> = getAllIcons()
}
