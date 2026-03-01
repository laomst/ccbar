# CCBar 自定义 Shell 路径技术调研报告

> 调研日期：2026-03-01
> 调研目标：研究如何在 CCBar 打开终端时支持自定义 Shell（如 bash/zsh/fish），同时保留 Shell Integration 能力
> 目标平台：IntelliJ IDEA 2024.2+（sinceBuild = "242"）
> 调研方法：反编译 `terminal.jar`（来自 IntelliJ IDEA CE 2024.2 Gradle 缓存）

## 1. 当前实现分析

### 1.1 两种终端打开方式

CCBar 插件有两种终端打开方式，Shell 来源均为 IDE 全局设置，不可自定义：

| 打开方式 | 实现位置 | Shell 来源 |
|---------|---------|-----------|
| 编辑器模式 | `TerminalFileEditor.kt` | `LocalTerminalDirectRunner` → IDE 终端设置 |
| 工具窗口模式 | `CCBarTerminalService.kt` | 反射调用 `createLocalShellWidget()` → IDE 终端设置 |

### 1.2 编辑器模式当前代码

```kotlin
// TerminalFileEditor.kt
val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
val terminalWidget = ShellTerminalWidget(project, runner.settingsProvider, editorDisposable)
runner.openSessionInDirectory(terminalWidget, workingDir)
```

`openSessionInDirectory(widget, workingDir)` 内部构造 `ShellStartupOptions` 时只传入 `workingDirectory`，Shell 路径从 `TerminalProjectOptionsProvider.getInstance(project).getShellPath()` 获取（即 IDE 全局设置 `Settings > Tools > Terminal > Shell path`）。

### 1.3 工具窗口模式当前代码

```kotlin
// CCBarTerminalService.kt — 反射调用
// 策略1: TerminalView.createLocalShellWidget(workingDir, tabName, requestFocus)
// 策略2: TerminalToolWindowManager.createLocalShellWidget(workingDir, tabName)
```

两个 API 只接受 `workingDir` 和 `tabName`，无 Shell 路径参数。

## 2. ShellStartupOptions 类分析（已反编译验证）

**包名**：`org.jetbrains.plugins.terminal`
**类型**：普通 `final class`（非 data class），私有构造函数 + Builder 模式

### 2.1 字段定义

| 字段 | 类型 | 说明 |
|------|------|------|
| `workingDirectory` | `String?` | 工作目录 |
| `shellCommand` | `List<String>?` | **Shell 命令**（如 `["/bin/zsh", "-l"]`） |
| `commandHistoryFileProvider` | `() -> Path?` | 命令历史文件提供者 |
| `initialTermSize` | `TermSize?` | 初始终端尺寸 |
| `widget` | `TerminalWidget?` | 关联的终端 widget |
| `shellIntegration` | `ShellIntegration?` | Shell Integration 信息 |
| `envVariables` | `Map<String, String>?` | 环境变量 |

### 2.2 Builder 类

Builder 提供链式设置方法，所有字段均可设置：

```kotlin
ShellStartupOptions.Builder()
    .workingDirectory("/path/to/dir")
    .shellCommand(listOf("/usr/local/bin/zsh", "-l"))
    .envVariables(mapOf("MY_VAR" to "value"))
    .initialTermSize(TermSize(120, 24))
    .build()
```

实例方法 `builder()` 返回基于当前值的新 Builder（copy-modify 模式）：

```kotlin
val modified = existingOptions.builder()
    .shellCommand(listOf("/custom/shell"))
    .build()
```

### 2.3 顶层工厂函数（`ShellStartupOptionsKt`）

```kotlin
// 简单工厂 — 仅指定工作目录
fun shellStartupOptions(workingDirectory: String?): ShellStartupOptions

// DSL 风格 — 通过 lambda 配置 Builder
fun shellStartupOptions(
    workingDirectory: String? = null,
    block: (ShellStartupOptions.Builder) -> Unit
): ShellStartupOptions
```

## 3. LocalTerminalDirectRunner 关键方法分析（已反编译验证）

**包名**：`org.jetbrains.plugins.terminal`
**继承**：`AbstractTerminalRunner<PtyProcess>`
**可子类化**：是（`public class`，非 `final`）

### 3.1 configureStartupOptions() — 核心配置方法

此方法将初始 options 转换为完全配置的 options，执行顺序：

```
configureStartupOptions(options)
  ① 解析工作目录        → getWorkingDirectory(options.workingDirectory)
  ② 构建环境变量        → getTerminalEnvironment(options.envVariables, workingDir)
  ③ 获取 Shell 命令    → doGetInitialCommand(options, env)
  ④ 注入 Shell Integration  → injectShellIntegration(options)  [如果启用]
  ⑤ 应用 LocalTerminalCustomizer 扩展点  → applyTerminalCustomizers(options)
```

反编译还原的伪代码：

```java
public ShellStartupOptions configureStartupOptions(ShellStartupOptions options) {
    String workingDir = getWorkingDirectory(options.getWorkingDirectory());
    Map<String, String> env = getTerminalEnvironment(options.getEnvVariables(), workingDir);
    List<String> command = doGetInitialCommand(options, env);

    TerminalWidget widget = options.getWidget();
    if (widget != null) {
        widget.setShellCommand(command);
    }

    ShellStartupOptions configured = options.builder()
        .shellCommand(command)
        .workingDirectory(workingDir)
        .envVariables(env)
        .build();

    if (enableShellIntegration()) {
        configured = injectShellIntegration(configured);
    }

    configured = applyTerminalCustomizers(configured);
    return configured;
}
```

### 3.2 getInitialCommand() — Shell 命令解析逻辑

**关键发现**：如果 `options.shellCommand` 已设置，则直接使用，不再从 IDE 设置读取。

```java
public List<String> getInitialCommand(Map<String, String> env) {
    ShellStartupOptions options = myStartupOptionsThreadLocal.get();
    List<String> shellCommand = (options != null) ? options.getShellCommand() : null;
    // 优先使用 options 中的 shellCommand，否则从 IDE 设置取
    return (shellCommand != null) ? shellCommand : convertShellPathToCommand(getShellPath());
}
```

`getShellPath()` 内部调用：

```java
private String getShellPath() {
    return TerminalProjectOptionsProvider.getInstance(myProject).getShellPath();
}
```

### 3.3 createProcess() — 进程创建

内部使用 `PtyProcessBuilder`，命令来自 `options.shellCommand`：

```java
public PtyProcess createProcess(ShellStartupOptions options) throws ExecutionException {
    String[] command = ArrayUtil.toStringArray(options.getShellCommand());
    Map<String, String> env = options.getEnvVariables();
    TermSize termSize = options.getInitialTermSize();
    String workingDir = options.getWorkingDirectory();

    PtyProcessBuilder builder = new PtyProcessBuilder(command)
        .setEnvironment(env)
        .setDirectory(workingDir)
        .setInitialColumns(termSize != null ? termSize.getColumns() : null)
        .setInitialRows(termSize != null ? termSize.getRows() : null)
        .setUseWinConPty(LocalPtyOptions.shouldUseWinConPty());

    return builder.start();
}
```

### 3.4 可覆写方法列表

| 方法 | 可见性 | 可覆写 |
|------|--------|--------|
| `configureStartupOptions(ShellStartupOptions)` | `public` | 是 |
| `createProcess(ShellStartupOptions)` | `public` | 是 |
| `createTtyConnector(PtyProcess)` | `public` | 是 |
| `getInitialCommand(Map)` | `public` | 是 |
| `enableShellIntegration()` | `protected` | 是 |
| `isBlockTerminalEnabled()` | `protected` | 是 |
| `getDefaultTabTitle()` | `public` | 是 |

## 4. AbstractTerminalRunner 关键方法分析

### 4.1 openSessionInDirectory(JBTerminalWidget, String) — 当前使用的方法

```java
public void openSessionInDirectory(JBTerminalWidget widget, String directory) {
    openSessionInDirectory(
        widget.asNewWidget(),
        getStartupOptions(directory)   // 仅包含 workingDirectory
    );
}
```

这是一个便捷方法，内部调用带 `ShellStartupOptions` 参数的版本，但 options 中只有 `workingDirectory`。

### 4.2 openSessionInDirectory(TerminalWidget, ShellStartupOptions) — 带完整 options 的版本

私有方法，在线程池中执行：

```
① awaitTermSize(widget)          — 等待终端尺寸确定
② configureStartupOptions(options) — 完整配置（含 Shell Integration 注入）
③ createProcess(configuredOptions)  — 创建 PTY 进程
④ createTtyConnector(process)      — 创建 TTY 连接器
⑤ [EDT] widget.connectTerminalSession(connector) — 连接并启动
```

### 4.3 startShellTerminalWidget() — 公开的带 options 入口

```java
public TerminalWidget startShellTerminalWidget(
    Disposable parent,
    ShellStartupOptions options,
    boolean deferSessionStart
)
```

此方法接受完整的 `ShellStartupOptions`，可以传入自定义 `shellCommand`。返回新式 `TerminalWidget`。

## 5. LocalTerminalCustomizer 扩展点

**包名**：`org.jetbrains.plugins.terminal`
**扩展点 ID**：`org.jetbrains.plugins.terminal.localTerminalCustomizer`

### 5.1 类定义

```java
public abstract class LocalTerminalCustomizer {
    // 可修改命令和环境变量，workingDirectory 只读
    public String[] customizeCommandAndEnvironment(
        Project project,
        String workingDirectory,
        String[] command,           // 返回修改后的命令数组
        Map<String, String> envs    // 直接修改此 Map
    );
}
```

### 5.2 调用时机

在 `configureStartupOptions()` 的第 ⑤ 步，**Shell Integration 注入之后**调用。此时 `command` 数组中已包含 Shell Integration 相关的参数。

### 5.3 局限性

- 全局生效，作用于所有终端会话，不仅限于 CCBar
- 没有 per-session 上下文传入
- 不适合用于 CCBar 的按 Option 级别自定义 Shell

## 6. 方案评估

### 6.1 方案对比

| 方案 | 自定义 Shell | Shell Integration | 编辑器模式 | 工具窗口模式 | 复杂度 |
|------|-------------|-------------------|-----------|-------------|--------|
| A. ShellStartupOptions.shellCommand + 手动拆解流程 | ✅ | ✅ 自动注入 | ✅ | ❌ | 中 |
| B. startShellTerminalWidget(options) | ✅ | ✅ 自动注入 | ✅ | ⚠️ 需验证 | 中 |
| C. LocalTerminalCustomizer 扩展点 | ✅ | ✅ | ✅ | ✅ | 低 |
| D. 直接 PtyProcessBuilder | ✅ | ❌ 丢失 | ✅ | ❌ | 低 |

### 6.2 推荐方案：A — 手动拆解 Runner 流程（编辑器模式）

**原理**：使用 `LocalTerminalDirectRunner` 的公开方法，手动走完终端启动流程，在构造 `ShellStartupOptions` 时指定 `shellCommand`。

**改造前（当前代码）**：

```kotlin
// TerminalFileEditor.kt — 无法指定 Shell
val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
runner.openSessionInDirectory(terminalWidget, workingDir)
```

**改造后**：

```kotlin
val runner = LocalTerminalDirectRunner.createTerminalRunner(project)

// 1. 构造带自定义 shellCommand 的 options
val options = ShellStartupOptions.Builder()
    .workingDirectory(workingDir)
    .shellCommand(listOf("/usr/local/bin/zsh", "-l"))  // 自定义 Shell
    .build()

// 2. configureStartupOptions 会自动注入 Shell Integration
val configured = runner.configureStartupOptions(options)

// 3. 创建 PTY 进程（使用配置好的命令，含 Shell Integration）
val process = runner.createProcess(configured)

// 4. 创建 TtyConnector
val connector = runner.createTtyConnector(process)

// 5. 连接 widget 并启动
terminalWidget.createTerminalSession(connector)
terminalWidget.start()
```

**优势**：
- `shellCommand` 在 `getInitialCommand()` 中被优先使用，不走 IDE 设置
- `configureStartupOptions()` 会根据 `shellCommand[0]` 的 Shell 类型自动注入对应的 Shell Integration 脚本
- `applyTerminalCustomizers()` 仍然会执行，保持与第三方插件的兼容
- 所有环境变量配置正常工作

**注意事项**：
- `configureStartupOptions()` 和 `createProcess()` 可能阻塞，需要在线程池中执行
- 需要在 EDT 中调用 `createTerminalSession()` + `start()`
- 需要处理 `Disposable` 已销毁的竞态条件

### 6.3 工具窗口模式的可能方案

#### 方案 B 扩展：使用 startShellTerminalWidget()

`AbstractTerminalRunner.startShellTerminalWidget(Disposable, ShellStartupOptions, boolean)` 是公开方法，可以传入带 `shellCommand` 的 options：

```kotlin
val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
val options = ShellStartupOptions.Builder()
    .workingDirectory(workDir)
    .shellCommand(listOf("/usr/local/bin/zsh"))
    .build()
val widget = runner.startShellTerminalWidget(parentDisposable, options, false)
```

**待验证问题**：
- 返回的 `TerminalWidget` 是否能自动出现在终端工具窗口中？
- 还是需要手动注册到 `TerminalView` / `TerminalToolWindowManager`？
- 需要在 2024.2 实际环境中测试确认

#### 方案 C 备选：LocalTerminalCustomizer

如果不需要 per-Option 级别的 Shell 自定义，可以通过 `LocalTerminalCustomizer` 全局替换 Shell。但此方案会影响所有终端会话。

## 7. 关键结论

1. **`ShellStartupOptions.shellCommand` 是自定义 Shell 的正确入口**：设置后会被 `getInitialCommand()` 优先采用
2. **Shell Integration 会自动注入**：`configureStartupOptions()` 根据 `shellCommand[0]` 判断 Shell 类型并注入对应脚本
3. **编辑器模式完全可行**：手动拆解 Runner 流程即可，无需子类化或反射
4. **工具窗口模式需验证**：`startShellTerminalWidget()` API 存在但需确认 widget 能否正确展示在工具窗口中
5. **不推荐 PtyProcessBuilder 裸启动**：会丢失 Shell Integration，`executeCommand()` 等高级功能将不可用
6. **不推荐 LocalTerminalCustomizer**：全局生效，无法 per-session 自定义

## 8. 参考资料

- 反编译来源：`~/.gradle/caches/8.13/transforms/.../ideaIC-2024.2-aarch64/plugins/terminal/lib/terminal.jar`
- [LocalTerminalDirectRunner 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/LocalTerminalDirectRunner.java)
- [AbstractTerminalRunner 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/AbstractTerminalRunner.java)
- [ShellTerminalWidget 源码](https://github.com/JetBrains/intellij-community/blob/master/plugins/terminal/src/org/jetbrains/plugins/terminal/ShellTerminalWidget.java)
- [pty4j 库](https://github.com/JetBrains/pty4j)
- [JediTerm 终端模拟器](https://github.com/JetBrains/jediterm)
