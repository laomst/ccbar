# Story-09: Option 环境变量配置 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前 Option 配置只支持 `baseCommand`、`workingDirectory`、`defaultTerminalName` 等字段。命令执行时直接在终端中输入命令文本，不支持为命令注入环境变量。

用户的使用场景中经常需要为不同的命令设置不同的环境变量，例如：
- `ANTHROPIC_MODEL=claude-sonnet-4-20250514` 用于指定 AI 模型
- `API_KEY=xxx` 用于注入密钥
- `NODE_ENV=development` 用于指定运行环境

目前用户只能把环境变量写进 baseCommand 中（如 `ANTHROPIC_MODEL=xxx claude`），不够直观，且跨 shell 兼容性差。

### 1.2 用户需求

1. 在设置页面为 Option 和 Button（直接命令模式）增加环境变量配置
2. 设置页面中的环境变量行：左侧是一个只读文本展示框，显示 `KEY1=val1;KEY2=val2` 格式的摘要；右侧有一个按钮，点击后弹出环境变量列表编辑对话框
3. 在命令确认弹框（CommandPreviewDialog）中也展示环境变量行，放在命令行的上方，同样支持展示和编辑
4. 执行命令时，需要按照目标 shell 类型正确注入环境变量

---

## 2. 需求分析

### 2.1 数据模型变更

在 `OptionConfig` 和 `ButtonConfig` 中新增 `envVariables` 字段：

```kotlin
data class OptionConfig(
    // ... 现有字段 ...
    var envVariables: String = ""  // 环境变量，格式 "KEY1=val1;KEY2=val2"
)

data class ButtonConfig(
    // ... 现有字段 ...
    var envVariables: String = ""  // 环境变量，格式 "KEY1=val1;KEY2=val2"
)
```

**存储格式**：简单的 `KEY1=val1;KEY2=val2` 文本字符串。每个环境变量都是简单文本，不做特殊解析或校验。

### 2.2 设置页面 - 环境变量行

#### 2.2.1 Option 详情面板

在 Option 详情面板中，**基础命令**行之后增加一行环境变量配置：

```
┌─ Option 详情 ──────────────────────────────────────────────┐
│ 名称:            [Claude Model                        ]    │
│ 图标:            [builtin:/actions/execute.svg     ][▼][…] │
│ 基础命令:        [claude                              ]    │
│ 环境变量:        [ANTHROPIC_MODEL=xxx;API_KEY=yyy ][…]     │
│ 工作目录:        [                                 ][…]    │
│ 默认终端窗口名称: [Claude - Model                     ]    │
│ 终端打开模式:    [终端工具窗口                      ▼]     │
└────────────────────────────────────────────────────────────┘
```

**UI 组件**：
- 左侧：可编辑的 `JBTextField`，展示和编辑 `KEY1=val1;KEY2=val2` 格式的环境变量文本，用户可直接在文本框中输入或修改
- 右侧：`[…]` 按钮（浏览按钮），点击后弹出环境变量列表编辑对话框（适合需要精细编辑的场景）

#### 2.2.2 Button 详情面板（直接命令模式）

在 Button 直接命令模式下，**直接命令**行之后增加一行环境变量配置，布局同上。

#### 2.2.3 环境变量编辑对话框

点击 `[…]` 按钮后弹出一个 `DialogWrapper`，内含一个可编辑的环境变量列表：

```
┌─ 环境变量 ──────────────────────────────────────────┐
│                                                      │
│  ┌──────────────────┬───────────────────────┐        │
│  │ 变量名            │ 值                    │        │
│  ├──────────────────┼───────────────────────┤        │
│  │ ANTHROPIC_MODEL  │ claude-sonnet-4-20... │        │
│  │ API_KEY          │ sk-xxx                │        │
│  └──────────────────┴───────────────────────┘        │
│  [+][-][↑][↓]                                        │
│                                                      │
│                        [ 取消 ]  [ 确定 ]             │
└──────────────────────────────────────────────────────┘
```

- 使用 `JBTable` + `ToolbarDecorator`（与 SubButton 表格风格一致）
- 两列：变量名、值，均可直接编辑
- 支持添加、删除、上移、下移操作
- 点击"确定"后将表格数据序列化为 `KEY1=val1;KEY2=val2` 格式回写到 `envVariables` 字段
- 点击"取消"放弃修改

### 2.3 命令确认弹框 - 环境变量行

在 `CommandPreviewDialog` 中，在命令行**上方**增加一行环境变量配置：

```
┌─ 命令预览与参数配置 ──────────────────────────────────────────┐
│                                                               │
│  终端标签名称: [Claude - Model                   ] ☐在编辑器中打开 │
│  环境变量:    [ANTHROPIC_MODEL=xxx;API_KEY=yyy][…]            │
│  命令:        [claude --model sonnet                     ]    │
│                                                               │
│                              [ 取消 ]  [ 执行 ]               │
└───────────────────────────────────────────────────────────────┘
```

- 与设置页面中的交互方式一致：可编辑文本框 + `[…]` 按钮打开编辑对话框
- 用户可在执行前临时修改环境变量
- 弹框的环境变量默认值来自 Option/Button 配置

### 2.4 交互行为

| 交互 | 行为 |
|------|------|
| 设置页面直接编辑文本框 | 输入 `KEY1=val1;KEY2=val2` 格式文本，失焦后同步到数据模型 |
| 设置页面点击 `[…]` | 打开环境变量编辑对话框，保存后更新文本框和数据模型 |
| CommandPreviewDialog 直接编辑 | 可直接修改环境变量文本 |
| CommandPreviewDialog 点击 `[…]` | 打开环境变量编辑对话框，保存后更新文本框 |
| 环境变量为空 | 展示框为空（placeholder 显示"KEY1=val1;KEY2=val2"），执行时不添加注入前缀 |
| 执行命令 | 按 `;` 切割环境变量，每条按第一个 `=` 分为 key/value，根据 OS 类型拼接注入语句 |

---

## 3. 技术方案

### 3.1 环境变量注入方案（跨 shell 兼容）

由于终端启动后使用的 shell 类型可能不同（bash、zsh、PowerShell、cmd 等），环境变量的注入方式需要兼容多种 shell。

#### 解析逻辑

环境变量字符串按 `;` 切割为多个条目，每个条目按**第一个** `=` 分割为 key 和 value：

```kotlin
fun parseEnvVariables(envVariables: String): List<Pair<String, String>> {
    if (envVariables.isBlank()) return emptyList()
    return envVariables.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains("=") }
        .map { entry ->
            val idx = entry.indexOf('=')
            entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
        }
}
```

#### 各 shell 注入方式

| Shell | 注入语法 | 作用域 |
|-------|---------|--------|
| bash / zsh / sh | `export KEY1=val1; export KEY2=val2; command` | 当前 shell 会话 |
| PowerShell | `$env:KEY1="val1"; $env:KEY2="val2"; command` | 当前 shell 会话 |
| cmd.exe | `set KEY1=val1 && set KEY2=val2 && command` | 当前 shell 会话 |
| fish | `set -x KEY1 val1; set -x KEY2 val2; command` | 当前 shell 会话 |

#### 实现策略

通过检测操作系统类型自动选择注入语法，后续可配合 Story-08 的 shell 类型配置进一步精细化：

```kotlin
fun buildCommandWithEnv(envVariables: String, command: String): String {
    val vars = parseEnvVariables(envVariables)
    if (vars.isEmpty()) return command

    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> {
            // Windows: 默认 PowerShell
            val prefix = vars.joinToString("; ") { (k, v) -> "\$env:$k=\"$v\"" }
            "$prefix; $command"
        }
        else -> {
            // macOS / Linux: bash / zsh，每个变量单独 export 避免值含空格的问题
            val prefix = vars.joinToString("; ") { (k, v) -> "export $k=$v" }
            "$prefix; $command"
        }
    }
}
```

最终执行的命令示例：
```bash
# bash / zsh
export ANTHROPIC_MODEL=claude-sonnet-4-20250514; export API_KEY=sk-xxx; claude --model sonnet

# PowerShell
$env:ANTHROPIC_MODEL="claude-sonnet-4-20250514"; $env:API_KEY="sk-xxx"; claude --model sonnet
```

### 3.2 环境变量编辑对话框

```kotlin
class EnvVariablesDialog(
    project: Project?,
    private val initialEnvVars: String
) : DialogWrapper(project) {

    private val tableModel: DefaultTableModel  // 两列：变量名、值

    val envVariablesText: String  // 返回 "KEY1=val1;KEY2=val2" 格式
}
```

### 3.3 环境变量展示控件

使用可编辑的 `JBTextField` + 浏览按钮的组合：

```kotlin
// 可编辑文本框 + 浏览按钮
val envVarField = JBTextField()  // 可直接编辑
val envVarBrowseBtn = JButton("...").apply {
    addActionListener {
        val dialog = EnvVariablesDialog(project, envVarField.text)
        if (dialog.showAndGet()) {
            envVarField.text = dialog.envVariablesText
        }
    }
}
```

### 3.4 CommandPreviewDialog 改造

在现有的两行布局基础上，在"命令"行上方插入"环境变量"行：

```kotlin
// 新增第二行（环境变量行，插入在终端名称和命令之间）
gbc.gridy = 1  // 原命令行变为 gridy = 2
panel.add(JBLabel("环境变量:"), gbc)
panel.add(envVarFieldPanel, gbc)  // envVarField + browseButton
```

新增属性：
```kotlin
val envVariables: String  // 获取环境变量（可能被用户在弹框中修改过）
```

### 3.5 命令执行流程改造

在 `CCBarTerminalService` 中：

```kotlin
// openTerminal 方法
fun openTerminal(project: Project, option: OptionConfig, subButton: SubButtonConfig?) {
    val baseCommand = buildCommand(option, subButton)
    val dialog = CommandPreviewDialog(
        project, baseCommand, option.defaultTerminalName,
        defaultOpenInEditor, option.envVariables  // 新增参数
    )
    if (!dialog.showAndGet()) return

    val finalCommand = buildCommandWithEnv(dialog.envVariables, dialog.fullCommand)
    createTerminalAndExecute(project, finalCommand, dialog.terminalName, workingDir, dialog.openInEditor)
}
```

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarSettings.kt` | `OptionConfig` 和 `ButtonConfig` 增加 `envVariables` 字段及 deepCopy |
| `CCBarSettingsPanel.kt` | Option 详情和 Button 详情中添加环境变量行 UI |
| `CommandPreviewDialog.kt` | 增加环境变量行（展示 + 编辑入口） |
| `CCBarTerminalService.kt` | 命令执行前拼接环境变量 |

### 4.2 需新增的文件

| 文件 | 内容 |
|------|------|
| `EnvVariablesDialog.kt` | 环境变量列表编辑对话框 |

### 4.3 不受影响的部分

- 弹出菜单（`CCBarPopupBuilder`）—— 弹出菜单不展示环境变量
- 终端创建逻辑（`createTerminalWidget`、`executeCommandOnWidget`）
- 项目级配置逻辑（`CCBarProjectSettings`）—— 数据结构变更会自动跟随
- 图标系统

---

## 5. 验收标准

### 5.1 功能验收

- [ ] Option 详情面板中正确显示环境变量行
- [ ] Button 直接命令模式详情面板中正确显示环境变量行
- [ ] 点击 `[…]` 按钮能打开环境变量编辑对话框
- [ ] 编辑对话框支持添加、删除、上移、下移环境变量
- [ ] 编辑对话框确认后正确回写数据
- [ ] CommandPreviewDialog 中正确显示环境变量行
- [ ] CommandPreviewDialog 中可临时修改环境变量
- [ ] 实际执行命令时正确注入环境变量（export 格式）
- [ ] 环境变量为空时命令正常执行（不添加 export 前缀）
- [ ] 配置导入/导出包含环境变量数据

### 5.2 UI 验收

- [ ] 环境变量行与其他配置行样式一致
- [ ] 明暗主题下显示正常
- [ ] 编辑对话框布局合理，表格可正常编辑

### 5.3 兼容性验收

- [ ] IntelliJ IDEA 2023.1+ 兼容
- [ ] 已有配置（无 envVariables 字段）正常加载（默认空字符串）
- [ ] 不影响现有命令执行行为

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 环境变量值中包含分号 `;` | 中 | 本阶段简单文本处理，用户需避免值中使用分号。后续可改用 JSON 数组存储 |
| 环境变量值中包含特殊字符（空格、引号等） | 中 | 由 shell 自行处理，用户需自行添加引号转义 |
| Windows 下 shell 类型判断不准 | 低 | 默认使用 PowerShell 语法（Windows 10+ 默认 shell），后续 Story-08 落地后可精确配置 |
| 向后兼容性 | 低 | 新增字段默认值为空字符串，不影响已有配置 |

---

## 7. 后续优化建议

1. **Shell 类型适配**：配合 Story-08 的 shell 类型选择，自动选择 export / $env: / set 等语法
2. **环境变量存储格式升级**：使用 JSON 数组 `[{"key":"K","value":"V"}]` 替代分号分隔，解决值中包含分号的问题
3. **环境变量模板**：支持预设常用的环境变量模板
4. **环境变量引用**：支持 `${projectDir}` 等变量引用
