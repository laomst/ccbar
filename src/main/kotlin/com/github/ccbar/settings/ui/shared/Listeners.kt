package com.github.ccbar.settings.ui.shared

import com.github.ccbar.settings.CommandBarConfig
import com.github.ccbar.settings.CommandConfig

/**
 * CommandBar 列表选择监听器
 */
interface CommandBarListListener {
    /**
     * 当选中 CommandBar 时触发
     * @param commandBar 选中的 CommandBar，null 表示未选中
     */
    fun onCommandBarSelected(commandBar: CommandBarConfig?)
}

/**
 * CommandBar 详情变更监听器
 */
interface CommandBarDetailListener {
    /**
     * 当直接命令模式切换时触发
     * @param isDirectMode 是否为直接命令模式
     */
    fun onDirectCommandModeChanged(isDirectMode: Boolean)

    /**
     * 当简易模式切换时触发
     * @param isSimpleMode 是否为简易模式
     */
    fun onSimpleModeChanged(isSimpleMode: Boolean)
}

/**
 * Command 列表操作监听器
 */
interface CommandListListener {
    /**
     * 当选中 Command 时触发
     * @param command 选中的 Command，null 表示未选中
     */
    fun onCommandSelected(command: CommandConfig?)

    /**
     * 当添加 Command 时触发
     */
    fun onAddCommand()

    /**
     * 当添加分割线时触发
     */
    fun onAddSeparator()
}

/**
 * Command 详情变更监听器
 */
interface CommandDetailListener {
    /**
     * 当快捷参数更新时触发
     */
    fun onQuickParamsUpdated()
}
