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
     */
    fun openInEditor(
        project: Project,
        command: String,
        terminalName: String,
        workingDir: String?
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val virtualFile = TerminalVirtualFile(
                    terminalName = terminalName,
                    workingDirectory = workingDir,
                    command = command
                )

                FileEditorManager.getInstance(project).openFile(virtualFile, true)

                LOG.info("CCBar: 终端编辑器已打开: $terminalName")
            } catch (e: Exception) {
                LOG.error("CCBar: 在编辑器中打开终端失败", e)
            }
        }
    }
}
