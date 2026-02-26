package com.github.ccbar.actions

import com.github.ccbar.settings.CCBarSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * CCBar 工具栏动态 ActionGroup
 * 根据配置动态生成工具栏按钮
 */
class CCBarToolbarActionGroup : ActionGroup(), DumbAware {

    // 缓存子 Action，避免每次 getChildren 都重建实例
    private var cachedChildren: Array<AnAction> = emptyArray()
    private var lastConfigVersion = -1

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val settings = CCBarSettings.getInstance()
        val buttons = settings.state.buttons
        val currentVersion = buttons.hashCode()

        if (currentVersion != lastConfigVersion) {
            cachedChildren = if (buttons.isEmpty()) {
                emptyArray()
            } else {
                buttons.map { buttonConfig ->
                    CCBarButtonAction(buttonConfig)
                }.toTypedArray()
            }
            lastConfigVersion = currentVersion
        }

        return cachedChildren
    }

    override fun update(e: AnActionEvent) {
        val settings = CCBarSettings.getInstance()
        val buttons = settings.state.buttons
        e.presentation.isVisible = buttons.isNotEmpty()
        e.presentation.isEnabled = true
    }
}