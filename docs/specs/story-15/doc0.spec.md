# Story-15: 终端标签页前缀设置 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

- 终端标签页名称由 `defaultTerminalName` 字段控制，通过命令预览对话框可临时修改
- 编辑器模式下，标签页显示 `terminalName.terminal`，通过 `TerminalVirtualFile.getPresentableName()` 返回 `terminalName`
- 工具窗口模式下，通过 `TerminalToolWindowManager.createShellWidget()` 创建终端，`tabName` 参数控制标签页名称
- 终端工具窗口的标签页不支持自定义图标，只能显示文字

### 1.2 用户需求

- 由于终端工具窗口标签页不支持图标，用户希望通过"前缀"来区分不同的命令类型
- 前缀是一个简短的文本标识（如 `[CC]`、`🤖`、`▶`），显示在终端标签页名称之前
- 用户希望能够分别控制前缀是否在编辑器标签页和终端工具窗口标签页显示
- 前缀设置应该在 CommandBar（直接命令模式）和 Command 级别都可配置

## 2. 需求分析

### 2.1 布局结构

在设置面板的 CommandBar 详情区域和 Command 详情区域，新增以下字段：

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ CommandBar Details (直接命令模式)                                               │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ Name:           [NPM Test                    ] [✓] 启用                        │
│ Icon:           [Browse...                        ]                             │
│ Command:        [npm test                         ]                             │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ 终端窗口名称前缀: [                          ] [✓] 编辑器  [✓] 终端窗口      │
│                   ↑ 前缀文本输入框                ↑ 两个开关控制显示位置        │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ 默认终端窗口名称: [NPM Test                        ]                             │
│ [ ] 在编辑器中打开                                                              │
│ ...                                                                            │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│ Command Details                                                                 │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ Name:           [Model                        ] [✓] 启用                        │
│ Icon:           [Browse...                        ]                             │
│ Base Command:   [claude                           ]                             │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ 终端窗口名称前缀: [                          ] [✓] 编辑器  [✓] 终端窗口      │
│                   ↑ 前缀文本输入框                ↑ 两个开关控制显示位置        │
│ ─────────────────────────────────────────────────────────────────────────────   │
│ 默认终端窗口名称: [Claude - Model                   ]                             │
│ [ ] 在编辑器中打开                                                              │
│ ...                                                                            │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 交互行为

#### 2.2.1 前缀文本输入

- 输入框允许任意文本（包括 emoji）
- 前缀文本不做格式限制，用户可自行添加方括号、emoji 等
- 留空表示不使用前缀

#### 2.2.2 显示位置开关

| 开关 | 说明 | 默认值 |
|------|------|--------|
| 编辑器 | 控制前缀是否在编辑器标签页显示 | 启用 |
| 终端窗口 | 控制前缀是否在终端工具窗口标签页显示 | 启用 |

- 两个开关可独立控制，可同时启用/禁用
- 当两个开关都禁用时，前缀文本保留但不在任何位置显示

#### 2.2.3 标签页名称生成规则

```
最终标签页名称 = (showPrefix && 前缀不为空) ? prefix + " " + terminalName : terminalName
```

- 前缀与终端名称之间自动添加一个空格
- 示例：
  - 前缀：`[CC]`，终端名称：`Claude - Model`
  - 编辑器标签页显示：`[CC] Claude - Model`
  - 终端窗口标签页显示：`[CC] Claude - Model`

### 2.3 命令确认弹框设计

CommandPreviewDialog 需要增强，在标签页名称输入框前根据条件显示图标和前缀：

```
┌───────────────────────────────────────────────────────────────────────┐
│  命令预览与参数配置                                                    │
├───────────────────────────────────────────────────────────────────────┤
│  终端标签名称: [🤖] [CC] [Claude - Model                    ] [✓] 编辑器│
│                ↑图标   ↑前缀  ↑用户可编辑的名称部分                    │
│                                                                       │
│  环境变量:     [KEY1=val1;KEY2=val2                        ][...]     │
│  命令:         [claude --model sonnet                               ]  │
│                                                                       │
│                              [ 取消 ]  [ 执行 ]                       │
└───────────────────────────────────────────────────────────────────────┘
```

#### 2.3.1 图标和前缀的显示规则

| 打开模式 | 图标显示 | 前缀显示 |
|----------|----------|----------|
| 编辑器模式（勾选"在编辑器中打开"） | ✅ 显示图标 | 根据 `showPrefixInEditor` 决定 |
| 终端窗口模式（未勾选） | ❌ 不显示图标 | 根据 `showPrefixInTerminal` 决定 |

#### 2.3.2 动态更新行为

- 用户切换"在编辑器中打开"复选框时，图标和前缀的显示状态需要动态更新
- 图标：编辑器模式显示，终端窗口模式隐藏
- 前缀：根据对应的开关状态决定是否显示

#### 2.3.3 标签页名称输入框结构

```
[图标Label] [前缀Label] [名称输入框]
```

- 图标和前缀为只读展示，不可编辑
- 名称输入框中的值为 `defaultTerminalName`，不包含前缀
- 最终标签页名称 = 前缀（如启用）+ " " + 名称输入框值

### 2.4 视觉规范

- 前缀输入框宽度与"默认终端窗口名称"输入框一致
- 两个开关使用复选框形式，水平排列在前缀输入框右侧
- 开关标签使用简短文字："编辑器"、"终端窗口"
- 弹框中的图标大小为 16x16，与前缀文字垂直居中对齐
- 弹框中的前缀使用普通文本样式，与名称输入框对齐

## 3. 技术方案

### 3.1 数据模型修改

#### 3.1.1 CommandBarConfig 新增字段

```kotlin
data class CommandBarConfig(
    // ... 现有字段 ...
    var terminalTabPrefix: String = "",           // 终端标签页前缀文本
    var showPrefixInEditor: Boolean = true,       // 是否在编辑器标签页显示前缀
    var showPrefixInTerminal: Boolean = true,     // 是否在终端工具窗口标签页显示前缀
)
```

#### 3.1.2 CommandConfig 新增字段

```kotlin
data class CommandConfig(
    // ... 现有字段 ...
    var terminalTabPrefix: String = "",           // 终端标签页前缀文本
    var showPrefixInEditor: Boolean = true,       // 是否在编辑器标签页显示前缀
    var showPrefixInTerminal: Boolean = true,     // 是否在终端工具窗口标签页显示前缀
)
```

### 3.2 设置面板修改

#### 3.2.1 CCBarSettingsPanel 修改点

1. **CommandBar 详情区域**（直接命令模式）：
   - 在"默认终端窗口名称"字段后添加前缀设置面板
   - 包含：前缀文本输入框 + 两个复选框

2. **Command 详情区域**：
   - 在"默认终端窗口名称"字段后添加前缀设置面板
   - 包含：前缀文本输入框 + 两个复选框

3. **数据绑定**：
   - 添加对应的 UI 组件引用
   - 实现 updateXxx() 方法同步数据
   - 实现 loadCommandBarDetails() / loadCommandDetails() 加载数据到 UI

### 3.3 命令确认弹框修改

#### 3.3.1 CommandPreviewDialog 修改点

**新增构造参数：**

```kotlin
class CommandPreviewDialog(
    private val project: Project?,
    private val baseCommand: String,
    private val defaultTerminalName: String,
    private val defaultOpenInEditor: Boolean = false,
    private val defaultEnvVariables: String = "",
    // 新增参数
    private val icon: Icon? = null,                    // Command/CommandBar 的图标
    private val terminalTabPrefix: String = "",        // 前缀文本
    private val showPrefixInEditor: Boolean = true,    // 编辑器模式是否显示前缀
    private val showPrefixInTerminal: Boolean = true   // 终端窗口模式是否显示前缀
) : DialogWrapper(project)
```

**UI 结构调整：**

```kotlin
// 第一行布局：[图标] [前缀] [名称输入框] [编辑器复选框]

// 图标标签（仅编辑器模式显示）
private val iconLabel = JLabel(icon).apply {
    preferredSize = Dimension(16, 16)
}

// 前缀标签（根据开关状态显示）
private val prefixLabel = JLabel(terminalTabPrefix)

// 名称输入框（不含前缀）
private val terminalNameField = JBTextField(defaultTerminalName)

// 复选框状态变化监听
private val editorModeCheckBox = JCheckBox("在编辑器中打开").apply {
    isSelected = defaultOpenInEditor
    addItemListener { updatePrefixAndIconVisibility() }
}
```

**动态更新方法：**

```kotlin
private fun updatePrefixAndIconVisibility() {
    val openInEditor = editorModeCheckBox.isSelected

    // 图标：仅编辑器模式显示
    iconLabel.isVisible = openInEditor && icon != null

    // 前缀：根据对应开关决定
    val showPrefix = if (openInEditor) showPrefixInEditor else showPrefixInTerminal
    prefixLabel.isVisible = showPrefix && terminalTabPrefix.isNotBlank()
}
```

### 3.4 终端服务修改

#### 3.4.1 CCBarTerminalService 修改点

**传递图标和前缀参数到弹框：**

```kotlin
fun openTerminal(project: Project, command: CommandConfig, quickParam: QuickParamConfig?, commonEnvVars: String = "") {
    val baseCommand = buildCommand(command, quickParam)
    val defaultOpenInEditor = command.terminalMode == TerminalMode.EDITOR
    val mergedEnvVars = mergeEnvVariables(commonEnvVars, command.envVariables)

    // 加载图标
    val icon = IconLoader.loadIcon(command.icon)

    val dialog = CommandPreviewDialog(
        project, baseCommand, command.defaultTerminalName, defaultOpenInEditor, mergedEnvVars,
        icon = icon,
        terminalTabPrefix = command.terminalTabPrefix,
        showPrefixInEditor = command.showPrefixInEditor,
        showPrefixInTerminal = command.showPrefixInTerminal
    )
    if (!dialog.showAndGet()) {
        return
    }

    // 根据用户选择的打开模式确定是否显示前缀
    val openInEditor = dialog.openInEditor
    val showPrefix = if (openInEditor) command.showPrefixInEditor else command.showPrefixInTerminal
    val terminalName = buildTerminalName(dialog.terminalName, command.terminalTabPrefix, showPrefix)

    createTerminalAndExecute(project, finalCommand, terminalName, workingDir, openInEditor, icon)
}

private fun buildTerminalName(baseName: String, prefix: String, showPrefix: Boolean): String {
    if (prefix.isBlank() || !showPrefix) return baseName
    return "$prefix $baseName"
}
```

**修改 createTerminalAndExecute 方法签名：**

```kotlin
private fun createTerminalAndExecute(
    project: Project,
    command: String,
    tabName: String,
    workingDir: String,
    openInEditor: Boolean = false,
    icon: Icon? = null  // 新增图标参数
) {
    if (openInEditor) {
        try {
            TerminalEditorService.openInEditor(project, command, tabName, workingDir, icon)
            return
        } catch (e: Exception) {
            LOG.warn("CCBar: 编辑器终端打开失败，回退到工具窗口", e)
        }
    }
    // ... 终端工具窗口模式 ...
}
```

### 3.5 编辑器标签页图标支持

#### 3.5.1 TerminalVirtualFile 修改

```kotlin
class TerminalVirtualFile(
    val terminalName: String,
    val workingDirectory: String?,
    val command: String?,
    val icon: Icon? = null  // 新增图标参数
) : LightVirtualFile("$terminalName.terminal", TerminalFileType.INSTANCE, "") {

    init {
        isWritable = false
    }

    override fun isValid(): Boolean = true

    override fun getPresentableName(): String = terminalName
}
```

#### 3.5.2 TerminalEditorService 修改

```kotlin
fun openInEditor(
    project: Project,
    command: String,
    terminalName: String,
    workingDir: String?,
    icon: Icon? = null  // 新增图标参数
) {
    ApplicationManager.getApplication().invokeLater {
        try {
            val virtualFile = TerminalVirtualFile(
                terminalName = terminalName,
                workingDirectory = workingDir,
                command = command,
                icon = icon
            )
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        } catch (e: Exception) {
            LOG.error("CCBar: 在编辑器中打开终端失败", e)
        }
    }
}
```

#### 3.5.3 编辑器标签页图标渲染

IntelliJ Platform 的编辑器标签页图标由 `FileEditor` 的 `getFile()` 方法返回的 `VirtualFile` 决定。通过重写 `TerminalFileType` 的 `getIcon()` 方法来提供图标：

```kotlin
class TerminalFileType private constructor() : FileType {
    // ...

    override fun getIcon(): Icon? = AllIcons.Actions.Execute  // 默认图标

    companion object {
        val INSTANCE = TerminalFileType()
    }
}
```

但这种方式只能提供静态图标。要实现每个终端标签页显示不同的图标，需要：

**方案：通过 VirtualFile 的 getUserData 机制**

```kotlin
// TerminalVirtualFile.kt
class TerminalVirtualFile(...) : LightVirtualFile(...) {
    companion object {
        val TERMINAL_ICON_KEY = Key.create<Icon>("ccbar.terminal.icon")
    }

    fun getTabIcon(): Icon? = icon ?: super.getIcon()
}

// TerminalFileEditor.kt
class TerminalFileEditor(...) : FileEditor {
    // ...

    // FileEditorManager 会查询此方法获取标签页图标
    // 但标准做法是通过 FileType 或 VirtualFile.getIcon()
}
```

**最终方案：使用 EditorTabTitleProvider 扩展点**

由于编辑器标签页图标通常由 FileType 决定，而我们需要每个标签页不同的图标，可以通过在标签页名称前添加 emoji 作为替代方案，或接受统一图标。

考虑到复杂度，建议：
1. **编辑器标签页图标**：使用 FileType 统一图标（或使用 emoji 前缀作为视觉区分）
2. **命令确认弹框**：显示图标预览（让用户知道使用的是哪个命令）

### 3.6 状态管理

- 前缀设置作为配置的一部分，随配置自动持久化
- 无需额外的状态管理

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarSettings.kt` | CommandBarConfig、CommandConfig 新增三个字段（terminalTabPrefix、showPrefixInEditor、showPrefixInTerminal） |
| `CCBarSettingsPanel.kt` | 添加前缀输入框和两个复选框的 UI 组件及数据绑定 |
| `CommandPreviewDialog.kt` | 新增图标和前缀相关参数，修改 UI 布局，实现动态显示/隐藏 |
| `CCBarTerminalService.kt` | 修改 openTerminal 和 openTerminalForCommandBar 方法，传递图标和前缀参数，构建带前缀的终端名称 |
| `TerminalEditorService.kt` | openInEditor 方法新增图标参数 |
| `TerminalVirtualFile.kt` | 新增图标字段 |
| `spec.md` | 更新数据结构章节 |

### 4.2 不受影响的部分

- QuickParam 配置（不涉及）
- 弹出菜单组件（不涉及）
- 终端工具窗口创建逻辑（仅修改名称参数）

## 5. 验收标准

### 5.1 功能验收

- [ ] CommandBar 直接命令模式下，可设置终端标签页前缀
- [ ] Command 列表模式下，每个 Command 可设置终端标签页前缀
- [ ] 前缀文本可包含 emoji 和特殊字符
- [ ] 编辑器开关可独立控制前缀在编辑器标签页的显示
- [ ] 终端窗口开关可独立控制前缀在终端工具窗口标签页的显示
- [ ] 配置保存后重启 IDE，前缀设置正确保留
- [ ] 导入/导出配置时，前缀设置正确序列化

### 5.2 命令确认弹框验收

- [ ] 弹框中根据打开模式显示/隐藏图标（编辑器模式显示，终端窗口模式隐藏）
- [ ] 弹框中根据前缀开关状态显示/隐藏前缀
- [ ] 切换"在编辑器中打开"复选框时，图标和前缀动态更新
- [ ] 图标、前缀、名称输入框布局整齐对齐

### 5.3 UI 验收

- [ ] 前缀设置面板布局整齐，与现有字段风格一致
- [ ] 复选框标签文字清晰易懂
- [ ] 输入框 placeholder 提示合理

### 5.4 兼容性验收

- [ ] 旧版本配置文件（无前缀字段）可正常加载，使用默认值
- [ ] 前缀留空时，终端标签页名称与之前行为一致

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 前缀过长导致标签页名称显示不完整 | 低 | 用户可自行调整，IDE 会自动截断显示 |
| 旧版本配置兼容性 | 低 | 新字段有默认值，旧配置加载时自动填充 |

## 7. 后续优化建议

- 可考虑提供前缀模板（如常用 emoji 快速选择）
- 可考虑在预览对话框中实时预览带前缀的标签页名称
