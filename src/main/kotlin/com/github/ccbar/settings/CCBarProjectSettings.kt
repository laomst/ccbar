package com.github.ccbar.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * CCBar 项目级配置管理类
 * 存储在 .idea/ccbar.xml
 * 用于为每个项目设置独立的按钮配置
 */
@State(
    name = "com.github.ccbar.settings.CCBarProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class CCBarProjectSettings : PersistentStateComponent<CCBarProjectSettings.ProjectState> {

    /**
     * 项目级配置状态
     */
    data class ProjectState(
        var enabled: Boolean = false,  // 是否启用项目配置
        var buttons: MutableList<ButtonConfig> = mutableListOf()  // 按钮配置（复用现有数据结构）
    ) {
        /**
         * 深拷贝
         */
        fun deepCopy(): ProjectState = ProjectState(
            enabled = enabled,
            buttons = buttons.map { it.deepCopy() }.toMutableList()
        )
    }

    private var myState = ProjectState()

    override fun getState(): ProjectState = myState

    override fun loadState(state: ProjectState) {
        myState = state
    }

    /**
     * 启用项目配置
     * 复制系统配置到项目配置
     */
    fun enable() {
        val systemSettings = CCBarSettings.getInstance()
        myState.buttons = systemSettings.state.deepCopy().buttons
        myState.enabled = true
    }

    /**
     * 禁用项目配置
     * 不删除数据，仅标记为禁用
     */
    fun disable() {
        myState.enabled = false
    }

    /**
     * 重置项目配置
     * 重新复制系统配置覆盖当前项目配置
     */
    fun resetToSystem() {
        val systemSettings = CCBarSettings.getInstance()
        myState.buttons = systemSettings.state.deepCopy().buttons
    }

    /**
     * 判断是否启用了项目配置
     */
    fun isEnabled(): Boolean = myState.enabled

    /**
     * 获取当前有效的按钮配置
     * 如果启用了项目配置，返回项目配置；否则返回 null（表示应使用系统配置）
     */
    fun getEffectiveButtons(): MutableList<ButtonConfig>? {
        return if (myState.enabled) myState.buttons else null
    }

    companion object {
        /**
         * 获取 CCBarProjectSettings 实例
         */
        fun getInstance(project: Project): CCBarProjectSettings =
            project.getService(CCBarProjectSettings::class.java)
    }
}
