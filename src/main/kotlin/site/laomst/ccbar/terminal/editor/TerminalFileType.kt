package site.laomst.ccbar.terminal.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import javax.swing.Icon

/**
 * 终端虚拟文件类型
 * 用于为编辑器区域中的终端 Tab 提供终端图标
 */
class TerminalFileType private constructor() : FileType {

    companion object {
        val INSTANCE = TerminalFileType()

        /** 终端图标，使用终端插件的类加载器加载 expui 终端图标 */
        val ICON: Icon = IconLoader.getIcon("/icons/expui/toolwindow/terminal.svg", ShellTerminalWidget::class.java)
    }

    override fun getName(): String = "CCBar Terminal"

    override fun getDefaultExtension(): String = "terminal"

    override fun getDescription(): String = "CCBar Terminal Session"

    override fun getIcon(): Icon = ICON

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true
}
