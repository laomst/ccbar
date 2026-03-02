# CCBar 编辑器终端中文输入法卡顿问题分析

> 调研日期：2026-03-02
> 问题描述：通过 CCBar 直接在编辑器中打开的终端标签页，在使用 Claude Code CLI 等交互式终端时，中文输入法有明显卡顿
> 对比参照：IDE 原生终端"移动到编辑器"功能无此问题

## 1. 问题背景

CCBar 插件支持将终端直接在编辑器区域打开（以 Tab 形式呈现）。用户反馈在使用交互式 CLI（如 Claude Code）时，中文输入法出现卡顿现象，而通过 IDE 原生终端"移动到编辑器"功能则无此问题。

## 2. 两种终端打开方式对比

### 2.1 CCBar 直接编辑器模式

**实现路径**：
```
用户点击按钮 → CommandPreviewDialog → TerminalEditorService.openInEditor()
    → 创建 TerminalVirtualFile
    → FileEditorManager.openFile()
    → TerminalEditorProvider.createEditor()
    → TerminalFileEditor
        → 创建全新的 ShellTerminalWidget
        → 创建全新的 PTY 进程
        → JPanel 包装 terminalWidget.component
        → 启动终端会话
```

**关键代码** (`TerminalFileEditor.kt`)：
```kotlin
class TerminalFileEditor(
    private val project: Project,
    private val terminalFile: TerminalVirtualFile
) : FileEditor {

    private val runner: LocalTerminalDirectRunner = LocalTerminalDirectRunner.createTerminalRunner(project)

    private val terminalWidget: ShellTerminalWidget = ShellTerminalWidget(
        project,
        runner.settingsProvider,
        editorDisposable
    )

    // 注意：这里有一个 JPanel 包装层
    private val mainPanel: JPanel = JPanel(BorderLayout()).apply {
        add(terminalWidget.component, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = terminalWidget.component
}
```

### 2.2 IDE 原生"移动到编辑器"

**实现路径**：
```
终端工具窗口中已有 ShellTerminalWidget
    → 右键菜单"Move to Editor"
    → 复用现有 Widget 实例
    → 迁移父容器到编辑器区域
    → 保持 PTY 会话和终端状态
```

### 2.3 核心差异对比

| 对比项 | CCBar 直接编辑器模式 | IDE 原生"移动到编辑器" |
|--------|---------------------|----------------------|
| **Widget 实例** | 创建全新的 `ShellTerminalWidget` | 复用工具窗口中已有 Widget |
| **PTY 进程** | 启动新的 PTY 进程 | 保持原有 PTY 会话 |
| **会话状态** | 全新会话，无历史 | 保留完整终端历史 |
| **父容器** | 自定义 `JPanel(BorderLayout)` | IDE 原生容器迁移 |
| **生命周期** | 独立 `Disposable`，随编辑器关闭销毁 | 与工具窗口终端共享生命周期 |
| **组件层次** | `FileEditorComponent` → `JPanel` → `terminalWidget.component` | 直接迁移，无额外包装 |
| **IME 状态** | 全新初始化 | 保持已有状态 |

## 3. 中文输入法卡顿原因分析

### 3.1 组件层次问题

CCBar 的实现中，`terminalWidget.component` 被包装在额外的 `JPanel` 中：

```
组件层次：
FileEditorComponent
  └── mainPanel (JPanel)          ← 额外的包装层
        └── terminalWidget.component
              └── JediTerm 内部组件
                    └── 终端文本区域（接收 IME 输入）
```

这个额外的包装层可能导致：

1. **IME 事件传播链变长**
   - `InputMethodEvent` 需要经过 `mainPanel` → `terminalWidget.component` → 内部终端组件
   - 每一层容器都可能引入事件处理延迟

2. **焦点管理混乱**
   - `getPreferredFocusedComponent()` 返回 `terminalWidget.component`
   - 但实际获得焦点的可能是 `mainPanel`
   - 焦点切换时 IME 上下文可能丢失

3. **enableInputMethods() 未显式调用**
   - `JPanel` 默认不处理 `InputMethodEvent`
   - 如果未显式启用输入法支持，事件可能被吞掉

### 3.2 输入法框架交互

Java AWT 输入法框架的工作流程：

```
用户输入 → 操作系统 IME → AWT InputMethodEvent
    → 组件层次传播 → 目标组件的 inputMethodTextChanged()
    → IME 组合文本显示 → 确认后发送 keyTyped/keyPressed
```

问题可能出现在：

1. **事件传播中断**：`JPanel` 未正确转发 `InputMethodEvent`
2. **IME 上下文切换**：焦点变化时 IME 上下文重建
3. **组合文本同步**：终端组件与 IME 组合窗口位置不同步

### 3.3 JediTerm 终端组件特性

JediTerm（IntelliJ 终端的底层库）的终端文本区域：

- 使用自定义绘制，非标准 Swing 文本组件
- 需要特殊处理 IME 组合文本
- 依赖正确的 `InputMethodRequests` 实现

如果外层容器干扰了 `InputMethodRequests` 的获取，会导致 IME 行为异常。

## 4. 解决方案

### 4.1 方案 A：显式启用输入法支持（推荐首先尝试）

**改动最小**，在 `mainPanel` 上显式启用输入法：

```kotlin
private val mainPanel: JPanel = JPanel(BorderLayout()).apply {
    add(terminalWidget.component, BorderLayout.CENTER)
    // 显式启用输入法支持
    enableInputMethods(true)
}
```

**优点**：
- 改动最小，风险低
- 可能直接解决问题

**缺点**：
- 如果问题不在 `enableInputMethods`，可能无效

### 4.2 方案 B：简化组件层次

移除 `JPanel` 包装，直接使用 `terminalWidget.component`：

```kotlin
// 移除 mainPanel
override fun getComponent(): JComponent = terminalWidget.component
override fun getPreferredFocusedComponent(): JComponent = terminalWidget.component
```

**优点**：
- 减少事件传播层次
- 与 IDE 原生实现更接近

**缺点**：
- 如果将来需要添加工具栏等组件，需要重新包装
- 需要确保 `terminalWidget.component` 能正确作为 `FileEditor` 的根组件

### 4.3 方案 C：确保正确的焦点和 IME 上下文

在终端启动后显式请求焦点并启用输入法：

```kotlin
private fun startTerminalSession() {
    // ... 现有代码 ...
    ApplicationManager.getApplication().invokeLater {
        if (Disposer.isDisposed(editorDisposable)) {
            process.destroy()
            return@invokeLater
        }

        try {
            terminalWidget.createTerminalSession(connector)
            terminalWidget.start()
            sessionStarted = true

            // 新增：确保焦点和 IME 上下文
            terminalWidget.component.requestFocusInWindow()
            terminalWidget.component.enableInputMethods(true)

            // ... 命令执行代码 ...
        } catch (e: Exception) {
            // ...
        }
    }
}
```

### 4.4 方案 D：复用 IDE 原生"移动到编辑器"机制

**最彻底的解决方案**，让 CCBar 先在工具窗口创建终端，然后调用 IDE 原生的"移动到编辑器"功能：

```kotlin
fun openInEditorViaToolWindow(project: Project, command: String, terminalName: String, workingDir: String) {
    // 1. 先在工具窗口创建终端
    val manager = TerminalToolWindowManager.getInstance(project)
    val widget = manager.createShellWidget(workingDir, terminalName, true, true)

    // 2. 执行命令
    widget.sendCommandToExecute(command)

    // 3. 调用 IDE 原生的"移动到编辑器"功能
    // 需要研究 TerminalSessionVirtualFile 或相关 API
    moveTerminalToEditor(widget)
}
```

**优点**：
- 终端创建流程与 IDE 原生完全一致
- IME 处理逻辑与原生一致
- 避免 DIY 组件包装带来的问题

**缺点**：
- 需要研究 IDE 内部 API（可能不稳定）
- 实现复杂度较高

### 4.5 方案对比

| 方案 | 改动量 | 风险 | 彻底性 | 推荐顺序 |
|------|--------|------|--------|----------|
| A. enableInputMethods | 极小 | 低 | 可能不彻底 | 1 |
| B. 简化组件层次 | 小 | 中 | 较彻底 | 2 |
| C. 显式焦点+IME | 小 | 低 | 可能不彻底 | 3 |
| D. 复用原生机制 | 大 | 中 | 最彻底 | 4 |

## 5. 实施建议

### 5.1 验证步骤

1. **复现问题**：在开发环境中确认中文输入法卡顿现象
2. **对比测试**：同时测试 IDE 原生"移动到编辑器"功能，确认其无卡顿
3. **日志分析**：添加 AWT 事件日志，观察 `InputMethodEvent` 传播情况

### 5.2 推荐实施顺序

1. **第一步**：尝试方案 A（enableInputMethods）
   - 如果有效，问题解决
   - 如果无效，进入下一步

2. **第二步**：尝试方案 B（简化组件层次）
   - 如果有效，问题解决
   - 如果无效，进入下一步

3. **第三步**：尝试方案 C（显式焦点+IME）
   - 可以与方案 A/B 结合使用

4. **第四步**：如果以上都无效，考虑方案 D（复用原生机制）
   - 需要深入研究 IDE 源码
   - 可能需要反射调用内部 API

## 6. 相关代码位置

| 文件 | 说明 |
|------|------|
| `src/main/kotlin/com/github/ccbar/terminal/editor/TerminalFileEditor.kt` | 终端编辑器核心实现 |
| `src/main/kotlin/com/github/ccbar/terminal/editor/TerminalEditorProvider.kt` | 编辑器提供者 |
| `src/main/kotlin/com/github/ccbar/terminal/editor/TerminalEditorService.kt` | 入口服务 |
| `src/main/kotlin/com/github/ccbar/terminal/editor/TerminalVirtualFile.kt` | 虚拟文件 |
| `docs/specs/story-06/doc0.tech-research.md` | 编辑器终端技术调研 |
| `docs/specs/story-08/doc0.tech-research.md` | 自定义 Shell 路径技术调研 |

## 7. 参考资料

- [Java AWT Input Method Framework](https://docs.oracle.com/javase/8/docs/technotes/guides/imf/)
- [JediTerm 终端模拟器](https://github.com/JetBrains/jediterm)
- [IntelliJ Platform Terminal API](https://plugins.jetbrains.com/docs/intellij/embedded-terminal.html)
