package site.laomst.ccbar.terminal.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 终端文件编辑器
 * 通过 LocalTerminalDirectRunner 创建终端会话，
 * 获得与工具窗口终端完全一致的能力（shell integration、环境变量、customizer 等）
 */
class TerminalFileEditor(
    private val project: Project,
    private val terminalFile: TerminalVirtualFile
) : FileEditor {

    companion object {
        private val LOG = Logger.getInstance(TerminalFileEditor::class.java)
    }

    private val userDataHolder = UserDataHolderBase()
    private val editorDisposable: Disposable = Disposer.newDisposable("CCBar-Terminal-${terminalFile.terminalName}")

    private val runner: LocalTerminalDirectRunner = LocalTerminalDirectRunner.createTerminalRunner(project)

    private val terminalWidget: ShellTerminalWidget = ShellTerminalWidget(
        project,
        runner.settingsProvider,
        editorDisposable
    )

    private val mainPanel: JPanel = JPanel(BorderLayout()).apply {
        add(terminalWidget.component, BorderLayout.CENTER)
    }

    init {
        startTerminalSession()
    }

    /**
     * 通过 LocalTerminalDirectRunner.openSessionInDirectory 启动终端会话
     * 该方法内部会：
     * 1. 构造 ShellStartupOptions（包含 shell 路径、环境变量、shell integration 脚本等）
     * 2. 在线程池中调用 createProcess() 创建 PTY 进程
     * 3. 调用 createTtyConnector() 创建连接器
     * 4. 在 EDT 中连接 widget 并启动终端会话
     */
    private fun startTerminalSession() {
        val workingDir = terminalFile.workingDirectory
            ?: project.basePath
            ?: System.getProperty("user.home")
        val command = terminalFile.command

        try {
            // 使用 LocalTerminalDirectRunner 的完整流程启动终端
            @Suppress("DEPRECATION")
            runner.openSessionInDirectory(terminalWidget, workingDir)

            // 命令执行需要在会话启动后进行
            if (!command.isNullOrBlank()) {
                scheduleCommandExecution(command)
            }

            LOG.info("CCBar: 终端在编辑器中启动成功（通过 LocalTerminalDirectRunner）: ${terminalFile.terminalName}")
        } catch (e: Exception) {
            LOG.error("CCBar: 终端会话启动失败", e)
        }
    }

    private fun scheduleCommandExecution(command: String) {
        try {
            terminalWidget.executeCommand(command)
        } catch (e: Exception) {
            LOG.warn("CCBar: executeCommand 失败，尝试通过 TerminalStarter 发送: ${e.message}")
            try {
                @Suppress("DEPRECATION")
                terminalWidget.terminalStarter?.sendString(command + "\n", false)
            } catch (e2: Exception) {
                LOG.error("CCBar: 命令执行完全失败", e2)
            }
        }
    }

    // ===== FileEditor 接口实现 =====

    override fun getComponent(): JComponent = mainPanel

    override fun getPreferredFocusedComponent(): JComponent = terminalWidget.component

    override fun getName(): String = terminalFile.terminalName

    @Suppress("DEPRECATION")
    override fun isValid(): Boolean = !Disposer.isDisposed(editorDisposable)

    override fun isModified(): Boolean = false

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) {}

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = terminalFile

    override fun <T : Any?> getUserData(key: Key<T>): T? = userDataHolder.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) = userDataHolder.putUserData(key, value)

    override fun dispose() {
        LOG.info("CCBar: 终端编辑器关闭: ${terminalFile.terminalName}")
        Disposer.dispose(editorDisposable)
    }
}
