# Story-06: 终端编辑器模式 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前 CCBar 插件所有终端都在底部 Terminal 工具窗口中打开：

```kotlin
// CCBarTerminalService.kt
private fun createTerminalAndExecute(project, command, tabName, workingDir) {
    // 通过 TerminalView/TerminalToolWindowManager 在工具窗口中创建终端
    val widget = createTerminalWidget(project, tabName, workingDir)
    executeCommandOnWidget(project, widget, command)
}
```

### 1.2 用户需求

用户希望能够选择在**编辑器区域**（以编辑器 Tab 形式）打开终端，特别适合长时间运行的 AI 编程助手（如 Claude Code）场景。编辑器模式的优势：
1. 终端与代码文件在同一个编辑器区域，可利用编辑器的分屏、Tab 管理能力
2. 减少在工具窗口和编辑器区域之间切换注意力
3. 更大的终端显示区域

---

## 2. 需求分析

### 2.1 配置层级

终端打开模式需要在以下两个层级进行配置：

| 配置层级 | 适用模式 | 说明 |
|---------|---------|------|
| ButtonConfig | 直接命令模式 | Button 直接执行命令时的终端打开模式 |
| OptionConfig | 选项列表模式 | Option 执行命令时的终端打开模式 |

### 2.2 终端打开模式

| 模式 | 值 | 说明 |
|------|-----|------|
| 工具窗口模式 | `""` (默认) | 在 Terminal 工具窗口中打开终端（原有行为） |
| 编辑器模式 | `"editor"` | 在编辑器区域以编辑器 Tab 形式打开终端 |

### 2.3 运行时切换

用户可以在 CommandPreviewDialog 中通过"在编辑器中打开"复选框临时切换终端打开模式，不影响配置的默认值。

---

## 3. 数据模型变更

### 3.1 新增常量

```kotlin
object TerminalMode {
    const val TOOL_WINDOW = ""        // 在终端工具窗口中打开（默认）
    const val EDITOR = "editor"       // 在编辑器区域中打开
}
```

### 3.2 ButtonConfig 变更

新增 `terminalMode` 字段：
```kotlin
data class ButtonConfig(
    // ... 既有字段 ...
    var terminalMode: String = ""  // 终端打开模式
)
```

### 3.3 OptionConfig 变更

新增 `terminalMode` 字段：
```kotlin
data class OptionConfig(
    // ... 既有字段 ...
    var terminalMode: String = ""  // 终端打开模式
)
```

---

## 4. UI 变更

### 4.1 设置面板 - Button 详情（直接命令模式）

在"默认终端窗口名称"字段下方新增"终端打开模式"下拉框：

```
┌───────────────────────────────────────────────┐
│ Button 详情                                   │
│ 名称:           [Claude Code              ]  │
│ 图标:           [builtin:...              ]  │
│ 直接命令:       [claude                   ]  │
│ 工作目录:       [                         ]  │
│ 默认终端窗口名称: [Claude Code             ]  │
│ 终端打开模式:   [终端工具窗口 ▼]             │
└───────────────────────────────────────────────┘
```

- 仅在直接命令模式时显示（与工作目录、终端名称同步显示/隐藏）
- 下拉选项：`终端工具窗口`、`编辑器`

### 4.2 设置面板 - Option 详情

在"默认终端窗口名称"字段下方新增"终端打开模式"下拉框：

```
┌──────────────────────────────────────────────┐
│ Option 详情                                  │
│ 名称:            [Model                   ]  │
│ 基础命令:        [claude                  ]  │
│ 工作目录:        [                        ]  │
│ 默认终端窗口名称: [Claude - Model          ]  │
│ 终端打开模式:    [终端工具窗口 ▼]            │
└──────────────────────────────────────────────┘
```

- 分割线类型的 Option 不显示此字段
- 下拉选项：`终端工具窗口`、`编辑器`

### 4.3 CommandPreviewDialog 变更

在终端标签名称行的右侧增加"在编辑器中打开"复选框：

```
┌───────────────────────────────────────────────────────────────────┐
│  终端标签名称: [Claude - Model            ] ☑ 在编辑器中打开    │
│  命令:         claude --model sonnet  [追加参数...            ]  │
│                                        [ 取消 ]  [ 执行 ]       │
└───────────────────────────────────────────────────────────────────┘
```

- 复选框默认值取决于对应配置（ButtonConfig 或 OptionConfig）的 `terminalMode` 字段
- 用户可在对话框中临时切换，不影响配置的持久值

---

## 5. 编辑器模式技术方案

基于 `doc0.tech-research.md` 调研结果，使用以下技术栈实现：

### 5.1 核心组件

| 组件 | 说明 |
|------|------|
| `TerminalVirtualFile` | `LightVirtualFile` 子类，承载终端 Tab 的虚拟文件 |
| `TerminalEditorProvider` | `FileEditorProvider` 实现，识别并处理 `TerminalVirtualFile` |
| `TerminalFileEditor` | `FileEditor` 实现，嵌入 `ShellTerminalWidget` 并管理 PTY 进程 |
| `TerminalEditorService` | 入口服务，创建虚拟文件并通过 `FileEditorManager` 打开 |

### 5.2 执行流程

```
用户点击按钮/选项
  → CommandPreviewDialog（命令预览 + 编辑器模式复选框）
  → CCBarTerminalService
      ├── [编辑器模式] TerminalEditorService.openInEditor()
      │   ├── 创建 TerminalVirtualFile
      │   ├── FileEditorManager.openFile() → 触发 TerminalEditorProvider
      │   ├── TerminalFileEditor 创建 ShellTerminalWidget
      │   ├── 线程池中启动 PTY 进程 + TtyConnector
      │   ├── EDT 中连接 widget ↔ connector 并启动
      │   └── 执行用户命令
      └── [工具窗口模式] 原有流程
```

---

## 6. 向后兼容

- 旧配置（无 `terminalMode` 字段）正常加载，默认值为 `""`（工具窗口模式）
- Kotlin data class 默认值机制确保兼容性，无需数据迁移
- 编辑器模式打开失败时自动回退到工具窗口模式

---

## 7. 验证清单

- [ ] 设置面板 Button 详情（直接命令模式）中可看到终端模式下拉框
- [ ] 设置面板 Option 详情中可看到终端模式下拉框
- [ ] 分割线类型的 Option 不显示终端模式下拉框
- [ ] CommandPreviewDialog 中可看到"在编辑器中打开"复选框
- [ ] 配置为编辑器模式的按钮/选项弹出对话框时复选框默认勾选
- [ ] 配置为工具窗口模式的按钮/选项弹出对话框时复选框默认不勾选
- [ ] 用户可在对话框中临时切换模式
- [ ] 编辑器模式：终端在编辑器 Tab 中打开，可交互
- [ ] 工具窗口模式：终端在 Terminal 工具窗口中打开（原有行为）
- [ ] 编辑器 Tab 关闭时终端进程正确清理
- [ ] 向后兼容：旧配置正常加载，默认为工具窗口模式
