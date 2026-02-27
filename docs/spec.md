# CCBar - IDEA Quick Command Launcher 插件需求文档

## 1. 项目背景

在开发过程中，开发者经常需要快速启动 AI coding assistant（如 Claude Code），并灵活切换不同的配置参数（如模型选择、工作区、系统提示等）。目前需要：
1. 手动打开终端
2. 输入完整的命令（包含各种参数）
3. 每次切换参数都要重新输入或修改命令

重复性高，影响开发效率和体验。

---

## 2. 核心概念

### 2.1 层级结构

插件的配置采用三层结构：

```
Button（工具栏按钮）
  ├── [直接命令模式] 直接执行 Button.command
  └── [选项列表模式]
        └── Option（选项，绑定 baseCommand + 可选工作目录）
              └── SubButton（子按钮，绑定 params 纯文本）
```

**命令生成规则：**
```
直接命令模式：最终执行命令 = Button.command
选项列表模式：最终执行命令 = Option.baseCommand + (SubButton.params 不为空 ? " " + SubButton.params : "")
```

- **Button**：工具栏上的入口按钮。支持两种模式：
  - **直接命令模式**：Button 绑定 command 字段，点击后直接执行命令（不弹出菜单）
  - **选项列表模式**：Button 不绑定 command，点击后弹出 Option 列表
- **Option**：按钮下的选项分组，绑定基础命令（baseCommand）和可选的工作目录。**点击 Option 本身直接执行 baseCommand（不带参数）**。
- **SubButton**：选项下的子按钮，绑定参数文本（params）。点击后执行 Option.baseCommand + params。params 为纯文本，第一阶段不支持变量替换。

### 2.2 Button 模式切换

| 模式 | 触发条件 | 点击行为 |
|------|----------|----------|
| 直接命令模式 | `Button.command` 不为空 | 直接执行命令 + 命名弹窗 |
| 选项列表模式 | `Button.command` 为空 | 弹出 Option 列表 |

**按钮启用条件**：`command` 不为空 OR `options` 不为空

---

## 3. 核心功能需求

### 3.1 多个快捷按钮
- 支持在 ToolBar 中添加多个快捷按钮
- 每个按钮代表一个命令类别（如 "Claude Code"、"Dev Tools" 等）
- 每个按钮可自定义图标（支持 IDEA 内置图标和自定义 SVG/PNG 文件）和标签

### 3.2 按钮点击行为

**直接命令模式**（Button.command 不为空）：
- 点击 Button 后直接弹出终端命名对话框
- 用户确认名称后执行 Button.command
- 工作目录：优先使用 Button.workingDirectory，否则使用项目根目录

**选项列表模式**（Button.command 为空）：
- 点击快捷按钮后弹出选项菜单
- 每个 Option 行采用**三列布局**：选项名称 | 命令预览输入框 | 子按钮列表
- **命令预览输入框**：
  - 默认显示 `Option.baseCommand`（基础命令）
  - 鼠标悬浮到子按钮时，显示完整命令 `Option.baseCommand + SubButton.params`
  - 输入框为只读状态，但**可点击**执行基础命令
- **点击选项名称**：执行 Option.baseCommand（不带参数）
- **点击命令预览输入框**：执行 Option.baseCommand（不带参数）
- **点击子按钮**：执行 Option.baseCommand + SubButton.params

```
┌──────────────────────────────────────────────────────────────────┐
│ Model     │ claude                    │ [Default][Sonnet][Opus] │
│ Workspace │ claude                    │ [Home][Work]            │
│ System    │ claude                    │ [Dev]                   │
└──────────────────────────────────────────────────────────────────┘
         ↑ 点击名称或命令预览都执行基础命令
                                          ↑ 悬浮子按钮时命令预览显示完整命令
```

### 3.3 子按钮（参数绑定）
- 每个子按钮绑定不同的参数变体（纯文本）
- 点击子按钮后执行：Option.baseCommand + SubButton.params
- 子按钮可显示图标或文字标签

### 3.4 命令配置
- 支持在设置面板中配置：
  - 快捷按钮列表（名称、图标）
  - 每个按钮下的选项列表（分组名称、基础命令、工作目录）
  - 每个选项下的子按钮列表（按钮名称 + 绑定参数）
- 配置为**应用级全局**，所有项目共享同一套配置
- 支持配置的导入/导出（JSON 格式）

### 3.5 终端自动执行
- 每次点击**始终新建**终端 Tab（不复用已有终端）
- 自动将完整命令输入终端并执行
- 终端工作目录默认为当前项目根目录，如果 Option 配置了自定义工作目录则使用自定义值

### 3.6 终端窗口管理
- 在 Terminal 工具窗口中新建终端标签页

### 3.7 终端命名弹窗
- 点击 Option 或子按钮后，**每次都弹出**终端命名对话框
- 用户可为新终端标签页输入名称
- 默认名称来自 Option 配置的 `defaultTerminalName` 字段
- 用户可修改后确认，或直接使用默认名称
- 点击 Cancel 取消本次操作，不创建终端

### 3.8 典型使用场景示例

**Claude Code 按钮的完整结构：**

| 工具栏按钮 | 选项（分组） | 选项基础命令 | 悬浮子按钮 | 绑定参数 | 最终执行命令 |
|------------|--------------|-------------|------------|----------|-------------|
| 🤖 Claude | Model | `claude` | *(点击 Model)* | *(无)* | `claude` |
| | | | [Sonnet] | `--model sonnet` | `claude --model sonnet` |
| | | | [Opus] | `--model opus` | `claude --model opus` |
| | Workspace | `claude` | [🏠 Home] | `--workspace ~/project-a` | `claude --workspace ~/project-a` |
| | | | [💼 Work] | `--workspace ~/project-b` | `claude --workspace ~/project-b` |
| | System | `claude` | [⚙️ Dev] | `--system-prompt "你是..."` | `claude --system-prompt "你是..."` |

**交互流程：**
1. 点击工具栏 `🤖 Claude` 按钮
2. 弹出下拉菜单，每行显示三列：选项名称 | 命令预览 | 子按钮列表
3. 鼠标悬浮到子按钮时，命令预览显示完整命令
4. 点击选项名称、命令预览输入框或子按钮
5. 弹出终端命名对话框，显示默认名称（可编辑）
6. 用户确认名称，点击 OK
7. 在 Terminal 工具窗口中新建终端标签页
8. 在终端中执行完整命令

---

## 4. 功能示意图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  IDEA 顶部工具栏                                                                │
│  [Save] [Run] [🤖 Claude▼] [🔧 Dev Tools▼] [Debug]                             │
│                     │                                                            │
│                     ▼ 点击展开                                                   │
│               ┌────────────────────────────────────────────────────────┐          │
│               │ Model     │ claude              │ [Default][Sonnet]   │          │
│               │ Workspace │ claude              │ [Home][Work]        │          │
│               │ System    │ claude              │ [Dev]               │          │
│               └────────────────────────────────────────────────────────┘          │
│                           ↑ 点击名称或命令预览执行基础命令                        │
│                                              ↑ 悬浮子按钮时预览显示完整命令      │
│                     │                                                            │
│                     ▼ 点击 [Sonnet] 或点击 "Model" / 命令预览框                  │
│               ┌─────────────────────────────┐                                    │
│               │  Terminal Name              │                                    │
│               │  ─────────────────────────  │                                    │
│               │  [Claude - Model       ]    │  ← 来自 Option.defaultTerminalName │
│               │                             │                                    │
│               │     [ OK ]  [ Cancel ]      │                                    │
│               └─────────────────────────────┘                                    │
│                     │                                                            │
│                     ▼ 点击 OK 后                                                 │
┌─────────────────────────────────────────────────────────────────────────────────┐
│  IDEA 主窗口                                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ Editor Tabs                                                            │    │
│  │ ┌──────────┐                                                           │    │
│  │ │ Main.ts  │                                                           │    │
│  │ └──────────┘                                                           │    │
│  │ ┌──────────────────────────────────────────────────────────────────┐   │    │
│  │ │ (编辑器内容区域)                                                  │   │    │
│  │ └──────────────────────────────────────────────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ Terminal 工具窗口                                                      │    │
│  │ ┌──────────┐ ┌──────────────────────────────┐                          │    │
│  │ │ Local    │ │ Claude - Model               │                          │    │
│  │ └──────────┘ └──────────────────────────────┘                          │    │
│  │ ┌──────────────────────────────────────────────────────────────────┐   │    │
│  │ │ $ claude --model sonnet                                         │   │    │
│  │ │                                                                  │   │    │
│  │ └──────────────────────────────────────────────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────┘

说明：
- 每行 Option 采用三列布局：选项名称 | 命令预览输入框 | 子按钮列表
- 命令预览输入框默认显示基础命令，悬浮子按钮时显示完整命令
- 点击选项名称或命令预览输入框：执行 Option.baseCommand（不带参数）
- 点击子按钮：执行 Option.baseCommand + SubButton.params
- 点击后弹出命名对话框（每次都弹出）
- 默认名称格式：来自 Option.defaultTerminalName（点击 Option 或其下任何 SubButton 均使用此名称）
- 点击 OK 后在 Terminal 工具窗口中新建终端标签页
- 点击 Cancel 取消操作，不创建终端
- 终端工作目录：优先使用 Option 配置的自定义目录，否则使用当前项目根目录
```

---

## 5. 配置数据结构

```json
{
  "buttons": [
    {
      "id": "quick-npm-test",
      "name": "NPM Test",
      "icon": "TOOLS_ICON",
      "command": "npm test",
      "workingDirectory": "",
      "defaultTerminalName": "NPM Test",
      "options": []
    },
    {
      "id": "claude-code",
      "name": "Claude Code",
      "icon": "AI_ICON",
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
          "subButtons": [
            {
              "id": "sonnet",
              "name": "Sonnet",
              "params": "--model sonnet"
            },
            {
              "id": "opus",
              "name": "Opus",
              "params": "--model opus"
            }
          ]
        },
        {
          "id": "workspace",
          "name": "Workspace",
          "baseCommand": "claude",
          "workingDirectory": "",
          "defaultTerminalName": "Claude - Workspace",
          "subButtons": [
            {
              "id": "workspace-a",
              "name": "Project A",
              "params": "--workspace ~/workspace/project-a"
            },
            {
              "id": "workspace-b",
              "name": "Project B",
              "params": "--workspace ~/workspace/project-b"
            }
          ]
        },
        {
          "id": "system",
          "name": "System",
          "baseCommand": "claude",
          "workingDirectory": "",
          "defaultTerminalName": "Claude - System",
          "subButtons": [
            {
              "id": "developer",
              "name": "Developer",
              "params": "--system-prompt \"你是资深的开发助手...\""
            }
          ]
        }
      ]
    },
    {
      "id": "dev-tools",
      "name": "Dev Tools",
      "icon": "TOOLS_ICON",
      "options": [
        {
          "id": "scripts",
          "name": "Scripts",
          "baseCommand": "npm",
          "workingDirectory": "",
          "defaultTerminalName": "npm Scripts",
          "subButtons": [
            {
              "name": "Test",
              "params": "test"
            },
            {
              "name": "Build",
              "params": "run build"
            },
            {
              "name": "Dev",
              "params": "run dev"
            }
          ]
        }
      ]
    }
  ]
}
```

**命令生成规则：**
```
最终执行命令 = Option.baseCommand + (SubButton.params 不为空 ? " " + SubButton.params : "")
```

**工作目录解析规则：**
```
终端工作目录 = Option.workingDirectory 不为空 ? Option.workingDirectory : 当前项目根目录
```

---

## 6. 配置界面设计

### 6.1 设置入口

**Settings → Tools → CCBar**

### 6.2 界面布局

**选项列表模式**（Button.command 为空）：

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
│  │ [+][-][↑][↓]       │  │ Work Dir:   [                              ]   │ │
│  └─────────────────────┘  │ Term Name:  [                              ]   │ │
│                           │                                                   │ │
│  ┌─────────────────────┐  │ ─────────────────────────────────────────────────  │ │
│  │ Options             │  │ Options (分组)                                   │ │
│  │                     │  │                                                   │ │
│  │ ▶ Model             │  │ ┌──────────────────────────────────────────────┐ │ │
│  │   Workspace         │  │ │ Option: Model                                │ │ │
│  │                     │  │ │ Base Command:      [claude              ]   │ │ │
│  │ [+][-][↑][↓]       │  │ │ Working Directory: [                    ]   │ │ │
│  └─────────────────────┘  │ │ Default Terminal Name: [Claude - Model  ]   │ │ │
│                           │ │ Sub Buttons: ...                             │ │ │
│                           │ └──────────────────────────────────────────────┘ │ │
│                           └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**直接命令模式**（Button.command 不为空）：

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
│  │ [+][-][↑][↓]       │  │ Term Name:  [NPM Test                       ]   │ │
│  └─────────────────────┘  │                                                   │ │
│                           │ ─────────────────────────────────────────────────  │ │
│                           │ ℹ️ 直接命令模式下，Options 配置不可用              │ │
│                           │                                                   │ │
│                           └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2.1 全局设置

当前版本无全局设置项，预留扩展位。

### 6.3 交互流程

#### 6.3.1 新增工具栏按钮
1. 点击按钮列表下方 `[+]`
2. 右侧显示空白表单
3. 填写 Name、选择 Icon
4. 点击 `[ Apply ]` 保存

#### 6.3.2 编辑工具栏按钮
1. 左侧列表选中某个按钮
2. 右侧显示该按钮的详细信息
3. 修改后点击 `[ Apply ]` 保存

#### 6.3.3 删除工具栏按钮
1. 左侧列表选中某个按钮
2. 点击 `[-]` 按钮（需确认）

#### 6.3.4 调整顺序
1. 选中某个按钮/选项/子按钮
2. 点击 `[↑]` 上移或 `[↓]` 下移

#### 6.3.5 新增选项（Option）
1. 在 Options 列表下方点击 `[+]`
2. 输入 Option 名称、Base Command、Working Directory（可选）、Default Terminal Name
3. 点击 Apply 保存

#### 6.3.6 新增子按钮
1. 选中某个 Option
2. 在 Sub Buttons 区域点击 `[+]`
3. 输入 Name 和 Params
4. 点击 `[x]` 删除该子按钮

#### 6.3.7 导入/导出配置
1. 点击 `[ Export ]` 将当前配置导出为 JSON 文件
2. 点击 `[ Import ]` 从 JSON 文件导入配置（覆盖当前配置）
3. 点击 `[ Reset ]` 恢复默认配置（需确认）

### 6.4 数据验证规则

| 字段 | 验证规则 |
|------|----------|
| Button Name | 必填，唯一 |
| Button Icon | 必填，内置图标名称或有效的文件路径 |
| Button Command | 可选，为空时必须配置 Options |
| Button Work Dir | 可选，留空表示使用当前项目根目录；如填写须为有效路径 |
| Button Term Name | 直接命令模式下必填 |
| Option Name | 必填，同一按钮下唯一 |
| Base Command | 必填 |
| Working Directory | 可选，留空表示使用当前项目根目录；如填写须为有效路径 |
| Default Terminal Name | 必填，命名弹窗中的默认终端名称 |
| Sub Button Name | 必填，同一 Option 下唯一 |
| Params | 可选，允许空字符串 |

**模式互斥规则**：
- 直接命令模式（Button.command 不为空）：Options 配置被忽略
- 选项列表模式（Button.command 为空）：必须至少有一个 Option

---

## 7. 配置数据持久化

### 7.1 持久化机制

使用 IDEA 的 `PersistentStateComponent` 实现配置持久化。配置为**应用级全局**，存储在：
```
<IDEA_CONFIG>/options/ccbar.xml
```

### 7.2 数据模型

```kotlin
data class PluginConfig(
    var buttons: List<ButtonConfig> = emptyList()
)

data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",  // 内置图标名称或自定义图标文件路径
    // 直接命令模式字段
    var command: String = "",  // 直接命令，为空则使用选项列表模式
    var workingDirectory: String = "",  // 工作目录，留空使用项目根目录
    var defaultTerminalName: String = "",  // 直接命令模式的默认终端名称
    var options: List<OptionConfig> = emptyList()
)

data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",  // 可选，留空则使用当前项目根目录
    var defaultTerminalName: String = "",  // 命名弹窗中的默认终端名称
    var subButtons: List<SubButtonConfig> = emptyList()
)

data class SubButtonConfig(
    var id: String = "",
    var name: String = "",
    var params: String = "",
    var icon: String = ""  // 可选，支持自定义子按钮图标
)
```

### 7.3 图标处理

| 图标类型 | 存储格式 | 说明 |
|---------|---------|------|
| IDEA 内置图标 | `builtin:AllIcons.Actions.Execute` | 以 `builtin:` 前缀标识 |
| 自定义图标文件 | `file:/path/to/icon.svg` | 以 `file:` 前缀标识，支持 SVG 和 PNG |

### 7.4 数据操作逻辑

| 操作 | 逻辑说明 |
|------|----------|
| 加载 | IDEA 启动时自动从 XML 加载配置 |
| 保存 | 修改后立即保存到 XML |
| 导出 | 将配置导出为 JSON 文件 |
| 导入 | 从 JSON 文件导入，覆盖当前配置 |
| 重置 | 恢复到默认配置模板 |

### 7.5 默认配置

插件首次安装时提供默认配置：

```kotlin
private val defaultConfig = PluginConfig(
    buttons = listOf(
        ButtonConfig(
            id = "claude-code-default",
            name = "Claude Code",
            icon = "builtin:AllIcons.Actions.Execute",
            options = listOf(
                OptionConfig(
                    id = "model",
                    name = "Model",
                    baseCommand = "claude",
                    workingDirectory = "",
                    defaultTerminalName = "Claude - Model",
                    subButtons = listOf(
                        SubButtonConfig(id = "sonnet", name = "Sonnet", params = "--model sonnet"),
                        SubButtonConfig(id = "opus", name = "Opus", params = "--model opus")
                    )
                )
            )
        )
    )
)
```

---

## 8. 非功能需求

| 需求项 | 说明 |
|--------|------|
| 性能 | 菜单展开响应时间 < 100ms |
| 兼容性 | 支持 IntelliJ IDEA 2024.2+ |
| 轻量化 | 插件体积 < 2MB |
| 可靠性 | 命令执行失败时有错误提示 |

---

## 9. 错误处理

| 场景 | 处理方式 |
|------|----------|
| baseCommand 不存在或不可执行 | 终端中显示 shell 原生的错误信息（如 command not found） |
| 终端创建失败 | 弹出 IDEA 通知（Notification）提示错误原因 |
| 配置文件损坏 | 自动回退到默认配置，弹出通知告知用户 |
| 导入的 JSON 格式错误 | 弹出错误对话框，不覆盖当前配置 |
| 自定义图标文件不存在 | 降级使用默认图标，弹出通知 |
| 自定义工作目录不存在 | 回退到项目根目录，弹出通知 |

---

## 10. MVP 功能范围

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 多快捷按钮 | P0 | 支持添加多个工具栏按钮 |
| Button 直接命令模式 | P0 | Button 可绑定直接命令，点击后直接执行（不弹出菜单） |
| 下拉选项菜单（内联子按钮） | P0 | 点击弹出菜单，子按钮内联显示在 Option 行右侧 |
| Option 可点击 | P0 | 点击 Option 名称执行 baseCommand（不带参数） |
| 命令配置面板 | P0 | 可视化配置按钮、选项、子按钮，支持排序 |
| 终端始终新建 | P0 | 每次执行都新建终端 Tab |
| 终端命名弹窗 | P0 | 每次执行前弹出命名对话框，默认名称来自 Option/Button 配置 |
| 终端打开到工具窗口 | P0 | 在 Terminal 工具窗口中新建终端标签页 |
| 自定义图标 | P0 | 支持 IDEA 内置图标和自定义 SVG/PNG |
| Option/Button 工作目录 | P0 | 支持 Option/Button 级自定义工作目录，默认项目根目录 |
| 导入/导出配置 | P1 | 支持 JSON 格式配置导入导出 |
| 命令编辑支持 | P2 | 支持命令模板/变量替换 |
| 环境变量配置 | P2 | 支持为 Option/SubButton 配置环境变量 |

---

## 11. 后续扩展方向

- 支持热键绑定
- 命令执行历史记录
- 动态变量（如 `${projectDir}`、`${fileName}` 等）
- 命令执行结果通知
- 支持多平台（VSCode、Cursor 等）
- 跨 Option 参数组合（同时选 Model + Workspace 生成组合命令）
- 环境变量配置
- 终端复用策略（同名终端检测与复用）

---