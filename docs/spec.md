# IDEA Quick Command Launcher 插件需求文档

## 1. 项目背景

在开发过程中，开发者经常需要快速启动 AI coding assistant（如 Claude Code），并灵活切换不同的配置参数（如模型选择、工作区、系统提示等）。目前需要：
1. 手动打开终端
2. 输入完整的命令（包含各种参数）
3. 每次切换参数都要重新输入或修改命令

重复性高，影响开发效率和体验。

---

## 2. 核心功能需求

### 2.1 多个快捷按钮
- 支持在工具栏添加多个快捷按钮
- 每个按钮代表一个命令类别（如 "Claude Code"、"GPT CLI" 等）
- 每个按钮可自定义图标和标签

### 2.2 下拉菜单/弹出选项
- 点击快捷按钮后弹出选项菜单
- 每个选项代表一个参数分组类别
- 选项后面悬浮显示多个子按钮

### 2.3 子按钮（参数绑定）
- 每个选项后面悬浮显示多个子按钮
- 每个子按钮绑定不同的参数变体
- 点击子按钮后执行：baseCommand + 子按钮绑定的参数
- 子按钮可显示图标或文字标签

### 2.4 命令配置
- 支持在设置面板中配置：
  - 快捷按钮列表（名称、图标）
  - 每个按钮下的选项列表（分组名称）
  - 每个选项下的子按钮列表（按钮名称 + 绑定参数）
  - 基础命令模板（可配置）

### 2.4 终端自动执行
- 选择选项后自动激活 IDEA 终端窗口
- 自动将完整命令输入终端并执行
- 支持在已有终端或新终端中执行

### 2.4.1 终端窗口管理（新增）
- 自动将终端窗口移动到编辑器区域（而非底部工具栏）
- 支持分屏显示：左侧/右侧/下方
- 自动固定终端标签页，防止意外关闭
- 支持全局设置：是否自动移动和固定
- 记忆上次分屏位置

### 2.4.2 终端命名弹窗
- 点击选项或子按钮前，弹出终端命名对话框
- 用户可为新终端标签页输入名称
- 支持默认名称模板（基于选项/子按钮名称）
- 提供"记住本次选择"选项，后续跳过弹窗
- 支持全局设置：是否每次都提示命名

### 2.5 典型使用场景示例

**Claude Code 按钮的完整结构：**

| 工具栏按钮 | 选项（分组） | 悬浮子按钮 | 绑定参数 | 执行命令 |
|------------|--------------|------------|----------|----------|
| 🤖 Claude | Model | [Default] | (空) | `claude` |
| | | [Sonnet] | `--model sonnet` | `claude --model sonnet` |
| | | [Opus] | `--model opus` | `claude --model opus` |
| | Workspace | [🏠 Home] | `--workspace ~/workspace/project-a` | `claude --workspace ~/workspace/project-a` |
| | | [💼 Work] | `--workspace ~/workspace/project-b` | `claude --workspace ~/workspace/project-b` |
| | System | [⚙️ Dev] | `--system-prompt "你是..."` | `claude --system-prompt "你是..."` |

**交互流程：**
1. 点击工具栏 `🤖 Claude` 按钮
2. 弹出下拉菜单，显示 `Model`、`Workspace`、`System` 等选项
3. 每个选项后面悬浮显示多个子按钮（如 [Default] [Sonnet] [Opus]）
4. 点击子按钮，弹出终端命名对话框
5. 用户输入终端标签页名称（或使用默认值）
6. 确认后自动将终端移动到编辑器区域（右侧分屏）
7. 固定终端标签页（显示锁定图标）
8. 执行 `baseCommand + 绑定参数`

---

## 3. 功能示意图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  IDEA 顶部工具栏                                                                    │
│  [Save] [Run] [🤖 Claude▼] [🔧 Dev Tools▼] [Debug]                               │
│                              │                                                      │
│                              ▼ 点击展开                                             │
│                        ┌─────────────────────────────────┐                          │
│                        │ Model          [⚪][🟡][🔴]      │  ← 悬浮子按钮            │
│                        │ Workspace      [🏠][💼]          │  ← 悬浮子按钮            │
│                        │ System         [⚙️]             │  ← 悬浮子按钮            │
│                        └─────────────────────────────────┘                          │
│                              │                                                      │
│                              ▼ 点击 [🟡] 按钮                                        │
│                        ┌─────────────────────────────────┐                          │
│                        │  Terminal Naming                │                          │
│                        │  ───────────────────────────   │                          │
│                        │  Terminal Name:                 │                          │
│                        │  [Claude - Sonnet          ]    │  ← 默认名称可编辑        │
│                        │                                │                          │
│                        │  [✓] Remember this choice      │  ← 记住此选项            │
│                        │                                │                          │
│                        │       [ OK ]  [ Cancel ]        │                          │
│                        └─────────────────────────────────┘                          │
│                              │                                                      │
│                              ▼ 点击 OK 后                                           │
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  IDEA 主窗口                                                                          │
│  ┌───────────────────────────────┬───────────────────────────────────────────────────┐ │
│  │ Editor Area                  │ Terminal (编辑器区域)                             │ │
│  │                             │                                                   │ │
│  │ ┌─────────────────────────┐ │ ┌───────────────────────────────────────────────┐ │ │
│  │ │ Main.ts                 │ │ │ Claude - Sonnet 🔒                    │  ← 固定标签页   │ │ │
│  │ │                         │ │ │ ─────────────────────────────────────────     │ │ │
│  │ │ const app = ...         │ │ │ $ claude --model sonnet                      │ │ │
│  │ │                         │ │ │                                               │ │ │
│  │ │                         │ │ │                                               │ │ │
│  │ └─────────────────────────┘ │ │                                               │ │ │
│  │                             │ │                                               │ │ │
│  └───────────────────────────────┴───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

说明：
- 每行菜单项后面悬浮显示多个子按钮
- 子按钮可以是图标或文字标签
- 点击子按钮后弹出命名对话框
- 默认名称格式：{Button Name} - {Option Name} - {Sub Button Name}
- 点击 OK 后自动将终端移动到编辑器区域（右侧分屏）
- 终端标签页自动固定（显示锁定图标 🔒）
- 执行命令
```

---

## 4. 配置数据结构（初步设计）

```json
{
  "buttons": [
    {
      "id": "claude-code",
      "name": "Claude Code",
      "icon": "AI_ICON",
      "baseCommand": "claude",
      "options": [
        {
          "id": "model",
          "name": "Model",
          "subButtons": [
            {
              "id": "default",
              "name": "Default",
              "params": ""
            },
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
          "subButtons": [
            {
              "id": "default",
              "name": "Default",
              "params": ""
            },
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
      "baseCommand": "npm",
      "options": [
        {
          "id": "scripts",
          "name": "Scripts",
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
最终执行命令 = baseCommand + (subButton.params 不为空 ? " " + subButton.params : "")
```

---

## 5. 配置界面设计

### 5.1 设置入口

**Settings → Tools → Quick Command Launcher**

### 5.2 界面布局

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: Quick Command Launcher                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Toolbar Buttons     │  │ Button Details                                  │ │
│  │                     │  │                                                   │ │
│  │ ▶ Claude Code       │  │ Name:       [Claude Code                    ]   │ │
│  │   Dev Tools         │  │ Icon:       [AI_ICON ▼                       ]   │ │
│  │                     │  │ Base Command: [claude                        ]   │ │
│  │ [ + Add Button ]   │  │                                                   │ │
│  └─────────────────────┘  │                                                   │ │
│                           │ ─────────────────────────────────────────────────  │ │
│  ┌─────────────────────┐  │ Options (分组)                                   │ │
│  │ Options             │  │                                                   │ │
│  │                     │  │ ┌──────────────────────────────────────────────┐ │ │
│  │ ▶ Model             │  │ │ Model                                        │ │ │
│  │   Workspace         │  │ │ Sub Buttons:                                 │ │ │
│  │   System            │  │ │ ┌────────────┬──────────────────┐           │ │ │
│  │                     │  │ │ │ Name       │ Params            │           │ │ │
│  │ [ + Add Option ]   │  │ │ ├────────────┼──────────────────┤           │ │ │
│  └─────────────────────┘  │ │ │ [Default] │                  │ [x]       │ │ │ │
│                           │ │ │ [Sonnet]   │ --model sonnet   │ [x]       │ │ │ │
│                           │ │ │ [Opus]     │ --model opus     │ [x]       │ │ │ │
│                           │ │ └────────────┴──────────────────┘           │ │ │
│                           │ │ [ + Add Sub Button ]                        │ │ │
│                           │ └──────────────────────────────────────────────┘ │ │
│                           │                                                   │ │
│                           │ ─────────────────────────────────────────────────  │ │
│                           │ Terminal Naming Settings                          │ │
│                           │ [✓] Prompt for terminal name each time          │ │
│                           │ Default name template: [{Button} - {Option}] ▼    │ │
│                           │                                                   │ │
│                           │ ─────────────────────────────────────────────────  │ │
│                           │ Terminal Window Settings                          │ │
│                           │ [✓] Move to editor area                          │ │
│                           │ Split position: [Right ▼]                        │ │
│                           │ [✓] Pin terminal tab                             │ │
│                           │                                                   │ │
│                           │ [ Save ] [ Cancel ]                              │ │
│                           └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2.1 全局设置

| 分类 | 设置项 | 说明 | 默认值 |
|------|--------|------|--------|
| Terminal Naming | Prompt for terminal name | 每次执行前是否弹出命名对话框 | ✓ 选中 |
| | Default name template | 默认终端名称模板 | `{Button} - {Option} - {SubButton}` |
| | Name template variables | 支持的变量：{Button}、{Option}、{SubButton}、{Command} | - |
| Terminal Window | Move to editor area | 自动将终端移动到编辑器区域 | ✓ 选中 |
| | Split position | 分屏位置：Left / Right / Bottom | Right |
| | Pin terminal tab | 自动固定终端标签页 | ✓ 选中 |

### 5.3 交互流程

#### 5.3.1 新增工具栏按钮
1. 点击 `[ + Add Button ]`
2. 右侧显示空白表单
3. 填写 Name、Icon、Base Command
4. 点击 `[ Save ]` 保存

#### 5.3.2 编辑工具栏按钮
1. 左侧列表选中某个按钮
2. 右侧显示该按钮的详细信息
3. 修改后点击 `[ Save ]` 保存

#### 5.3.3 删除工具栏按钮
1. 左侧列表选中某个按钮
2. 点击 `[ - Delete ]` 按钮（需确认）

#### 5.3.4 新增选项（Option）
1. 在按钮详情区域点击 `[ + Add Option ]`
2. 输入 Option 名称
3. 点击保存

#### 5.3.5 新增子按钮
1. 选中某个 Option
2. 在 Sub Buttons 区域点击 `[ + Add Sub Button ]`
3. 输入 Name 和 Params
4. 点击 `[x]` 删除该子按钮

### 5.4 数据验证规则

| 字段 | 验证规则 |
|------|----------|
| Button Name | 必填，唯一 |
| Base Command | 必填 |
| Option Name | 必填，同一按钮下唯一 |
| Sub Button Name | 必填，同一 Option 下唯一 |
| Params | 可选，允许空字符串 |

---

## 6. 配置数据持久化

### 6.1 持久化机制

使用 IDEA 的 `PersistentStateComponent` 实现配置持久化，配置存储在：
```
<IDEA_CONFIG>/options/quick-command-launcher.xml
```

### 6.2 数据模型

```kotlin
data class PluginConfig(
    var buttons: List<ButtonConfig> = emptyList(),
    // 全局设置
    var globalSettings: GlobalSettingsConfig = GlobalSettingsConfig()
)

// 全局设置
data class GlobalSettingsConfig(
    // 每次执行前是否弹出命名对话框
    var promptTerminalName: Boolean = true,
    // 默认终端名称模板
    var terminalNameTemplate: String = "{Button} - {Option} - {SubButton}",
    // 是否自动将终端移动到编辑器区域
    var moveToEditorArea: Boolean = true,
    // 分屏位置：LEFT, RIGHT, BOTTOM
    var splitPosition: SplitPosition = SplitPosition.RIGHT,
    // 是否自动固定终端标签页
    var pinTerminalTab: Boolean = true
)

// 分屏位置枚举
enum class SplitPosition {
    LEFT,
    RIGHT,
    BOTTOM
}

data class ButtonConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",
    var baseCommand: String = "",
    var options: List<OptionConfig> = emptyList()
)

data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var subButtons: List<SubButtonConfig> = emptyList()
)

data class SubButtonConfig(
    var id: String = "",
    var name: String = "",
    var params: String = "",
    var icon: String = ""  // 可选，支持自定义子按钮图标
)
```

### 6.3 数据操作逻辑

| 操作 | 逻辑说明 |
|------|----------|
| 加载 | IDEA 启动时自动从 XML 加载配置 |
| 保存 | 修改后立即保存到 XML |
| 导出 | 将配置导出为 JSON 文件 |
| 导入 | 从 JSON 文件导入并合并配置 |
| 重置 | 恢复到默认配置模板 |

### 6.4 默认配置

插件首次安装时提供默认配置：

```kotlin
private val defaultConfig = PluginConfig(
    globalSettings = GlobalSettingsConfig(
        promptTerminalName = true,
        terminalNameTemplate = "{Button} - {Option} - {SubButton}",
        moveToEditorArea = true,
        splitPosition = SplitPosition.RIGHT,
        pinTerminalTab = true
    ),
    buttons = listOf(
        ButtonConfig(
            id = "claude-code-default",
            name = "Claude Code",
            icon = "AI_ICON",
            baseCommand = "claude",
            options = listOf(
                OptionConfig(
                    id = "model",
                    name = "Model",
                    subButtons = listOf(
                        SubButtonConfig(id = "default", name = "Default", params = ""),
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

## 7. 非功能需求

| 需求项 | 说明 |
|--------|------|
| 性能 | 菜单展开响应时间 < 100ms |
| 兼容性 | 支持 IntelliJ IDEA 2023.1+ |
| 轻量化 | 插件体积 < 2MB |
| 可靠性 | 命令执行失败时有错误提示 |

---

## 8. MVP 功能范围

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 多快捷按钮 | P0 | 支持添加多个工具栏按钮 |
| 下拉选项菜单 | P0 | 点击弹出命令选项列表 |
| 命令配置面板 | P0 | 可视化配置按钮和命令选项 |
| 终端自动执行 | P0 | 自动打开终端并执行命令 |
| 终端命名弹窗 | P0 | 执行前弹出终端命名对话框 |
| 名称模板变量 | P0 | 支持 {Button}{Option}{SubButton} 变量 |
| 终端窗口移动 | P0 | 自动将终端移动到编辑器区域 |
| 分屏位置选择 | P0 | 支持 Left/Right/Bottom 分屏位置 |
| 标签页固定 | P0 | 自动固定终端标签页 |
| 命令编辑支持 | P1 | 支持命令模板/变量替换 |
| 导入/导出配置 | P2 | 支持配置文件的导入导出 |
| 分隔符/分组 | P2 | 选项菜单支持分组显示 |

---

## 9. 后续扩展方向

- 支持热键绑定
- 命令执行历史记录
- 动态变量（如 `${projectDir}`、`${fileName}` 等）
- 命令执行结果通知
- 支持多平台（VSCode Cursor 等）

---