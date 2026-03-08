package site.laomst.ccbar.actions

import site.laomst.ccbar.icons.CCBarIcons
import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.Point
import java.awt.Component

/**
 * CCBar 工具栏 CommandBar Action
 * 每个实例对应一个 CommandBarConfig
 * 支持两种模式：
 * - 直接命令模式：command 不为空，点击后直接执行命令
 * - Command 列表模式：command 为空，点击后弹出Command 列表
 */
class CCBarCommandBarAction(
    private val commandBarConfig: CommandBarConfig
) : AnAction(commandBarConfig.name, null, CCBarIcons.loadIcon(commandBarConfig.icon)), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (commandBarConfig.isDirectCommandMode()) {
            // 直接命令模式：执行命令
            CCBarTerminalService.openTerminalForCommandBar(project, commandBarConfig)
        } else {
            // Command 列表模式：弹出菜单
            val component = e.inputEvent?.component ?: return
            val popup = CCBarPopupBuilder.buildPopup(project, commandBarConfig)
            // 弹框左对齐到CommandBar左边缘（向右展开）
            showPopupLeftAligned(popup, component)
        }
    }

    /**
     * 显示弹框，左对齐到组件的左边缘（向右展开）
     */
    private fun showPopupLeftAligned(popup: JBPopup, component: Component) {
        val componentBounds = component.bounds
        val locationOnScreen = component.locationOnScreen

        // 计算弹框位置：左对齐到组件左边缘
        val x = locationOnScreen.x
        val y = locationOnScreen.y + componentBounds.height

        popup.showInScreenCoordinates(component, Point(x, y))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = commandBarConfig.name
        e.presentation.icon = CCBarIcons.loadIcon(commandBarConfig.icon, e.project)
        // 启用条件：直接命令不为空 OR 有启用的普通 Command
        e.presentation.isEnabled = commandBarConfig.command.isNotBlank() || commandBarConfig.commands.any { it.enabled && it.isCommand() }
    }
}
