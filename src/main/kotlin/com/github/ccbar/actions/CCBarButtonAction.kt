package com.github.ccbar.actions

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.popup.PopupFactoryImpl
import java.awt.Point
import java.awt.Component

/**
 * CCBar 工具栏按钮 Action
 * 每个实例对应一个 ButtonConfig
 * 支持两种模式：
 * - 直接命令模式：command 不为空，点击后直接执行命令
 * - 选项列表模式：command 为空，点击后弹出选项列表
 */
class CCBarButtonAction(
    private val buttonConfig: ButtonConfig
) : AnAction(buttonConfig.name, null, CCBarIcons.loadIcon(buttonConfig.icon)), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (buttonConfig.isDirectCommandMode()) {
            // 直接命令模式：执行命令
            CCBarTerminalService.openTerminalForButton(project, buttonConfig)
        } else {
            // 选项列表模式：弹出菜单
            val component = e.inputEvent?.component ?: return
            val popup = CCBarPopupBuilder.buildPopup(project, buttonConfig)
            // 弹框左对齐到按钮左边缘（向右展开）
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
        e.presentation.text = buttonConfig.name
        e.presentation.icon = CCBarIcons.loadIcon(buttonConfig.icon, e.project)
        // 启用条件：直接命令不为空 OR 选项列表不为空
        e.presentation.isEnabled = buttonConfig.command.isNotBlank() || buttonConfig.options.isNotEmpty()
    }
}
