package com.github.ccbar.terminal

import com.github.ccbar.icons.CCBarIcons
import com.github.ccbar.settings.CommandBarConfig
import com.github.ccbar.settings.CommandConfig
import com.github.ccbar.settings.QuickParamConfig
import com.github.ccbar.settings.TerminalMode
import com.github.ccbar.terminal.editor.TerminalEditorService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
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
     * 为 Command 打开终端（Command 列表模式）
     * @param commonEnvVars CommandBar 级别的公共环境变量
     */
    fun openTerminal(project: Project, command: CommandConfig, quickParam: QuickParamConfig?, commonEnvVars: String = "") {
        val baseCommand = buildCommand(command, quickParam)
        val defaultOpenInEditor = command.terminalMode == TerminalMode.EDITOR
        val mergedEnvVars = mergeEnvVariables(commonEnvVars, command.envVariables)
        val dialog = CommandPreviewDialog(project, baseCommand, command.defaultTerminalName, defaultOpenInEditor, mergedEnvVars)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val finalCommand = buildCommandWithEnv(project, dialog.envVariables, dialog.fullCommand)
        val workingDir = resolveWorkingDirectory(project, command)
        val openInEditor = dialog.openInEditor
        createTerminalAndExecute(project, finalCommand, terminalName, workingDir, openInEditor, command.icon)
    }

    /**
     * 为 CommandBar 直接命令模式打开终端
     */
    fun openTerminalForCommandBar(project: Project, commandBar: CommandBarConfig) {
        val defaultName = commandBar.defaultTerminalName.ifBlank { commandBar.name }
        val defaultOpenInEditor = commandBar.terminalMode == TerminalMode.EDITOR
        val mergedEnvVars = mergeEnvVariables(commandBar.commonEnvVariables, commandBar.envVariables)
        val dialog = CommandPreviewDialog(project, commandBar.command, defaultName, defaultOpenInEditor, mergedEnvVars)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val finalCommand = buildCommandWithEnv(project, dialog.envVariables, dialog.fullCommand)
        val workingDir = resolveWorkingDirectoryForCommandBar(project, commandBar)
        val openInEditor = dialog.openInEditor
        createTerminalAndExecute(project, finalCommand, terminalName, workingDir, openInEditor, commandBar.icon)
    }

    private fun buildCommand(command: CommandConfig, quickParam: QuickParamConfig?): String {
        val baseCommand = command.baseCommand
        val params = quickParam?.params?.trim() ?: ""
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
     * 合并两层环境变量，overrideEnvVars 中的同名变量覆盖 baseEnvVars
     */
    private fun mergeEnvVariables(baseEnvVars: String, overrideEnvVars: String): String {
        val baseVars = parseEnvVariables(baseEnvVars)
        val overrideVars = parseEnvVariables(overrideEnvVars)
        if (baseVars.isEmpty()) return overrideEnvVars.trim()
        if (overrideVars.isEmpty()) return baseEnvVars.trim()
        val merged = LinkedHashMap<String, String>()
        baseVars.forEach { (k, v) -> merged[k] = v }
        overrideVars.forEach { (k, v) -> merged[k] = v }
        return merged.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    /**
     * 根据 IDE 配置的 shell 类型构建带环境变量注入的命令
     * PowerShell: $env:K1="v1"; $env:K2="v2"; command
     * cmd.exe: set K1=v1&& set K2=v2&& command
     * bash/zsh/fish 等：export K1=v1; export K2=v2; command
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
            LOG.info("CCBar: 无法获取 IDE 终端 shell 路径，回退到 OS 检测：${e.message}")
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

    private fun resolveWorkingDirectory(project: Project, command: CommandConfig): String {
        val configuredDir = command.workingDirectory.trim()

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

    private fun resolveWorkingDirectoryForCommandBar(project: Project, commandBar: CommandBarConfig): String {
        val configuredDir = commandBar.workingDirectory.trim()

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
        openInEditor: Boolean = false,
        iconPath: String? = null
    ) {
        if (openInEditor) {
            try {
                TerminalEditorService.openInEditor(project, command, tabName, workingDir, iconPath)
                return
            } catch (e: Exception) {
                LOG.warn("CCBar: 编辑器终端打开失败，回退到工具窗口", e)
            }
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                val manager = TerminalToolWindowManager.getInstance(project)

                // 获取终端工具窗口的 ContentManager，用于后续设置图标
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
                val contentManager = toolWindow?.contentManager

                // 创建终端
                val widget = manager.createShellWidget(workingDir, tabName, true, true)

                // 执行命令
                widget.sendCommandToExecute(command)

                // 设置终端标签页图标 - 通过 ContentManager 获取新创建的 Content
                // 注意：需要在 EDT 上延迟执行，确保 Content 已完全添加到 ContentManager
                if (!iconPath.isNullOrBlank() && contentManager != null) {
                    // 方式 1：延迟查找并设置图标（兼容大多数情况）
                    ApplicationManager.getApplication().invokeLater {
                        try {
                            setTerminalTabIcon(contentManager, tabName, iconPath, project)
                        } catch (e: Exception) {
                            LOG.info("CCBar: 设置终端标签页图标失败：${e.message}")
                        }
                    }
                }

                LOG.info("CCBar: 终端创建并执行命令成功：$tabName")
            } catch (e: Exception) {
                LOG.warn("CCBar: 终端创建/命令执行异常", e)
                showNotification(project, "终端创建失败", "无法创建终端：${e.message}", NotificationType.ERROR)
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

    /**
     * 设置终端标签页图标
     * 采用多重策略确保图标设置成功：
     * 1. 首先尝试通过 tabName 匹配 Content
     * 2. 备选方案：查找最后一个 Content（假设新创建的）
     */
    @Suppress("UNCHECKED_CAST")
    private fun setTerminalTabIcon(
        contentManager: Any,
        terminalName: String,
        iconPath: String,
        project: Project
    ) {
        // 使用反射访问 ContentManager 的 contents 属性
        val contentsMethod = contentManager.javaClass.getMethod("getContents")
        val contents = contentsMethod.invoke(contentManager) as? Array<Any>
            ?: return

        // 获取 Content 类的 tabName 和 setIcon 方法
        val getTabNameMethod = Class.forName("com.intellij.openapi.wm.Content")
            .getMethod("getTabName")
        val setIconMethod = Class.forName("com.intellij.openapi.wm.Content")
            .getMethod("setIcon", javax.swing.Icon::class.java)

        // 策略 1：通过 tabName 匹配
        val contentByName = contents.firstOrNull {
            getTabNameMethod.invoke(it) as? String == terminalName
        }
        if (contentByName != null) {
            val icon = CCBarIcons.loadIcon(iconPath, project)
            setIconMethod.invoke(contentByName, icon)
            LOG.info("CCBar: 已设置终端标签页图标（通过名称匹配）：$terminalName")
            return
        }

        // 策略 2：使用最后一个 Content（假设新创建的）
        if (contents.isNotEmpty()) {
            val lastContent = contents.last()
            val icon = CCBarIcons.loadIcon(iconPath, project)
            setIconMethod.invoke(lastContent, icon)
            LOG.info("CCBar: 已设置终端标签页图标（通过最后一个 Content）：$terminalName")
        } else {
            LOG.warn("CCBar: 未找到 Content 来设置图标：$terminalName")
        }
    }
}
