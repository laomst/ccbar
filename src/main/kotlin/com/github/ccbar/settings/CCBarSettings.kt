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
        var commandBars: MutableList<CommandBarConfig> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // 如果配置为空，初始化默认配置
        if (myState.commandBars.isEmpty()) {
            myState.commandBars = createDefaultCommandBars()
        }
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefault() {
        myState.commandBars = createDefaultCommandBars()
    }

    companion object {
        /**
         * 获取 CCBarSettings 实例
         */
        fun getInstance(): CCBarSettings =
            ApplicationManager.getApplication().getService(CCBarSettings::class.java)

        /**
         * 创建默认CommandBar 配置
         */
        fun createDefaultCommandBars(): MutableList<CommandBarConfig> = mutableListOf(
            CommandBarConfig(
                id = "claude-code-default",
                name = "Claude Code",
                icon = "builtin:AllIcons.Actions.Execute",
                commands = mutableListOf(
                    CommandConfig(
                        id = "model",
                        name = "Model",
                        baseCommand = "claude",
                        workingDirectory = "",
                        defaultTerminalName = "Claude - Model",
                        quickParams = mutableListOf(
                            QuickParamConfig(
                                id = "sonnet",
                                name = "Sonnet",
                                params = "--model sonnet"
                            ),
                            QuickParamConfig(
                                id = "opus",
                                name = "Opus",
                                params = "--model opus"
                            )
                        )
                    ),
                    CommandConfig(
                        id = "workspace",
                        name = "Workspace",
                        baseCommand = "claude",
                        workingDirectory = "",
                        defaultTerminalName = "Claude - Workspace",
                        quickParams = mutableListOf(
                            QuickParamConfig(
                                id = "project-a",
                                name = "Project A",
                                params = "--workspace ~/workspace/project-a"
                            ),
                            QuickParamConfig(
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
 * 快捷参数配置
 * 绑定参数文本，点击后执行 Command.baseCommand + params
 */
data class QuickParamConfig(
    var id: String = "",
    var name: String = "",
    var params: String = "",
    var icon: String = "",
    var enabled: Boolean = true
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): QuickParamConfig = QuickParamConfig(
        id = id,
        name = name,
        params = params,
        icon = icon,
        enabled = enabled
    )
}

/**
 * 终端打开模式常量
 */
object TerminalMode {
    const val TOOL_WINDOW = ""        // 在终端工具窗口中打开（默认）
    const val EDITOR = "editor"       // 在编辑器区域中打开
}

/**
 * Command 类型常量
 */
object CommandType {
    const val COMMAND = "command"
    const val SEPARATOR = "separator"
}

/**
 * Command 配置
 * 绑定基础命令（baseCommand）和可选的工作目录
 * 支持两种类型：普通 Command（type 为空或 "command"）和分割线（type = "separator"）
 */
data class CommandConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "builtin:AllIcons.Actions.Execute",
    var baseCommand: String = "",
    var envVariables: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var quickParams: MutableList<QuickParamConfig> = mutableListOf(),
    var type: String = "",  // 空值或"command"=普通 Command, "separator"=分割线
    var terminalMode: String = "",  // 终端打开模式：""=工具窗口, "editor"=编辑器
    var enabled: Boolean = true
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): CommandConfig = CommandConfig(
        id = id,
        name = name,
        icon = icon,
        baseCommand = baseCommand,
        envVariables = envVariables,
        workingDirectory = workingDirectory,
        defaultTerminalName = defaultTerminalName,
        quickParams = quickParams.map { it.deepCopy() }.toMutableList(),
        type = type,
        terminalMode = terminalMode,
        enabled = enabled
    )

    /**
     * 判断是否为分割线类型
     */
    fun isSeparator(): Boolean = type == CommandType.SEPARATOR

    /**
     * 判断是否为普通Command 类型（默认）
     */
    fun isCommand(): Boolean = type != CommandType.SEPARATOR
}

/**
 * 工具栏 CommandBar配置
 * 支持两种模式：
 * - 直接命令模式：command 不为空，点击后直接执行命令
 * - Command 列表模式：command 为空，点击后弹出Command 列表
 */
data class CommandBarConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    // 直接命令模式字段
    var command: String = "",  // 直接命令，为空则使用Command 列表模式
    var envVariables: String = "",  // 环境变量，格式：KEY1=val1;KEY2=val2
    var workingDirectory: String = "",  // 工作目录，留空使用项目根目录
    var defaultTerminalName: String = "",  // 直接命令模式的默认终端名称
    var terminalMode: String = "",  // 终端打开模式：""=工具窗口, "editor"=编辑器
    var simpleMode: Boolean = false,  // 简易模式：仅显示Command名称，隐藏命令预览和快捷参数
    var commands: MutableList<CommandConfig> = mutableListOf(),
    var enabled: Boolean = true
) {
    /**
     * 深拷贝
     */
    fun deepCopy(): CommandBarConfig = CommandBarConfig(
        id = id,
        name = name,
        icon = icon,
        command = command,
        envVariables = envVariables,
        workingDirectory = workingDirectory,
        defaultTerminalName = defaultTerminalName,
        terminalMode = terminalMode,
        simpleMode = simpleMode,
        commands = commands.map { it.deepCopy() }.toMutableList(),
        enabled = enabled
    )

    /**
     * 判断是否为直接命令模式
     */
    fun isDirectCommandMode(): Boolean = command.isNotBlank()
}

/**
 * State 的深拷贝扩展方法
 */
fun CCBarSettings.State.deepCopy(): CCBarSettings.State = CCBarSettings.State(
    commandBars = commandBars.map { it.deepCopy() }.toMutableList()
)
