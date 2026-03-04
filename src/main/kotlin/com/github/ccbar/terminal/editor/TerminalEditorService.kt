package com.github.ccbar.terminal.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * 终端在编辑器中打开的服务入口
 */
object TerminalEditorService {

    private val LOG = Logger.getInstance(TerminalEditorService::class.java)

    /**
     * 在编辑器区域打开终端
     * @param iconPath 可选的图标路径，支持内置图标(builtin:xxx)、文件路径或网络URL
     */
    fun openInEditor(
        project: Project,
        command: String,
        terminalName: String,
        workingDir: String?,
        iconPath: String? = null
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val virtualFile = TerminalVirtualFile(
                    terminalName = terminalName,
                    workingDirectory = workingDir,
                    command = command,
                    iconPath = iconPath
                )

                FileEditorManager.getInstance(project).openFile(virtualFile, true)

                LOG.info("CCBar: 终端编辑器已打开: $terminalName")
            } catch (e: Exception) {
                LOG.error("CCBar: 在编辑器中打开终端失败", e)
            }
        }
    }
}
