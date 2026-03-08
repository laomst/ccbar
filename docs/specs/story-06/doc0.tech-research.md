# CCBar 终端在编辑器区域打开技术调研报告

> 调研日期：2026-03-01
> 调研目标：研究如何在点击选项触发打开终端窗口时，让终端窗口直接在编辑器区域打开（以编辑器 Tab 形式呈现）
> 目标平台：IntelliJ IDEA 2024.2+（sinceBuild = "242"）

## 1. 当前实现分析

### 1.1 现有终端创建流程

当前 CCBar 插件通过 `CCBarTerminalService` 在**终端工具窗口**中打开终端：

```
用户点击按钮/选项 → CommandPreviewDialog（命令预览） → CCBarTerminalService.createTerminalAndExecute()
    ├── createTerminalWidget()  ← 通过反射调用 TerminalView/TerminalToolWindowManager API
    └── executeCommandOnWidget() ← 通过反射尝试多种命令执行方式
```

具体的 API 调用策略（反射方式）：
1. `TerminalView.getInstance(project).createLocalShellWidget(workingDir, tabName, true)`
2. `TerminalView.getInstance(project).createLocalShellWidget(workingDir, tabName)`
3. `TerminalToolWindowManager.getInstance(project).createLocalShellWidget(workingDir, tabName)`

命令执行策略（反射方式）：
1. `widget.executeCommand(command)` — ShellTerminalWidget 的方法
2. `widget.typedShellCommand(command)`
3. `widget.sendCommandToTerminal(command)`
4. `widget.getTerminalStarter().sendString(command + "\n")`
5. `ProcessHandler/TtyConnector` 的输入流写入

### 1.2 当前实现的局限

1. 终端固定在底部工具窗口中打开，占用额外空间
2. 与编辑器区域分离，需要在两个区域之间切换注意力
3. 对于长时间运行的 AI 编程助手（如 Claude Code），在编辑器区域打开更符合使用习惯

## 2. 核心 API 调研（已验证）

### 2.1 JediTermWidget —— 终端模拟器核心

`JediTermWidget` 是 [JediTerm](https://github.com/JetBrains/jediterm) 库的核心组件，也是 IntelliJ 终端的底层实现。

**已验证的关键方法**：

| 方法 | 说明 | 来源验证 |
|------|------|----------|
| `setTtyConnector(TtyConnector)` | 设置 TTY 连接器 | JediTermWidget.java 源码 |
| `createTerminalSession(TtyConnector)` | 内部调用 `setTtyConnector()` 并返回 `this` | JediTermWidget.java 源码 |
| `start()` | 启动终端会话 | BasicTerminalShellExample.java |
| `stop()` | 停止终端会话 | JediTermWidget.java 源码 |
| `isSessionRunning()` | 检查会话是否运行中 | JediTermWidget.java 源码 |
| `getTtyConnector()` | 获取当前 TtyConnector | JediTermWidget.java 源码 |

**经典用法**（来自 [BasicTerminalShellExample.java](https://github.com/JetBrains/jediterm/blob/master/JediTerm/src/main/java/com/jediterm/example/BasicTerminalShellExample.java)）：

```java
JediTermWidget widget = new JediTermWidget(80, 24, new DefaultSettingsProvider());
widget.setTtyConnector(createTtyConnector());
widget.start();
```

### 2.2 JBTerminalWidget —— IntelliJ 平台封装

`JBTerminalWidget` 继承自 `JediTermWidget`，是 IntelliJ 平台对终端组件的封装。

**已验证的构造函数**（来自 [非官方 API 文档](https://dploeger.github.io/intellij-api-doc/com/intellij/terminal/JBTerminalWidget.html)）：

```java
// 主要构造函数（推荐使用）
public JBTerminalWidget(
    Project project,
    JBTerminalSystemSettingsProviderBase settingsProvider,
    Disposable parent
)

// 扩展构造函数（指定终端尺寸）
public JBTerminalWidget(
    Project project,
    int columns, int lines,
    JBTerminalSystemSettingsProviderBase settingsProvider,
    TerminalExecutionConsole console,
    Disposable parent
)
```

**注意**：
- 构造函数需要 `JBTerminalSystemSettingsProviderBase`，而非简单的 boolean 参数
- 需要传入 `Disposable parent` 用于资源生命周期管理
- **不存在** `setWorkingDirectory()`、`createTerminal()`、`executeCommand()` 方法

### 2.3 ShellTerminalWidget —— Shell 交互扩展

`ShellTerminalWidget` 继承自 `JBTerminalWidget`，提供 Shell 层面的交互能力。

**已验证的构造函数**（来自 [ShellTerminalWidget.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/ShellTerminalWidget.java)）：

```java
// 推荐构造函数
public ShellTerminalWidget(
    Project project,
    JBTerminalSystemSettingsProvider settingsProvider,
    Disposable parent
)
```

**已验证的关键方法**：

| 方法 | 说明 |
|------|------|
| `executeCommand(String command)` | 发送命令到 shell |
| `getProcessTtyConnector()` | 获取 ProcessTtyConnector |
| `hasRunningCommands()` | 检查是否有正在运行的命令 |
| `getTerminalTextBuffer()` | 获取终端文本缓冲区 |

**静态工具方法**：
- `ShellTerminalWidget.asShellJediTermWidget(TerminalWidget)` — 尝试转型
- `ShellTerminalWidget.toShellJediTermWidgetOrThrow(TerminalWidget)` — 转型或抛异常

### 2.4 PTY 进程创建 —— pty4j

[pty4j](https://github.com/JetBrains/pty4j) 是 JetBrains 的 PTY 库，用于创建伪终端进程。IntelliJ 终端插件已包含此依赖。

**已验证的用法**（来自 JediTerm 示例和 LocalTerminalDirectRunner 源码）：

```kotlin
import com.pty4j.PtyProcessBuilder

val process = PtyProcessBuilder()
    .setCommand(arrayOf("/bin/zsh", "--login"))  // Shell 命令
    .setDirectory(workingDirectory)              // 工作目录
    .setEnvironment(System.getenv())             // 环境变量
    .setInitialColumns(120)                      // 初始列数
    .setInitialRows(24)                          // 初始行数
    .setConsole(false)                           // 非控制台模式
    .start()
```

### 2.5 TtyConnector —— PTY 连接器

`TtyConnector` 是终端 widget 与 PTY 进程之间的桥梁。

**已验证的实现类**：
- `PtyProcessTtyConnector` — 包装 `PtyProcess`，是最常用的实现

```kotlin
import com.intellij.terminal.pty.PtyProcessTtyConnector
import java.nio.charset.StandardCharsets

val connector = PtyProcessTtyConnector(process, StandardCharsets.UTF_8)
```

**注意**：import 路径在不同版本中可能不同：
- IntelliJ 2024.x: `com.intellij.terminal.pty.PtyProcessTtyConnector`
- 更老版本: `com.jediterm.pty.PtyProcessTtyConnector`

### 2.6 LocalTerminalDirectRunner —— IntelliJ 终端进程启动器

[LocalTerminalDirectRunner](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/LocalTerminalDirectRunner.java) 是 IntelliJ 内部用于启动本地终端进程的类。

**已验证的关键 API**：

```java
// 获取实例（静态工厂方法）
LocalTerminalDirectRunner.createTerminalRunner(Project project)

// 关键方法
public PtyProcess createProcess(ShellStartupOptions options) throws ExecutionException
public TtyConnector createTtyConnector(PtyProcess process)  // 返回 PtyProcessTtyConnector
public ShellStartupOptions configureStartupOptions(ShellStartupOptions options)
```

**内部流程**：
1. `configureStartupOptions()` → 调用 `LocalOptionsConfigurer`，应用 `LocalTerminalCustomizer` 扩展点
2. `createProcess()` → 使用 `PtyProcessBuilder` 创建进程
3. `createTtyConnector()` → 包装为 `PtyProcessTtyConnector`

**Shell 路径获取**：
- 通过 `TerminalProjectOptionsProvider.getInstance(project).getShellPath()` 获取用户配置的 Shell 路径

### 2.7 AbstractTerminalRunner —— 终端会话编排

[AbstractTerminalRunner](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/AbstractTerminalRunner.java) 是终端会话创建的核心编排类。

**已验证的完整流程**：

```
openSessionInDirectory(workingDir)
  → configureStartupOptions(ShellStartupOptions)
  → [线程池]:
      createProcess(options)          → PtyProcess
      createTtyConnector(process)     → TtyConnector
  → [EDT]:
      widget.createTerminalSession(connector)
      widget.start()
```

**创建 widget 的方法**：

```java
// 创建 ShellTerminalWidget
public ShellTerminalWidget createShellTerminalWidget(Disposable parent)
// 内部实现: new ShellTerminalWidget(myProject, mySettingsProvider, parent)
```

## 3. FileEditor 嵌入方案（已验证模式）

### 3.1 IntelliJ 平台标准模式

将自定义内容嵌入编辑器区域是 IntelliJ 平台的标准功能，通过 `FileEditorProvider` + `FileEditor` + `VirtualFile` 实现。

IntelliJ 终端插件自身也使用了这种模式（在 `terminal.xml` 中注册）：

```xml
<fileEditorProvider
    id="classic-terminal-session-editor"
    implementation="org.jetbrains.plugins.terminal.vfs.ClassicTerminalSessionEditorProvider"/>
```

对应的关键类：
- `TerminalSessionVirtualFileImpl` — 终端会话虚拟文件
- `ClassicTerminalSessionEditorProvider` — 终端编辑器提供者

**我们的方案采用完全相同的模式**，但独立实现以避免依赖内部 API。

### 3.2 整体架构

```
用户点击按钮/选项
  → CommandPreviewDialog（命令预览）
  → CCBarTerminalService.openTerminal()
      ├── [编辑器模式] TerminalEditorService.openInEditor()
      │   ├── 创建 TerminalVirtualFile（LightVirtualFile 子类）
      │   ├── FileEditorManager.openFile() 触发 TerminalEditorProvider
      │   ├── TerminalEditorProvider.createEditor() 创建 TerminalFileEditor
      │   ├── TerminalFileEditor 内部创建 ShellTerminalWidget
      │   ├── 在线程池中启动 PTY 进程 + TtyConnector
      │   ├── 在 EDT 中连接 widget ↔ connector 并启动
      │   └── 延迟执行用户命令
      └── [工具窗口模式] 原有流程（回退方案）
```

### 3.3 组件设计

#### 3.3.1 TerminalVirtualFile

使用 `LightVirtualFile` 作为基类，这是 IntelliJ 平台提供的轻量级虚拟文件实现，无需自定义 `VirtualFileSystem`。

```kotlin
package site.laomst.ccbar.terminal.editor

import com.intellij.testFramework.LightVirtualFile

/**
 * 终端虚拟文件
 * 用于在编辑器区域承载终端 Tab
 */
class TerminalVirtualFile(
    val terminalName: String,
    val workingDirectory: String?,
    val command: String?
) : LightVirtualFile("$terminalName.terminal") {

    init {
        isWritable = false
    }

    override fun isValid(): Boolean = true

    /**
     * 用于编辑器 Tab 显示的名称
     */
    override fun getPresentableName(): String = terminalName
}
```

**设计要点**：
- 继承 `LightVirtualFile` 而非自定义 `VirtualFileSystem`，避免需要实现大量无关的抽象方法（`deleteFile`、`moveFile`、`renameFile` 等）
- `LightVirtualFile` 自带 `NonPhysicalFileSystem`，平台已内置完善支持
- 通过 `instanceof` 类型检查（`file is TerminalVirtualFile`）识别终端文件，而非脆弱的 URL 字符串匹配
- 终端配置信息（名称、工作目录、命令）作为字段直接存储在文件对象上

#### 3.3.2 TerminalEditorProvider

```kotlin
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
```

**设计要点**：
- 实现 `DumbAware` 接口，确保在索引构建期间也能工作
- `accept()` 使用 `is` 类型检查，简洁且类型安全
- `getPolicy()` 返回 `HIDE_DEFAULT_EDITOR`，防止平台尝试用文本编辑器打开 `.terminal` 文件

#### 3.3.3 TerminalFileEditor

这是最核心的组件，负责创建终端 widget、启动 PTY 进程、管理生命周期。

```kotlin
package site.laomst.ccbar.terminal.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.JBTerminalSystemSettingsProvider
import com.pty4j.PtyProcessBuilder
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 终端文件编辑器
 * 将 ShellTerminalWidget 嵌入编辑器 Tab 中
 */
class TerminalFileEditor(
    private val project: Project,
    private val terminalFile: TerminalVirtualFile
) : FileEditor {

    companion object {
        private val LOG = Logger.getInstance(TerminalFileEditor::class.java)
    }

    // 编辑器生命周期的 Disposable 父节点
    private val editorDisposable: Disposable = Disposer.newDisposable("CCBar-Terminal-${terminalFile.terminalName}")

    // 终端 widget
    private val terminalWidget: ShellTerminalWidget = ShellTerminalWidget(
        project,
        JBTerminalSystemSettingsProvider(),
        editorDisposable
    )

    // 主面板
    private val mainPanel: JPanel = JPanel(BorderLayout()).apply {
        add(terminalWidget.component, BorderLayout.CENTER)
    }

    // 终端会话是否已启动
    private var sessionStarted = false

    init {
        startTerminalSession()
    }

    /**
     * 启动终端会话
     * 在线程池中创建 PTY 进程，然后在 EDT 中连接到 widget
     */
    private fun startTerminalSession() {
        val workingDir = terminalFile.workingDirectory
            ?: project.basePath
            ?: System.getProperty("user.home")
        val command = terminalFile.command

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 1. 获取用户配置的 Shell 路径
                val shellPath = getShellPath()

                // 2. 创建 PTY 进程
                val process = PtyProcessBuilder()
                    .setCommand(arrayOf(shellPath))
                    .setDirectory(workingDir)
                    .setEnvironment(System.getenv())
                    .setInitialColumns(120)
                    .setInitialRows(24)
                    .setConsole(false)
                    .start()

                // 3. 创建 TtyConnector
                val connector = createTtyConnector(process)

                // 4. 在 EDT 中连接并启动终端
                ApplicationManager.getApplication().invokeLater {
                    if (Disposer.isDisposed(editorDisposable)) {
                        process.destroy()
                        return@invokeLater
                    }

                    try {
                        terminalWidget.createTerminalSession(connector)
                        terminalWidget.start()
                        sessionStarted = true

                        // 5. 执行用户命令（如果有）
                        if (!command.isNullOrBlank()) {
                            scheduleCommandExecution(command)
                        }

                        LOG.info("CCBar: 终端在编辑器中启动成功: ${terminalFile.terminalName}")
                    } catch (e: Exception) {
                        LOG.error("CCBar: 终端会话启动失败", e)
                        process.destroy()
                    }
                }
            } catch (e: Exception) {
                LOG.error("CCBar: PTY 进程创建失败", e)
            }
        }
    }

    /**
     * 获取用户配置的 Shell 路径
     * 优先使用 IntelliJ 终端设置中的配置
     */
    private fun getShellPath(): String {
        return try {
            // 通过反射获取 TerminalProjectOptionsProvider，避免直接依赖可能变更的 API
            val providerClass = Class.forName(
                "org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider"
            )
            val getInstance = providerClass.getMethod("getInstance", Project::class.java)
            val provider = getInstance.invoke(null, project)
            val getShellPath = providerClass.getMethod("getShellPath")
            val shellPath = getShellPath.invoke(provider) as? String
            if (!shellPath.isNullOrBlank()) shellPath else defaultShellPath()
        } catch (e: Exception) {
            LOG.info("CCBar: 无法获取终端设置中的 Shell 路径，使用默认值: ${e.message}")
            defaultShellPath()
        }
    }

    /**
     * 平台默认 Shell 路径
     */
    private fun defaultShellPath(): String {
        return if (System.getProperty("os.name").lowercase().contains("win")) {
            "cmd.exe"
        } else {
            System.getenv("SHELL") ?: "/bin/bash"
        }
    }

    /**
     * 创建 TtyConnector
     * 兼容不同版本的 import 路径
     */
    private fun createTtyConnector(process: com.pty4j.PtyProcess): com.jediterm.terminal.TtyConnector {
        // 优先尝试 IntelliJ 平台的 PtyProcessTtyConnector
        try {
            val connectorClass = Class.forName("com.intellij.terminal.pty.PtyProcessTtyConnector")
            val constructor = connectorClass.getConstructor(
                com.pty4j.PtyProcess::class.java,
                java.nio.charset.Charset::class.java
            )
            return constructor.newInstance(process, StandardCharsets.UTF_8) as com.jediterm.terminal.TtyConnector
        } catch (_: Exception) {}

        // 回退到 jediterm 的 PtyProcessTtyConnector
        try {
            val connectorClass = Class.forName("com.jediterm.pty.PtyProcessTtyConnector")
            val constructor = connectorClass.getConstructor(
                com.pty4j.PtyProcess::class.java,
                java.nio.charset.Charset::class.java
            )
            return constructor.newInstance(process, StandardCharsets.UTF_8) as com.jediterm.terminal.TtyConnector
        } catch (_: Exception) {}

        throw IllegalStateException("无法创建 PtyProcessTtyConnector，当前 IDE 版本不支持")
    }

    /**
     * 延迟执行命令
     * 等待终端 Shell 提示符就绪后再发送命令
     */
    private fun scheduleCommandExecution(command: String) {
        // 使用 ShellTerminalWidget 的 executeCommand 方法
        // 该方法内部会等待 shell integration 就绪或使用 fallback
        try {
            terminalWidget.executeCommand(command)
        } catch (e: Exception) {
            LOG.warn("CCBar: executeCommand 失败，尝试通过 TerminalStarter 发送: ${e.message}")
            // 回退方案：直接通过 TerminalStarter 发送字符串
            try {
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

    override fun isValid(): Boolean = !Disposer.isDisposed(editorDisposable)

    override fun isModified(): Boolean = false

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) {}

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    override fun getFile(): VirtualFile = terminalFile

    override fun dispose() {
        LOG.info("CCBar: 终端编辑器关闭: ${terminalFile.terminalName}")
        Disposer.dispose(editorDisposable)
    }
}
```

**设计要点**：

1. **PTY 进程创建在线程池中执行**：`PtyProcessBuilder.start()` 可能阻塞，不能在 EDT 上运行
2. **连接和启动在 EDT 中执行**：Swing 组件操作必须在 EDT 上
3. **使用 `ShellTerminalWidget` 而非 `JBTerminalWidget`**：因为需要 `executeCommand()` 方法
4. **Disposable 层级设计**：`editorDisposable` 作为 `ShellTerminalWidget` 的父 Disposable，编辑器关闭时自动级联销毁 widget 及其资源
5. **TtyConnector 创建兼容多版本**：通过反射尝试不同的类路径
6. **Shell 路径尊重用户设置**：优先从 `TerminalProjectOptionsProvider` 获取
7. **销毁前检查**：在 `invokeLater` 回调中检查 `Disposer.isDisposed()`，防止编辑器已关闭但回调仍在执行

#### 3.3.4 TerminalEditorService —— 入口服务

```kotlin
package site.laomst.ccbar.terminal.editor

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

                // FileEditorManager.openFile 会触发 TerminalEditorProvider.createEditor()
                FileEditorManager.getInstance(project).openFile(virtualFile, true)

                LOG.info("CCBar: 终端编辑器已打开: $terminalName")
            } catch (e: Exception) {
                LOG.error("CCBar: 在编辑器中打开终端失败", e)
            }
        }
    }
}
```

### 3.4 plugin.xml 注册

```xml

<extensions defaultExtensionNs="com.intellij">
    <!-- 终端编辑器提供者 -->
    <fileEditorProvider
            implementation="site.laomst.ccbar.terminal.editor.TerminalEditorProvider"/>
</extensions>
```

### 3.5 与现有代码的集成

修改 `CCBarTerminalService`，根据配置决定打开方式：

```kotlin
// CCBarTerminalService.kt 修改点

private fun createTerminalAndExecute(
    project: Project,
    command: String,
    tabName: String,
    workingDir: String
) {
    // 根据配置决定打开方式
    if (CCBarSettings.getInstance().state.openTerminalInEditor) {
        try {
            TerminalEditorService.openInEditor(project, command, tabName, workingDir)
        } catch (e: Exception) {
            LOG.warn("CCBar: 编辑器终端打开失败，回退到工具窗口", e)
            openInToolWindow(project, command, tabName, workingDir)
        }
    } else {
        openInToolWindow(project, command, tabName, workingDir)
    }
}

// 原有的工具窗口打开方式提取为独立方法
private fun openInToolWindow(project: Project, command: String, tabName: String, workingDir: String) {
    // 现有的 createTerminalWidget + executeCommandOnWidget 逻辑
}
```

### 3.6 配置项

在 `CCBarSettings.State` 中新增：

```kotlin
data class State(
    var commandBars: MutableList<CommandBarConfig> = mutableListOf(),
    // 新增：是否在编辑器区域打开终端（默认开启）
    var openTerminalInEditor: Boolean = true
)
```

## 4. 关键技术点与风险分析

### 4.1 已验证可行的部分

| 技术点 | 验证来源 | 可行性 |
|--------|----------|--------|
| `LightVirtualFile` 作为编辑器 Tab 载体 | IntelliJ 平台标准模式，广泛使用 | ✅ 确定可行 |
| `FileEditorProvider` + `FileEditor` 嵌入自定义内容 | IntelliJ 平台标准扩展点 | ✅ 确定可行 |
| `PtyProcessBuilder` 创建 PTY 进程 | pty4j 库，终端插件已包含 | ✅ 确定可行 |
| `ShellTerminalWidget` 构造与使用 | AbstractTerminalRunner 源码验证 | ✅ 确定可行 |
| `JediTermWidget.createTerminalSession()` + `start()` | JediTerm 源码验证 | ✅ 确定可行 |

### 4.2 需要注意的风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `PtyProcessTtyConnector` 类路径在不同版本间不同 | 编译/运行时异常 | 反射方式尝试多个路径 |
| `ShellTerminalWidget.executeCommand()` 在 shell 未就绪时调用 | 命令丢失 | 使用延迟发送 + 回退到 `TerminalStarter.sendString()` |
| `JBTerminalSystemSettingsProvider` 构造函数签名可能变化 | 编译错误 | 编译时验证，反射作为回退 |
| 编辑器 Tab 关闭时 PTY 进程未清理 | 进程泄漏 | Disposable 层级 + `ProcessHandler.destroyProcess()` |
| 2025.2+ Reworked Terminal 可能影响 Classic API | 功能降级 | 保留工具窗口回退方案 |

### 4.3 与原方案的关键差异

| 对比维度 | 原方案（doc0 初版） | 本方案 |
|----------|---------------------|--------|
| VirtualFile | 自定义 `VirtualFileSystem` + URL 协议 | `LightVirtualFile` 子类，无需自定义文件系统 |
| Widget 类型 | `JBTerminalWidget`（缺少 shell 交互） | `ShellTerminalWidget`（支持 `executeCommand`） |
| PTY 创建 | **完全缺失** | `PtyProcessBuilder` → `PtyProcess` → `PtyProcessTtyConnector` |
| 连接流程 | 臆造的 `createTerminal()` | 已验证的 `createTerminalSession(connector)` → `start()` |
| 会话管理 | 全局 `object` 单例 `ConcurrentHashMap` | 无需独立管理，Disposable 层级自动处理 |
| accept() 判断 | URL 字符串前缀匹配 | `file is TerminalVirtualFile` 类型检查 |
| Shell 路径 | 硬编码 | 尊重 `TerminalProjectOptionsProvider` 用户设置 |
| 线程模型 | 全部在 EDT | PTY 创建在线程池，Swing 操作在 EDT |
| 命令执行时机 | 简单 `invokeLater` | `ShellTerminalWidget.executeCommand()` 内置等待机制 |

## 5. 实现步骤

### 阶段一：核心框架（1-2天）

1. 创建 `site.laomst.ccbar.terminal.editor` 包
2. 实现 `TerminalVirtualFile`（LightVirtualFile 子类）
3. 实现 `TerminalEditorProvider`（FileEditorProvider）
4. 在 `plugin.xml` 注册 `fileEditorProvider`

### 阶段二：终端编辑器实现（2-3天）

1. 实现 `TerminalFileEditor`
   - ShellTerminalWidget 创建与嵌入
   - PTY 进程创建（PtyProcessBuilder）
   - TtyConnector 连接（兼容多版本）
   - 命令执行逻辑
   - Disposable 生命周期管理
2. 实现 `TerminalEditorService`（入口服务）

### 阶段三：集成与配置（1-2天）

1. 修改 `CCBarTerminalService`，添加编辑器/工具窗口分支
2. 在 `CCBarSettings.State` 添加 `openTerminalInEditor` 配置项
3. 在设置界面添加开关

### 阶段四：测试与优化（1天）

1. 测试终端基本交互（输入、输出、颜色、滚动）
2. 测试命令执行（立即执行、延迟执行）
3. 测试编辑器 Tab 关闭时资源清理
4. 测试回退到工具窗口的场景

## 6. 参考资料

- [JediTerm 终端模拟器](https://github.com/JetBrains/jediterm) — 终端核心库
- [pty4j PTY 库](https://github.com/JetBrains/pty4j) — 伪终端进程创建
- [IntelliJ Platform Plugin SDK - Embedded Terminal](https://plugins.jetbrains.com/docs/intellij/embedded-terminal.html) — 官方终端 API 文档
- [JBTerminalWidget API 文档](https://dploeger.github.io/intellij-api-doc/com/intellij/terminal/JBTerminalWidget.html) — 非官方 API 文档
- [LocalTerminalDirectRunner 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/LocalTerminalDirectRunner.java)
- [AbstractTerminalRunner 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/AbstractTerminalRunner.java)
- [ShellTerminalWidget 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/ShellTerminalWidget.java)
- [Terminal 2025.2 变更公告](https://platform.jetbrains.com/t/terminal-implementation-changes-from-v2025-2-of-intellij-based-ides/2264/1)
- [BasicTerminalShellExample.java](https://github.com/JetBrains/jediterm/blob/master/JediTerm/src/main/java/com/jediterm/example/BasicTerminalShellExample.java) — JediTerm 官方示例
