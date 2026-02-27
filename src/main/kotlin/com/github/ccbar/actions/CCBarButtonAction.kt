package com.github.ccbar.actions

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.terminal.CCBarTerminalService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

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
            popup.showUnderneathOf(component)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = buttonConfig.name
        e.presentation.icon = CCBarIcons.loadIcon(buttonConfig.icon, e.project)
        // 启用条件：直接命令不为空 OR 选项列表不为空
        e.presentation.isEnabled = buttonConfig.command.isNotBlank() || buttonConfig.options.isNotEmpty()
    }
}
