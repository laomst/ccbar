package site.laomst.ccbar.actions

import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.CCBarProjectSettings
import site.laomst.ccbar.settings.CCBarSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * CCBar 工具栏动态 ActionGroup
 * 根据配置动态生成工具栏 CommandBar
 * 优先使用项目配置（如果启用），否则使用系统配置
 */
class CCBarToolbarActionGroup : ActionGroup(), DumbAware {

    // 缓存子 Action，避免每次 getChildren 都重建实例
    private var cachedChildren: Array<AnAction> = emptyArray()
    private var lastConfigVersion = -1

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project
        val commandBars = getEffectiveCommandBars(project)
        val currentVersion = commandBars.hashCode()

        if (currentVersion != lastConfigVersion) {
            cachedChildren = if (commandBars.isEmpty()) {
                emptyArray()
            } else {
                commandBars.map { commandBarConfig ->
                    CCBarCommandBarAction(commandBarConfig)
                }.toTypedArray()
            }
            lastConfigVersion = currentVersion
        }

        return cachedChildren
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val commandBars = getEffectiveCommandBars(project)
        e.presentation.isVisible = commandBars.isNotEmpty()
        e.presentation.isEnabled = true
    }

    /**
     * 获取当前有效的CommandBar 配置
     * 优先级：项目配置（启用时） > 系统配置
     */
    private fun getEffectiveCommandBars(project: Project?): List<CommandBarConfig> {
        // 如果有项目，检查项目配置
        if (project != null) {
            val projectSettings = CCBarProjectSettings.getInstance(project)
            val projectButtons = projectSettings.getEffectiveCommandBars()
            if (projectButtons != null) {
                return projectButtons.filter { it.enabled }
            }
        }

        // 使用系统配置
        val systemSettings = CCBarSettings.getInstance()
        return systemSettings.state.commandBars.filter { it.enabled }
    }
}
