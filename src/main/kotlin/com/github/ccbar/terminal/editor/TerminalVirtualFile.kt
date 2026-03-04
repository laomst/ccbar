package com.github.ccbar.terminal.editor

import com.intellij.testFramework.LightVirtualFile

/**
 * 终端虚拟文件
 * 用于在编辑器区域承载终端 Tab
 */
class TerminalVirtualFile(
    val terminalName: String,
    val workingDirectory: String?,
    val command: String?,
    val iconPath: String? = null
) : LightVirtualFile("$terminalName.terminal", TerminalFileType.INSTANCE, "") {

    init {
        isWritable = false
    }

    override fun isValid(): Boolean = true

    override fun getPresentableName(): String = terminalName
}
