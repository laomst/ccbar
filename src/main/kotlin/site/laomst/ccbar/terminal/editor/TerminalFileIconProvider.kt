package site.laomst.ccbar.terminal.editor

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * 为 TerminalVirtualFile 提供自定义图标
 * 支持根据 Command 或 CommandBar 配置的图标显示在编辑器标签页上
 */
class TerminalFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file is TerminalVirtualFile) {
            return file.icon ?: TerminalFileType.INSTANCE.icon
        }
        return null
    }
}
