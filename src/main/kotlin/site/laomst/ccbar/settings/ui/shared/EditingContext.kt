package site.laomst.ccbar.settings.ui.shared

import site.laomst.ccbar.settings.CCBarProjectSettings
import site.laomst.ccbar.settings.CCBarSettings
import site.laomst.ccbar.settings.CommandBarConfig
import site.laomst.ccbar.settings.CommandConfig

/**
 * 编辑上下文接口
 * 管理编辑状态，在主面板和子面板之间共享数据
 */
interface EditingContext {
    /**
     * 当前配置模式
     */
    val configMode: ConfigMode

    /**
     * 当前编辑状态
     */
    val currentState: CCBarSettings.State

    /**
     * 当前选中的 CommandBar
     */
    var selectedCommandBar: CommandBarConfig?

    /**
     * 当前选中的 Command
     */
    var selectedCommand: CommandConfig?

    /**
     * 忽略更新标志（用于批量更新时避免循环）
     */
    var ignoreUpdate: Boolean

    /**
     * 项目路径
     */
    val projectPath: String?

    /**
     * 通知 CommandBar 列表已变更
     */
    fun notifyCommandBarListChanged()

    /**
     * 通知 Command 列表已变更
     */
    fun notifyCommandListChanged()

    /**
     * 通知 CommandBar 详情已变更
     */
    fun notifyCommandBarDetailChanged()

    /**
     * 通知 Command 详情已变更
     */
    fun notifyCommandDetailChanged()
}

/**
 * 默认编辑上下文实现
 */
class DefaultEditingContext(
    override val configMode: ConfigMode,
    private val systemState: CCBarSettings.State,
    private val projectState: CCBarProjectSettings.ProjectState?,
    override val projectPath: String?,
    private val onCommandBarListChanged: () -> Unit = {},
    private val onCommandListChanged: () -> Unit = {},
    private val onCommandBarDetailChanged: () -> Unit = {},
    private val onCommandDetailChanged: () -> Unit = {}
) : EditingContext {

    override var selectedCommandBar: CommandBarConfig? = null
    override var selectedCommand: CommandConfig? = null
    override var ignoreUpdate: Boolean = false

    override val currentState: CCBarSettings.State
        get() = if (configMode == ConfigMode.PROJECT && projectState?.enabled == true) {
            CCBarSettings.State(projectState.commandBars)
        } else {
            systemState
        }

    override fun notifyCommandBarListChanged() {
        onCommandBarListChanged()
    }

    override fun notifyCommandListChanged() {
        onCommandListChanged()
    }

    override fun notifyCommandBarDetailChanged() {
        onCommandBarDetailChanged()
    }

    override fun notifyCommandDetailChanged() {
        onCommandDetailChanged()
    }
}
