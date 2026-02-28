package com.github.ccbar.terminal

import com.github.ccbar.settings.ButtonConfig
import com.github.ccbar.settings.OptionConfig
import com.github.ccbar.settings.SubButtonConfig
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
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
        val dialog = CommandPreviewDialog(project, baseCommand, option.defaultTerminalName)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val command = dialog.fullCommand
        val workingDir = resolveWorkingDirectory(project, option)
        createTerminalAndExecute(project, command, terminalName, workingDir)
    }

    /**
     * 为 Button 直接命令模式打开终端
     */
    fun openTerminalForButton(project: Project, button: ButtonConfig) {
        val defaultName = button.defaultTerminalName.ifBlank { button.name }
        val dialog = CommandPreviewDialog(project, button.command, defaultName)
        if (!dialog.showAndGet()) {
            return
        }
        val terminalName = dialog.terminalName
        val command = dialog.fullCommand
        val workingDir = resolveWorkingDirectoryForButton(project, button)
        createTerminalAndExecute(project, command, terminalName, workingDir)
    }

    private fun buildCommand(option: OptionConfig, subButton: SubButtonConfig?): String {
        val baseCommand = option.baseCommand
        val params = subButton?.params?.trim() ?: ""
        return if (params.isNotEmpty()) "$baseCommand $params" else baseCommand
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
        workingDir: String
    ) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val widget = createTerminalWidget(project, tabName, workingDir)
                if (widget != null) {
                    executeCommandOnWidget(project, widget, command)
                } else {
                    showNotification(project, "终端创建失败", "无法创建终端，不支持当前 IDE 版本", NotificationType.ERROR)
                }
            } catch (e: Exception) {
                LOG.warn("CCBar: 终端创建/命令执行异常", e)
                showNotification(project, "终端创建失败", "无法创建终端: ${e.message}", NotificationType.ERROR)
            }
        }
    }

    /**
     * 创建终端 widget，依次尝试多种 API
     */
    private fun createTerminalWidget(project: Project, tabName: String, workingDir: String): Any? {
        // 策略1: TerminalView.createLocalShellWidget (2024.2+)
        try {
            val viewClass = Class.forName("org.jetbrains.plugins.terminal.TerminalView")
            val getInstance = viewClass.getMethod("getInstance", Project::class.java)
            val view = getInstance.invoke(null, project)

            // 尝试带3个参数的版本 (path, tabName, requestFocus)
            try {
                val create = viewClass.getMethod(
                    "createLocalShellWidget",
                    String::class.java, String::class.java, Boolean::class.java
                )
                val widget = create.invoke(view, workingDir, tabName, true)
                if (widget != null) {
                    LOG.info("CCBar: 使用 TerminalView.createLocalShellWidget(3) 创建终端成功")
                    return widget
                }
            } catch (_: NoSuchMethodException) {}

            // 尝试带2个参数的版本 (path, tabName)
            try {
                val create = viewClass.getMethod(
                    "createLocalShellWidget",
                    String::class.java, String::class.java
                )
                val widget = create.invoke(view, workingDir, tabName)
                if (widget != null) {
                    LOG.info("CCBar: 使用 TerminalView.createLocalShellWidget(2) 创建终端成功")
                    return widget
                }
            } catch (_: NoSuchMethodException) {}

        } catch (e: Exception) {
            LOG.info("CCBar: TerminalView 策略失败: ${e.message}")
        }

        // 策略2: TerminalToolWindowManager.createLocalShellWidget
        try {
            val mgrClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
            val getInstance = mgrClass.getMethod("getInstance", Project::class.java)
            val mgr = getInstance.invoke(null, project)

            try {
                val create = mgrClass.getMethod(
                    "createLocalShellWidget",
                    String::class.java, String::class.java
                )
                val widget = create.invoke(mgr, workingDir, tabName)
                if (widget != null) {
                    LOG.info("CCBar: 使用 TerminalToolWindowManager.createLocalShellWidget 创建终端成功")
                    return widget
                }
            } catch (_: NoSuchMethodException) {}

        } catch (e: Exception) {
            LOG.info("CCBar: TerminalToolWindowManager 策略失败: ${e.message}")
        }

        return null
    }

    /**
     * 在 widget 上执行命令，依次尝试多种方法
     */
    private fun executeCommandOnWidget(project: Project, widget: Any, command: String) {
        val widgetClass = widget.javaClass
        LOG.info("CCBar: widget 实际类型: ${widgetClass.name}")

        // 方法1: executeCommand(String) — ShellTerminalWidget 经典方法
        if (tryInvokeMethod(widget, "executeCommand", command)) {
            LOG.info("CCBar: 使用 executeCommand 执行命令成功")
            return
        }

        // 方法2: typedShellCommand(String) — 部分版本的替代方法
        if (tryInvokeMethod(widget, "typedShellCommand", command)) {
            LOG.info("CCBar: 使用 typedShellCommand 执行命令成功")
            return
        }

        // 方法3: sendCommandToTerminal(String)
        if (tryInvokeMethod(widget, "sendCommandToTerminal", command)) {
            LOG.info("CCBar: 使用 sendCommandToTerminal 执行命令成功")
            return
        }

        // 方法4: 通过 TerminalStarter 发送文本
        if (trySendViaTerminalStarter(widget, command)) {
            LOG.info("CCBar: 使用 TerminalStarter.sendString 执行命令成功")
            return
        }

        // 方法5: 通过 ProcessHandler 的 stdin 写入
        if (trySendViaProcessInput(widget, command)) {
            LOG.info("CCBar: 使用 ProcessHandler 写入命令成功")
            return
        }

        // 所有方法都失败了，记录可用方法以便排查
        val methods = widgetClass.methods.map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
        LOG.warn("CCBar: 所有命令执行方式均失败。widget 类: ${widgetClass.name}，可用方法: $methods")

        showNotification(
            project,
            "命令执行失败",
            "终端已创建，但无法自动执行命令。请手动输入命令: $command",
            NotificationType.WARNING
        )
    }

    /**
     * 尝试通过反射调用 widget 上的单参数 String 方法
     */
    private fun tryInvokeMethod(widget: Any, methodName: String, arg: String): Boolean {
        return try {
            val method = findMethodInHierarchy(widget.javaClass, methodName, String::class.java)
            if (method != null) {
                method.invoke(widget, arg)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            LOG.info("CCBar: 调用 $methodName 失败: ${e.message}")
            false
        }
    }

    /**
     * 通过 TerminalStarter 发送命令文本
     */
    private fun trySendViaTerminalStarter(widget: Any, command: String): Boolean {
        return try {
            val getStarter = findMethodInHierarchy(widget.javaClass, "getTerminalStarter")
                ?: return false
            val starter = getStarter.invoke(widget) ?: return false

            // 尝试 sendString(String, boolean) 签名
            try {
                val sendMethod = starter.javaClass.getMethod("sendString", String::class.java, Boolean::class.javaPrimitiveType)
                sendMethod.invoke(starter, command + "\n", false)
                return true
            } catch (_: NoSuchMethodException) {}

            // 尝试 sendString(String) 签名
            try {
                val sendMethod = starter.javaClass.getMethod("sendString", String::class.java)
                sendMethod.invoke(starter, command + "\n")
                return true
            } catch (_: NoSuchMethodException) {}

            false
        } catch (e: Exception) {
            LOG.info("CCBar: TerminalStarter 方式失败: ${e.message}")
            false
        }
    }

    /**
     * 通过 ProcessHandler 的输入流写入命令
     */
    private fun trySendViaProcessInput(widget: Any, command: String): Boolean {
        return try {
            // 遍历 widget 类层级寻找 getProcessHandler 或 getProcessTtyConnector
            val handler = tryGetField(widget, "getProcessHandler")
                ?: tryGetField(widget, "getProcessTtyConnector")
                ?: return false

            // 尝试通过 ProcessHandler.getProcessInput()
            try {
                val getInput = handler.javaClass.getMethod("getProcessInput")
                val outputStream = getInput.invoke(handler) as? java.io.OutputStream
                if (outputStream != null) {
                    outputStream.write((command + "\n").toByteArray())
                    outputStream.flush()
                    return true
                }
            } catch (_: Exception) {}

            // 尝试通过 TtyConnector.write(String)
            try {
                val writeMethod = handler.javaClass.getMethod("write", String::class.java)
                writeMethod.invoke(handler, command + "\n")
                return true
            } catch (_: Exception) {}

            // 尝试通过 TtyConnector.write(ByteArray)
            try {
                val writeMethod = handler.javaClass.getMethod("write", ByteArray::class.java)
                writeMethod.invoke(handler, (command + "\n").toByteArray())
                return true
            } catch (_: Exception) {}

            false
        } catch (e: Exception) {
            LOG.info("CCBar: ProcessHandler 方式失败: ${e.message}")
            false
        }
    }

    private fun tryGetField(obj: Any, getterName: String): Any? {
        return try {
            val method = findMethodInHierarchy(obj.javaClass, getterName)
            method?.invoke(obj)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 在类层级结构中查找方法（包括父类和接口）
     */
    private fun findMethodInHierarchy(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *paramTypes).also { it.isAccessible = true }
            } catch (_: NoSuchMethodException) {}
            current = current.superclass
        }
        // 也检查所有接口
        for (method in clazz.methods) {
            if (method.name == name && method.parameterCount == paramTypes.size) {
                var match = true
                for (i in paramTypes.indices) {
                    if (!method.parameterTypes[i].isAssignableFrom(paramTypes[i])) {
                        match = false
                        break
                    }
                }
                if (match) return method
            }
        }
        return null
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