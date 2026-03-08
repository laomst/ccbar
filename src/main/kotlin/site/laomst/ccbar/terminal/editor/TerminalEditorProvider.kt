package site.laomst.ccbar.terminal.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 终端编辑器提供者
 * 当 FileEditorManager 打开 TerminalVirtualFile 时，
 * 由本类创建对应的 TerminalFileEditor
 */
class TerminalEditorProvider : FileEditorProvider, DumbAware {

    override fun getEditorTypeId(): String = "ccbar-terminal-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is TerminalVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val terminalFile = file as TerminalVirtualFile
        return TerminalFileEditor(project, terminalFile)
    }
}
