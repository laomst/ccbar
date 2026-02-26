package com.github.ccbar.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * CCBar 插件配置管理类
 * 使用 PersistentStateComponent 实现应用级配置持久化
 */
@State(
    name = "com.github.ccbar.settings.CCBarSettings",
    storages = [Storage("ccbar.xml")]
)
class CCBarSettings : PersistentStateComponent<CCBarSettings.State> {

    /**
     * 插件配置状态
     */
    data class State(
        var buttons: MutableList<ButtonConfig> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // 如果配置为空，初始化默认配置
        if (myState.buttons.isEmpty()) {
            myState.buttons = createDefaultButtons()
        }
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        myState.buttons = createDefaultButtons()
    }

    companion object {
        /**
         * 获取 CCBarSettings 实例
         */
        fun getInstance(): CCBarSettings =
            ApplicationManager.getApplication().getService(CCBarSettings::class.java)

        /**
         * 创建默认按钮配置
         */
        fun createDefaultButtons(): MutableList<ButtonConfig> = mutableListOf(
            ButtonConfig(
                id = "claude-code-default",
                name = "Claude Code",
                icon = "builtin:AllIcons.Actions.Execute",
                options = mutableListOf(
                    OptionConfig(
                        id = "model",
                        name = "Model",
                        baseCommand = "claude",
                        workingDirectory = "",
                        defaultTerminalName = "Claude - Model",
                        subButtons = mutableListOf(
                            SubButtonConfig(
                                id = "sonnet",
                                name = "Sonnet",
                                params = "--model sonnet"
                            ),
                            SubButtonConfig(
                                id = "opus",
                                name = "Opus",
                                params = "--model opus"
                            )
                        )
                    ),
                    OptionConfig(
                        id = "workspace",
                        name = "Workspace",
                        baseCommand = "claude",
                        workingDirectory = "",
                        defaultTerminalName = "Claude - Workspace",
                        subButtons = mutableListOf(
                            SubButtonConfig(
                                id = "project-a",
                                name = "Project A",
                                params = "--workspace ~/workspace/project-a"
                            ),
                            SubButtonConfig(
                                id = "project-b",
                                name = "Project B",
                                params = "--workspace ~/workspace/project-b"
                            )
                        )
                    )
                )
            )
        )
    }
}

/**
 * 子按钮配置
 * 绑定参数文本，点击后执行 Option.baseCommand + params
 */
data class SubButtonConfig(
    var id: String = "",
    var name: String = "",
    var params: String = "",
    var icon: String = ""
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): SubButtonConfig = SubButtonConfig(
        id = id,
        name = name,
        params = params,
        icon = icon
    )
}

/**
 * 选项配置
 * 绑定基础命令（baseCommand）和可选的工作目录
 */
data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var subButtons: MutableList<SubButtonConfig> = mutableListOf()
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): OptionConfig = OptionConfig(
        id = id,
        name = name,
        baseCommand = baseCommand,
        workingDirectory = workingDirectory,
        defaultTerminalName = defaultTerminalName,
        subButtons = subButtons.map { it.deepCopy() }.toMutableList()
    )
}

/**
 * 工具栏按钮配置
 * 纯分类容器，不绑定命令
 */
data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    var options: MutableList<OptionConfig> = mutableListOf()
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): ButtonConfig = ButtonConfig(
        id = id,
        name = name,
        icon = icon,
        options = options.map { it.deepCopy() }.toMutableList()
    )
}

/**
 * State 的深拷贝扩展方法
 */
fun CCBarSettings.State.deepCopy(): CCBarSettings.State = CCBarSettings.State(
    buttons = buttons.map { it.deepCopy() }.toMutableList()
)
