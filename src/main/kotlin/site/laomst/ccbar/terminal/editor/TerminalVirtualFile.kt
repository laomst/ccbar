package site.laomst.ccbar.terminal.editor

import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

/**
 * 终端虚拟文件
 * 用于在编辑器区域承载终端 Tab
 */
class TerminalVirtualFile(
    val terminalName: String,
    val workingDirectory: String?,
    val command: String?,
    val icon: Icon? = null
) : LightVirtualFile("$terminalName.terminal", TerminalFileType.INSTANCE, "") {

    init {
        isWritable = false
    }

    override fun isValid(): Boolean = true

    override fun getPresentableName(): String = terminalName
}
