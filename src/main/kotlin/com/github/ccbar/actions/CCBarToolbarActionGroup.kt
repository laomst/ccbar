package com.github.ccbar.actions

import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.settings.CCBarProjectSettings
import com.github.ccbar.settings.CCBarSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * CCBar 工具栏动态 ActionGroup
 * 根据配置动态生成工具栏按钮
 * 优先使用项目配置（如果启用），否则使用系统配置
 */
class CCBarToolbarActionGroup : ActionGroup(), DumbAware {

    // 缓存子 Action，避免每次 getChildren 都重建实例
    private var cachedChildren: Array<AnAction> = emptyArray()
    private var lastConfigVersion = -1

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project
        val buttons = getEffectiveButtons(project)
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
        val project = e.project
        val buttons = getEffectiveButtons(project)
        e.presentation.isVisible = buttons.isNotEmpty()
        e.presentation.isEnabled = true
    }

    /**
     * 获取当前有效的按钮配置
     * 优先级：项目配置（启用时） > 系统配置
     */
    private fun getEffectiveButtons(project: Project?): List<ButtonConfig> {
        // 如果有项目，检查项目配置
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            val projectButtons = projectSettings.getEffectiveButtons()
            if (projectButtons != null) {
                return projectButtons
            }
        }

        // 使用系统配置
        val systemSettings = CCBarSettings.getInstance()
        return systemSettings.state.buttons
    }
}
