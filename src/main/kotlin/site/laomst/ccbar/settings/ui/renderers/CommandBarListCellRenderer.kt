package site.laomst.ccbar.settings.ui.renderers

import site.laomst.ccbar.icons.CCBarIcons
import site.laomst.ccbar.settings.CommandBarConfig
import com.intellij.ui.JBColor
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

/**
 * CommandBar 列表渲染器
 */
class CommandBarListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CommandBarConfig) {
            text = value.name
            icon = CCBarIcons.loadIcon(value.icon)
            if (!value.enabled && !isSelected) {
                foreground = JBColor.GRAY
            }
        }
        return component
    }
}
