# Story-02: Button 直接命令模式 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前 Button 是**纯容器**设计，不绑定任何命令：

```
Button（工具栏按钮，纯容器）
  └── Option（绑定 baseCommand + workingDirectory + defaultTerminalName）
        └── SubButton（绑定 params）
```

**点击 Button 行为**：弹出 Option 列表，用户选择后执行命令。

**配置结构**：
```kotlin
data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    var options: MutableList<OptionConfig> = mutableListOf()
)
```

### 1.2 用户需求

用户希望 Button 可以直接绑定一个命令，点击后直接执行，而不弹出选项列表：

- **简化操作**：对于只需要执行单个命令的场景，无需创建 Option 层级
- **快速访问**：一键执行常用命令
- **灵活切换**：Button 可以在"直接命令模式"和"选项列表模式"之间切换

---

## 2. 需求分析

### 2.1 数据模型变更

**ButtonConfig 新增字段**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `command` | String | "" | 直接命令，为空则使用 options 模式 |
| `workingDirectory` | String | "" | 工作目录，留空使用项目根目录 |
| `defaultTerminalName` | String | "" | 直接命令模式的默认终端名称 |

**配置结构**：
```kotlin
data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    // 新增字段
    var command: String = "",           // 直接命令
    var workingDirectory: String = "",  // 工作目录
    var defaultTerminalName: String = "", // 终端名称
    var options: MutableList<OptionConfig> = mutableListOf()
)
```

### 2.2 执行逻辑

```
点击 Button:
  ├─ command 不为空 → 直接执行命令 + 命名弹窗
  └─ command 为空 → 弹出 Options 列表（原有逻辑）
```

**命令执行流程（直接命令模式）**：
1. 点击 Button
2. 弹出终端命名对话框（默认名称来自 `ButtonConfig.defaultTerminalName`）
3. 用户确认名称
4. 创建终端并执行 `ButtonConfig.command`
5. 工作目录：优先使用 `ButtonConfig.workingDirectory`，否则使用项目根目录

### 2.3 按钮状态

```
启用条件: command 不为空 OR options 不为空
禁用条件: command 为空 AND options 为空
```

**CCBarButtonAction.update() 逻辑变更**：
```kotlin
override fun update(e: AnActionEvent) {
    e.presentation.text = buttonConfig.name
    e.presentation.icon = CCBarIcons.loadIcon(buttonConfig.icon, e.project)
    // 修改启用条件
    e.presentation.isEnabled = buttonConfig.command.isNotBlank() || buttonConfig.options.isNotEmpty()
}
```

### 2.4 设置界面变更

#### 2.4.1 Button 详情面板新增字段

在 Button 详情区域新增三个字段：
- **直接命令**：文本输入框
- **工作目录**：目录选择器（复用 Option 的逻辑）
- **终端名称**：文本输入框

#### 2.4.2 Options 区域显示逻辑

当 `command` 不为空时：
- **隐藏** Options 配置区域（列表 + 详情 + SubButton 表格）
- 显示提示信息："直接命令模式下，Options 配置不可用"

当 `command` 为空时：
- **显示** Options 配置区域（原有行为）

### 2.5 配置示例

**直接命令模式 Button**：
```json
{
  "id": "quick-npm",
  "name": "NPM Test",
  "icon": "builtin:AllIcons.Actions.Execute",
  "command": "npm test",
  "workingDirectory": "",
  "defaultTerminalName": "NPM Test",
  "options": []
}
```

**选项列表模式 Button（原有行为）**：
```json
{
  "id": "claude-code",
  "name": "Claude Code",
  "icon": "builtin:AllIcons.Actions.Execute",
  "command": "",
  "workingDirectory": "",
  "defaultTerminalName": "",
  "options": [
    {
      "id": "model",
      "name": "Model",
      "baseCommand": "claude",
      "workingDirectory": "",
      "defaultTerminalName": "Claude - Model",
      "subButtons": [...]
    }
  ]
}
```

---

## 3. 技术方案

### 3.1 数据模型修改

**文件**：`CCBarSettings.kt`

```kotlin
data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    // 新增字段
    var command: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var options: MutableList<OptionConfig> = mutableListOf()
) {
    fun deepCopy(): ButtonConfig = ButtonConfig(
        id = id,
        name = name,
        icon = icon,
        command = command,
        workingDirectory = workingDirectory,
        defaultTerminalName = defaultTerminalName,
        options = options.map { it.deepCopy() }.toMutableList()
    )

    // 新增：判断是否为直接命令模式
    fun isDirectCommandMode(): Boolean = command.isNotBlank()
}
```

### 3.2 按钮点击逻辑修改

**文件**：`CCBarButtonAction.kt`

```kotlin
override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    if (buttonConfig.isDirectCommandMode()) {
        // 直接命令模式：执行命令
        CCBarTerminalService.openTerminalForButton(project, buttonConfig)
    } else {
        // 选项列表模式：弹出菜单
        val component = e.inputEvent?.component ?: return
        val popup = CCBarPopupBuilder.buildPopup(project, buttonConfig)
        popup.showUnderneathOf(component)
    }
}

override fun update(e: AnActionEvent) {
    e.presentation.text = buttonConfig.name
    e.presentation.icon = CCBarIcons.loadIcon(buttonConfig.icon, e.project)
    // 修改启用条件
    e.presentation.isEnabled = buttonConfig.command.isNotBlank() || buttonConfig.options.isNotEmpty()
}
```

### 3.3 终端服务新增方法

**文件**：`CCBarTerminalService.kt`

```kotlin
/**
 * 为 Button 直接命令模式打开终端
 */
fun openTerminalForButton(project: Project, button: ButtonConfig) {
    val terminalName = showNameDialogForButton(project, button) ?: return
    val command = button.command
    val workingDir = resolveWorkingDirectoryForButton(project, button)
    createTerminalAndExecute(project, command, terminalName, workingDir)
}

private fun showNameDialogForButton(project: Project, button: ButtonConfig): String? {
    return Messages.showInputDialog(
        project,
        "请输入终端标签名称：",
        "终端命名",
        null,
        button.defaultTerminalName.ifBlank { button.name },
        null
    )
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
```

### 3.4 设置界面修改

**文件**：`CCBarSettingsPanel.kt`

#### 3.4.1 新增 Button 详情字段

```kotlin
// Button 详情字段（新增）
private lateinit var buttonCommandField: JBTextField
private lateinit var buttonWorkingDirectoryField: TextFieldWithBrowseButton
private lateinit var buttonTerminalNameField: JBTextField

// Options 面板引用（用于控制显示/隐藏）
private lateinit var optionPanel: JComponent
```

#### 3.4.2 Button 详情面板修改

```kotlin
private fun createButtonDetailPanel(): JComponent {
    val outerPanel = JPanel(BorderLayout())
    val panel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createTitledBorder("Button 详情")
    }

    // 原有字段：Name
    // ...

    // 原有字段：Icon
    // ...

    // 新增：直接命令
    val commandPanel = JPanel(BorderLayout())
    commandPanel.add(JLabel("直接命令:"), BorderLayout.WEST)
    buttonCommandField = JBTextField().apply {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateButtonCommand()
            override fun removeUpdate(e: DocumentEvent?) = updateButtonCommand()
            override fun changedUpdate(e: DocumentEvent?) = updateButtonCommand()
        })
    }
    commandPanel.add(buttonCommandField, BorderLayout.CENTER)
    panel.add(commandPanel)

    // 新增：工作目录
    val dirPanel = JPanel(BorderLayout())
    dirPanel.add(JLabel("工作目录:"), BorderLayout.WEST)
    buttonWorkingDirectoryField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(...)
        textField.document.addDocumentListener(...)
    }
    dirPanel.add(buttonWorkingDirectoryField, BorderLayout.CENTER)
    panel.add(dirPanel)

    // 新增：终端名称
    val terminalNamePanel = JPanel(BorderLayout())
    terminalNamePanel.add(JLabel("终端名称:"), BorderLayout.WEST)
    buttonTerminalNameField = JBTextField().apply {
        document.addDocumentListener(...)
    }
    terminalNamePanel.add(buttonTerminalNameField, BorderLayout.CENTER)
    panel.add(terminalNamePanel)

    outerPanel.add(panel, BorderLayout.NORTH)
    return outerPanel
}
```

#### 3.4.3 Options 面板显示/隐藏逻辑

```kotlin
private fun updateButtonCommand() {
    if (ignoreUpdate) return
    selectedButton?.command = buttonCommandField.text
    // 切换 Options 面板显示状态
    updateOptionPanelVisibility()
}

private fun updateOptionPanelVisibility() {
    val isDirectMode = selectedButton?.isDirectCommandMode() == true
    optionPanel.isVisible = !isDirectMode
}
```

### 3.5 验证逻辑修改

**文件**：`CCBarSettingsPanel.kt`

```kotlin
fun validate(): List<String> {
    val errors = mutableListOf<String>()

    for ((buttonIndex, button) in editingState.buttons.withIndex()) {
        // Button 基础验证
        if (button.name.isBlank()) {
            errors.add("Button ${buttonIndex + 1}: 名称不能为空")
        }
        if (editingState.buttons.count { it.name == button.name } > 1) {
            errors.add("Button '${button.name}': 名称重复")
        }

        // 直接命令模式验证
        if (button.isDirectCommandMode()) {
            if (button.defaultTerminalName.isBlank()) {
                errors.add("Button '${button.name}': 直接命令模式下，终端名称不能为空")
            }
            // Options 不验证
        } else {
            // 选项列表模式验证（原有逻辑）
            if (button.options.isEmpty()) {
                errors.add("Button '${button.name}': 未配置直接命令时，必须至少有一个 Option")
            }
            // 原有 Option 验证逻辑...
        }
    }

    return errors
}
```

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarSettings.kt` | ButtonConfig 新增字段：command, workingDirectory, defaultTerminalName；修改 deepCopy() |
| `CCBarButtonAction.kt` | 修改 actionPerformed() 增加直接命令判断；修改 update() 修改启用条件 |
| `CCBarSettingsPanel.kt` | 新增 Button 级别字段编辑；增加 Options 面板显示/隐藏逻辑；修改验证逻辑 |
| `CCBarTerminalService.kt` | 新增 openTerminalForButton() 方法及相关辅助方法 |

### 4.2 不受影响的部分

- SubButton 配置和逻辑
- Option 的命令执行逻辑（非直接命令模式）
- 弹出菜单样式（CCBarPopupBuilder）
- 终端创建和命令执行的核心逻辑
- 配置导入/导出（JSON 格式向后兼容）

### 4.3 向后兼容性

- 新增字段均有默认值（空字符串）
- 现有配置（无新字段）可正常加载和使用
- 现有 Button 默认为"选项列表模式"

---

## 5. 验收标准

### 5.1 功能验收

- [x] Button 可以配置直接命令（command 字段）
- [x] Button 可以配置工作目录（workingDirectory 字段）
- [x] Button 可以配置终端名称（defaultTerminalName 字段）
- [ ] 点击直接命令模式的 Button，弹出终端命名对话框
- [ ] 点击直接命令模式的 Button，直接执行命令（不弹出选项列表）
- [ ] 点击选项列表模式的 Button，弹出选项列表（原有行为）
- [ ] Button 既无直接命令又无 Options 时，按钮禁用

### 5.2 UI 验收

- [x] Button 详情面板新增三个字段：直接命令、工作目录、终端名称
- [ ] 直接命令不为空时，Options 面板隐藏
- [ ] 直接命令为空时，Options 面板显示
- [ ] 切换直接命令时，Options 面板显示/隐藏平滑切换

### 5.3 兼容性验收

- [ ] 现有配置可正常加载（向后兼容）
- [ ] 配置导入/导出正常工作
- [x] 兼容 IntelliJ IDEA 2023.1+
- [ ] 兼容 macOS/Windows/Linux

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 现有配置迁移问题 | 低 | 新增字段有默认值，旧配置自动兼容 |
| UI 布局在直接命令模式下显示异常 | 低 | 使用动态显示/隐藏，保持原有布局 |
| 验证逻辑遗漏 | 低 | 完整的单元测试和手动测试 |
| 导入旧版本 JSON 配置 | 低 | Gson 自动忽略未知字段，新字段使用默认值 |

---

## 7. 后续优化建议

1. **命令历史**：为直接命令模式的 Button 记录执行历史
2. **快捷键绑定**：支持为 Button 绑定快捷键
3. **命令模板**：支持在直接命令中使用变量（如 `${projectDir}`）
4. **批量操作**：支持批量设置 Button 的工作目录

---

## 8. 附录：UI 效果示意

### 8.1 选项列表模式（command 为空）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: CCBar                                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Toolbar Buttons     │  │ Button Details                                  │ │
│  │                     │  │                                                   │ │
│  │ ▶ Claude Code       │  │ Name:       [Claude Code                    ]   │ │
│  │   Dev Tools         │  │ Icon:       [Browse...                      ]   │ │
│  │                     │  │ Command:    [                              ]   │ │
│  │                     │  │ Work Dir:   [                              ]   │ │
│  │                     │  │ Term Name:  [                              ]   │ │
│  │                     │  │                                                   │ │
│  │                     │  │ ─────────────────────────────────────────────────  │ │
│  │                     │  │ Options (分组)                                   │ │
│  │                     │  │ ┌──────────────────────────────────────────────┐ │ │
│  │                     │  │ │ Option: Model                                │ │ │
│  │                     │  │ │ Base Command: ...                            │ │ │
│  │                     │  │ └──────────────────────────────────────────────┘ │ │
│  └─────────────────────┘  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 直接命令模式（command 不为空）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: CCBar                                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Toolbar Buttons     │  │ Button Details                                  │ │
│  │                     │  │                                                   │ │
│  │   Claude Code       │  │ Name:       [NPM Test                       ]   │ │
│  │ ▶ NPM Test          │  │ Icon:       [Browse...                      ]   │ │
│  │   Dev Tools         │  │ Command:    [npm test                       ]   │ │
│  │                     │  │ Work Dir:   [                              ]   │ │
│  │                     │  │ Term Name:  [NPM Test                       ]   │ │
│  │                     │  │                                                   │ │
│  │                     │  │ ─────────────────────────────────────────────────  │ │
│  │                     │  │ ℹ️ 直接命令模式下，Options 配置不可用              │ │
│  │                     │  │                                                   │ │
│  └─────────────────────┘  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 点击直接命令模式 Button

```
┌─────────────────────────────┐
│  终端命名                    │
│  ─────────────────────────  │
│  [NPM Test             ]    │
│                             │
│     [ OK ]  [ Cancel ]      │
└─────────────────────────────┘
```
