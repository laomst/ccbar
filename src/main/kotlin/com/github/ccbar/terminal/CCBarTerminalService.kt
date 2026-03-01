package com.github.ccbar.terminal

import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.settings.OptionConfig
import com.github.ccbar.settings.SubButtonConfig
import com.github.ccbar.settings.TerminalMode
import com.github.ccbar.terminal.editor.TerminalEditorService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.io.File

/**
 * CCBar 终端服务
 * 负责创建终端并执行命令
 */
object CCBarTerminalService {

    private const val NOTIFICATION_GROUP_ID = "CCBar"
    private val LOG = Logger.getInstance(CCBarTerminalService::class.java)

    /**
     * 为 Option 打开终端（选项列表模式）
     */
    fun openTerminal(project: Project, option: OptionConfig, subButton: SubButtonConfig?) {
        val baseCommand = buildCommand(option, subButton)
        val defaultOpenInEditor = option.terminalMode == TerminalMode.EDITOR
        val dialog = CommandPreviewDialog(project, baseCommand, option.defaultTerminalName, defaultOpenInEditor, option.envVariables)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val command = buildCommandWithEnv(project, dialog.envVariables, dialog.fullCommand)
        val workingDir = resolveWorkingDirectory(project, option)
        val openInEditor = dialog.openInEditor
        createTerminalAndExecute(project, command, terminalName, workingDir, openInEditor)
    }

    /**
     * 为 Button 直接命令模式打开终端
     */
    fun openTerminalForButton(project: Project, button: ButtonConfig) {
        val defaultName = button.defaultTerminalName.ifBlank { button.name }
        val defaultOpenInEditor = button.terminalMode == TerminalMode.EDITOR
        val dialog = CommandPreviewDialog(project, button.command, defaultName, defaultOpenInEditor, button.envVariables)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val command = buildCommandWithEnv(project, dialog.envVariables, dialog.fullCommand)
        val workingDir = resolveWorkingDirectoryForButton(project, button)
        val openInEditor = dialog.openInEditor
        createTerminalAndExecute(project, command, terminalName, workingDir, openInEditor)
    }

    private fun buildCommand(option: OptionConfig, subButton: SubButtonConfig?): String {
        val baseCommand = option.baseCommand
        val params = subButton?.params?.trim() ?: ""
        return if (params.isNotEmpty()) "$baseCommand $params" else baseCommand
    }

    /**
     * 解析环境变量字符串为键值对列表
     * 格式：KEY1=val1;KEY2=val2，按第一个 = 分割
     */
    private fun parseEnvVariables(envVars: String): List<Pair<String, String>> {
        if (envVars.isBlank()) return emptyList()
        return envVars.split(";").mapNotNull { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                trimmed.substring(0, eqIndex).trim() to trimmed.substring(eqIndex + 1).trim()
            } else {
                null
            }
        }
    }

    /**
     * 根据 IDE 配置的 shell 类型构建带环境变量注入的命令
     * PowerShell: $env:K1="v1"; $env:K2="v2"; command
     * cmd.exe: set K1=v1&& set K2=v2&& command
     * bash/zsh/fish 等: export K1=v1; export K2=v2; command
     */
    private fun buildCommandWithEnv(project: Project, envVars: String, command: String): String {
        val vars = parseEnvVariables(envVars)
        if (vars.isEmpty()) return command

        val shellType = getShellType(project)
        val envPrefix = vars.joinToString("; ") { (key, value) ->
            when (shellType) {
                ShellType.POWERSHELL -> "\$env:$key=\"$value\""
                ShellType.CMD -> "set $key=$value&&"
                ShellType.POSIX -> "export $key=$value"
            }
        }
        return if (shellType == ShellType.CMD) {
            // cmd.exe: set K=V&& command（&& 已在 joinToString 中）
            "$envPrefix $command"
        } else {
            "$envPrefix; $command"
        }
    }

    private enum class ShellType {
        POSIX,       // bash, zsh, sh, fish 等
        POWERSHELL,  // powershell, pwsh
        CMD          // cmd.exe
    }

    /**
     * 根据 IDE 终端配置的 shell 路径判断 shell 类型
     */
    private fun getShellType(project: Project): ShellType {
        val shellPath = try {
            TerminalProjectOptionsProvider.getInstance(project).shellPath
        } catch (e: Exception) {
            LOG.info("CCBar: 无法获取 IDE 终端 shell 路径，回退到 OS 检测: ${e.message}")
            return if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) {
                ShellType.POWERSHELL
            } else {
                ShellType.POSIX
            }
        }

        val shellName = shellPath.substringAfterLast("/").substringAfterLast("\\").lowercase()
        return when {
            shellName.contains("powershell") || shellName.contains("pwsh") -> ShellType.POWERSHELL
            shellName.contains("cmd") -> ShellType.CMD
            else -> ShellType.POSIX  // bash, zsh, sh, fish, etc.
        }
    }

    private fun resolveWorkingDirectory(project: Project, option: OptionConfig): String {
        val configuredDir = option.workingDirectory.trim()

        if (configuredDir.isNotEmpty()) {
            val dir = File(configuredDir)
            if (dir.exists() && dir.isDirectory) {
                return dir.absolutePath
            } else {
                showNotification(
                    project,
                    "工作目录不存在",
                    "配置的工作目录 '$configuredDir' 不存在，已回退到项目根目录",
                    NotificationType.WARNING
                )
            }
        }

        return project.basePath ?: System.getProperty("user.home")
    }

    private fun resolveWorkingDirectoryForButton(project: Project, button: ButtonConfig): String {
        val configuredDir = button.workingDirectory.trim()

        if (configuredDir.isNotEmpty()) {
            val dir = File(configuredDir)
            if (dir.exists() && dir.isDirectory) {
                return dir.absolutePath
            } else {
                showNotification(
                    project,
                    "工作目录不存在",
                    "配置的工作目录 '$configuredDir' 不存在，已回退到项目根目录",
                    NotificationType.WARNING
                )
            }
        }

        return project.basePath ?: System.getProperty("user.home")
    }

    private fun createTerminalAndExecute(
        project: Project,
        command: String,
        tabName: String,
        workingDir: String,
        openInEditor: Boolean = false
    ) {
        if (openInEditor) {
            try {
                TerminalEditorService.openInEditor(project, command, tabName, workingDir)
                return
            } catch (e: Exception) {
                LOG.warn("CCBar: 编辑器终端打开失败，回退到工具窗口", e)
            }
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                val manager = TerminalToolWindowManager.getInstance(project)
                val widget = manager.createShellWidget(workingDir, tabName, true, true)
                widget.sendCommandToExecute(command)
                LOG.info("CCBar: 终端创建并执行命令成功: $tabName")
            } catch (e: Exception) {
                LOG.warn("CCBar: 终端创建/命令执行异常", e)
                showNotification(project, "终端创建失败", "无法创建终端: ${e.message}", NotificationType.ERROR)
            }
        }
    }

    private fun showNotification(
        project: Project,
        title: String,
        content: String,
        type: NotificationType
    ) {
        val notification = Notification(NOTIFICATION_GROUP_ID, title, content, type)
        Notifications.Bus.notify(notification, project)
    }
}
