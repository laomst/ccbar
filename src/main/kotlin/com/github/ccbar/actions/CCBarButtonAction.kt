package com.github.ccbar.actions

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.ButtonConfig
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * CCBar 工具栏按钮 Action
 * 每个实例对应一个 ButtonConfig
 */
class CCBarButtonAction(
    private val buttonConfig: ButtonConfig
) : AnAction(buttonConfig.name, null, CCBarIcons.loadIcon(buttonConfig.icon)), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val component = e.inputEvent?.component ?: return

        // 构建并显示弹出菜单
        val popup = CCBarPopupBuilder.buildPopup(project, buttonConfig)
        popup.showUnderneathOf(component)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = buttonConfig.name
        e.presentation.icon = CCBarIcons.loadIcon(buttonConfig.icon, e.project)
        e.presentation.isEnabled = buttonConfig.options.isNotEmpty()
    }
}
