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
CommandBar（工具栏按钮）
  ├── [直接命令模式] 直接执行 CommandBar.command
  └── [命令列表模式]
        └── Command（命令，绑定 baseCommand + 可选工作目录）
              └── QuickParam（快捷参数，绑定 params 纯文本）
```

**命令生成规则：**
```
直接命令模式：最终执行命令 = CommandBar.command
命令列表模式：最终执行命令 = Command.baseCommand + (QuickParam.params 不为空 ? " " + QuickParam.params : "")
```

- **CommandBar**：工具栏上的入口按钮。支持两种模式：
  - **直接命令模式**：CommandBar 绑定 command 字段，点击后直接执行命令（不弹出菜单）
  - **命令列表模式**：CommandBar 不绑定 command，点击后弹出 Command 列表
    - **简易模式**（可选）：开启后弹出菜单仅显示命令名称，不展示命令预览和快捷参数
  - 支持禁用（`enabled = false`），禁用后数据保留但工具栏不显示该按钮
- **Command**：按钮下的命令分组，绑定基础命令（baseCommand）和可选的工作目录。**点击 Command 本身直接执行 baseCommand（不带参数）**。支持禁用，禁用后弹出菜单不显示该命令。
- **QuickParam**：命令下的快捷参数，绑定参数文本（params）。点击后执行 Command.baseCommand + params。params 为纯文本，第一阶段不支持变量替换。支持禁用，禁用后弹出菜单不显示该快捷参数。

### 2.2 CommandBar 模式切换

| 模式 | 触发条件 | 点击行为 |
|------|----------|----------|
| 直接命令模式 | `CommandBar.command` 不为空 | 直接执行命令 + 命名弹窗 |
| 命令列表模式 | `CommandBar.command` 为空 | 弹出 Command 列表 |
| 命令列表模式（简易） | `CommandBar.command` 为空且 `simpleMode = true` | 弹出仅含名称的 Command 列表 |

**按钮启用条件**：`command` 不为空 OR 存在启用的普通 Command（`enabled = true` 且非分割线）

---

## 3. 核心功能需求

### 3.1 多个快捷按钮
- 支持在 ToolBar 中添加多个快捷按钮
- 每个按钮代表一个命令类别（如 "Claude Code"、"Dev Tools" 等）
- 每个按钮可自定义图标（支持 IDEA 内置图标、自定义 SVG/PNG 文件和 HTTP/HTTPS 网络图片）和标签

### 3.2 按钮点击行为

**直接命令模式**（CommandBar.command 不为空）：
- 点击 CommandBar 后直接弹出终端命名对话框
- 用户确认名称后执行 CommandBar.command
- 工作目录：优先使用 CommandBar.workingDirectory，否则使用项目根目录

**命令列表模式**（CommandBar.command 为空）：
- 点击快捷按钮后弹出命令菜单
- 每个 Command 行采用**三列布局**：命令预览输入框 | 快捷参数列表 | 命令名称
- **布局宽度动态计算**：
  - 按钮宽度：根据按钮文字动态计算，确保完整显示
  - 命令预览框宽度：根据所有命令中最长的命令文本动态计算（有最大宽度限制）
  - 命令名称宽度：根据所有命令中最长的名称动态计算
  - 宽度补偿：当某行按钮总宽度小于所有行中最大按钮总宽度时，命令预览框自动补偿差值，确保每行"预览框+按钮"总宽度一致
- **命令预览输入框**：
  - 文字**右对齐**显示
  - 默认显示 `Command.baseCommand`（基础命令）
  - 鼠标悬浮到快捷参数时，显示完整命令 `Command.baseCommand + QuickParam.params`
  - 输入框为只读状态，但**可点击**执行基础命令
- **命令名称**：**左对齐**显示，可点击
- **点击命令名称**：执行 Command.baseCommand（不带参数）
- **点击命令预览输入框**：执行 Command.baseCommand（不带参数）
- **点击快捷参数**：执行 Command.baseCommand + QuickParam.params

```
┌──────────────────────────────────────────────────────────────────┐
│        claude │ [Default][Sonnet][Opus] │ Model                  │
│        claude │ [Home][Work]            │ Workspace              │
│        claude │ [Dev]                   │ System                 │
└──────────────────────────────────────────────────────────────────┘
    ↑ 右对齐      ↑ 动态宽度，按钮数量不限      ↑ 左对齐
    ↑ 点击命令预览或命令名称都执行基础命令
                   ↑ 悬浮快捷参数时命令预览显示完整命令
```

### 3.3 快捷参数（参数绑定）
- 每个快捷参数绑定不同的参数变体（纯文本）
- 点击快捷参数后执行：Command.baseCommand + QuickParam.params
- 快捷参数可显示图标或文字标签

### 3.4 命令配置
- 支持在设置面板中配置：
  - 快捷按钮列表（名称、图标）
  - 每个按钮下的命令列表（分组名称、基础命令、工作目录）
  - 每个命令下的快捷参数列表（参数名称 + 绑定参数）
- 配置为**应用级全局**，所有项目共享同一套配置
- 支持配置的导入/导出（JSON 格式）

### 3.5 终端自动执行
- 每次点击**始终新建**终端 Tab（不复用已有终端）
- 自动将完整命令输入终端并执行
- 终端工作目录默认为当前项目根目录，如果 Command 配置了自定义工作目录则使用自定义值

### 3.6 终端窗口管理
- 支持两种终端打开模式：
  - **工具窗口模式**（默认）：在 Terminal 工具窗口中新建终端标签页
  - **编辑器模式**：在编辑器区域以编辑器 Tab 形式打开终端，适合长时间运行的 AI 编程助手场景
- 终端打开模式可在 CommandBar（直接命令模式）和 Command 上分别配置
- 用户可在 CommandPreviewDialog 中临时切换终端打开模式

#### 两种模式的能力对比

两种模式底层均通过 `LocalTerminalDirectRunner` 创建终端会话，终端本身的能力完全一致：

| 能力 | 工具窗口模式 | 编辑器模式 |
|------|:----------:|:---------:|
| 终端基本功能（输入/输出/颜色） | ✅ | ✅ |
| Shell Integration（提示符检测、命令完成状态） | ✅ | ✅ |
| IDE 终端字体/配色设置 | ✅ | ✅ |
| 复制/粘贴/Find | ✅ | ✅ |
| LocalTerminalCustomizer 扩展点 | ✅ | ✅ |
| 与代码 Tab 混排/分屏/拖拽 | ❌ | ✅ |
| Terminal 工具窗口 Tab 管理 | ✅ | ❌ |
| 会话恢复（IDE 重启后恢复） | ✅ | ❌ |

**差异说明**：
- 两种模式的终端能力完全一致，差异仅在于 Tab 所在的容器
- 编辑器模式适合需要长时间运行、与代码频繁切换的场景（如 AI 编程助手）
- 工具窗口模式适合传统的终端使用场景，可享受 Terminal 工具窗口的完整管理能力

### 3.7 终端命名与参数配置弹窗
- 点击 Command 或快捷参数后，**每次都弹出**命令预览与参数配置对话框
- 弹框采用三行布局：
  - **第一行**：终端标签名称输入框（默认名称来自 Command 配置的 `defaultTerminalName` 字段）+ "在编辑器中打开"复选框（默认值取决于对应配置的 terminalMode）
  - **第二行**：环境变量展示框（只读）+ `[…]` 编辑按钮，点击打开环境变量列表编辑对话框
  - **第三行**：命令输入框（可编辑，预填基础命令 + 空格）
- 最终执行命令 = 环境变量注入语句 + 命令（如果有环境变量）
- 点击"执行"创建终端并执行完整命令
- 点击"取消"取消本次操作，不创建终端

### 3.8 环境变量配置
- CommandBar 支持两层环境变量：`commonEnvVariables`（全局）和 `envVariables`（直接命令专用）
- Command 支持 `envVariables` 环境变量
- `commonEnvVariables` 作为全局默认，对 CommandBar 下所有 Command 和直接命令生效
- 合并规则（统一）：
  - 直接命令模式：最终环境变量 = `CommandBar.commonEnvVariables` 合并 `CommandBar.envVariables`（后者覆盖同名）
  - Command 列表模式：最终环境变量 = `CommandBar.commonEnvVariables` 合并 `Command.envVariables`（后者覆盖同名）
- 环境变量以 `KEY1=val1;KEY2=val2` 格式存储
- 设置面板中提供可编辑文本框 + `[…]` 按钮，文本框可直接输入编辑，也可点击按钮打开表格形式的编辑对话框
- CommandBar 的全局环境变量字段始终可见，标签为"环境变量(公共):"
- 编辑对话框为两列表格（变量名、值），支持添加/删除/上移/下移
- 命令确认弹框中展示合并后的环境变量，用户可临时修改
- 执行时按 `;` 切割，每条按第一个 `=` 分为 key/value，根据 OS 类型选择注入语法：
  - macOS/Linux（bash/zsh）：`export KEY1=val1; export KEY2=val2; command`
  - Windows（PowerShell）：`$env:KEY1="val1"; $env:KEY2="val2"; command`

### 3.9 典型使用场景示例

**Claude Code 按钮的完整结构：**

| 工具栏按钮 | 命令（分组） | 命令基础命令 | 悬浮快捷参数 | 绑定参数 | 最终执行命令 |
|------------|--------------|-------------|------------|----------|-------------|
| Claude | Model | `claude` | *(点击 Model)* | *(无)* | `claude` |
| | | | [Sonnet] | `--model sonnet` | `claude --model sonnet` |
| | | | [Opus] | `--model opus` | `claude --model opus` |
| | Workspace | `claude` | [Home] | `--workspace ~/project-a` | `claude --workspace ~/project-a` |
| | | | [Work] | `--workspace ~/project-b` | `claude --workspace ~/project-b` |
| | System | `claude` | [Dev] | `--system-prompt "你是..."` | `claude --system-prompt "你是..."` |

**交互流程：**
1. 点击工具栏 `Claude` 按钮
2. 弹出下拉菜单，每行显示三列：命令预览（右对齐）| 快捷参数列表（动态宽度）| 命令名称（左对齐）
3. 鼠标悬浮到快捷参数时，命令预览显示完整命令
4. 点击命令名称、命令预览输入框或快捷参数
5. 弹出命令预览与参数配置对话框
6. 用户可输入追加参数（可选），可修改终端名称
7. 点击"执行"
8. 在 Terminal 工具窗口中新建终端标签页
9. 在终端中执行完整命令（基础命令 + 追加参数）

---

## 4. 功能示意图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  IDEA 顶部工具栏                                                                │
│  [Save] [Run] [🤖 Claude▼] [🔧 Dev Tools▼] [Debug]                             │
│                     │                                                            │
│                     ▼ 点击展开                                                   │
│               ┌────────────────────────────────────────────────────────┐          │
│               │        claude │ [Default][Sonnet] │ Model              │          │
│               │        claude │ [Home][Work]      │ Workspace          │          │
│               │        claude │ [Dev]             │ System             │          │
│               └────────────────────────────────────────────────────────┘          │
│               ↑ 右对齐    ↑ 动态宽度按钮列表       ↑ 左对齐                        │
│               ↑ 点击命令预览或命令名称执行基础命令                                 │
│                              ↑ 悬浮快捷参数时预览显示完整命令                       │
│                     │                                                            │
│                     ▼ 点击 [Sonnet] 或点击 "Model" / 命令预览框                  │
│               ┌───────────────────────────────────────┐                          │
│               │  命令预览与参数配置                    │                          │
│               │  ───────────────────────────────────  │                          │
│               │  终端标签名称: [Claude - Model   ]    │                          │
│               │  环境变量:  [ANTHROPIC_MODEL=...][…]  │                          │
│               │  命令: [claude --model sonnet      ]  │                          │
│               │                                       │                          │
│               │          [ 取消 ]  [ 执行 ]           │                          │
│               └───────────────────────────────────────┘                          │
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
- 每行 Command 采用三列布局：命令预览输入框（右对齐）| 快捷参数列表（动态宽度）| 命令名称（左对齐）
- 按钮宽度根据文字动态计算，数量不限
- 命令预览框宽度根据最长命令动态计算，按钮少的行会自动补偿宽度保持对齐
- 命令预览输入框默认显示基础命令，悬浮快捷参数时显示完整命令
- 点击命令名称或命令预览输入框：执行 Command.baseCommand（不带参数）
- 点击快捷参数：执行 Command.baseCommand + QuickParam.params
- 点击后弹出命令预览与参数配置对话框（两行布局）
- 第一行：终端标签名称；第二行：基础命令 + 追加参数输入框
- 最终执行命令 = 基础命令 + 空格 + 追加参数
- 点击"执行"后在 Terminal 工具窗口中新建终端标签页
- 点击"取消"取消操作，不创建终端
- 终端工作目录：优先使用 Command 配置的自定义目录，否则使用当前项目根目录
```

---

## 5. 配置数据结构

```json
{
  "commandBars": [
    {
      "id": "quick-npm-test",
      "name": "NPM Test",
      "icon": "TOOLS_ICON",
      "command": "npm test",
      "workingDirectory": "",
      "defaultTerminalName": "NPM Test",
      "commonEnvVariables": "",
      "envVariables": "",
      "commands": []
    },
    {
      "id": "claude-code",
      "name": "Claude Code",
      "icon": "AI_ICON",
      "command": "",
      "workingDirectory": "",
      "defaultTerminalName": "",
      "commonEnvVariables": "ANTHROPIC_MODEL=claude-sonnet-4-20250514",
      "envVariables": "",
      "commands": [
        {
          "id": "model",
          "name": "Model",
          "baseCommand": "claude",
          "workingDirectory": "",
          "defaultTerminalName": "Claude - Model",
          "envVariables": "ANTHROPIC_MODEL=claude-sonnet-4-20250514",
          "quickParams": [
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
          "envVariables": "",
          "quickParams": [
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
          "envVariables": "",
          "quickParams": [
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
      "commands": [
        {
          "id": "scripts",
          "name": "Scripts",
          "baseCommand": "npm",
          "workingDirectory": "",
          "defaultTerminalName": "npm Scripts",
          "envVariables": "",
          "quickParams": [
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
最终执行命令 = Command.baseCommand + (QuickParam.params 不为空 ? " " + QuickParam.params : "")
```

**工作目录解析规则：**
```
终端工作目录 = Command.workingDirectory 不为空 ? Command.workingDirectory : 当前项目根目录
```

---

## 6. 配置界面设计

### 6.1 设置入口

**Settings → Tools → CCBar**

### 6.2 界面布局

**命令列表模式**（CommandBar.command 为空）：

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: CCBar                                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Toolbar Buttons     │  │ CommandBar Details                               │ │
│  │                     │  │                                                   │ │
│  │ ▶ Claude Code       │  │ Name:       [Claude Code               ] [✓] 启用 │ │
│  │   Dev Tools         │  │ Icon:       [Browse...                      ]   │ │
│  │ [+][-][↑][↓]       │  │ Command:    [                              ]   │ │
│  └─────────────────────┘  │ [✓] 简易模式                                    │ │
│                           │ ─────────────────────────────────────────────────  │ │
│                           │ Commands (分组)                                  │ │
│  ┌─────────────────────┐  │                                                   │ │
│  │ Commands            │  │ ┌──────────────────────────────────────────────┐ │ │
│  │                     │  │ │ Command: Model                              │ │ │
│  │ ▶ Model             │  │ │ Name: [Model                    ] [✓] 启用 │ │ │
│  │   Workspace         │  │ │ Icon:              [Browse...          ]   │ │ │
│  │                     │  │ │ Base Command:      [claude              ]   │ │ │
│  │                     │  │ │ Term Name: [Claude - Model              ]   │ │ │
│  │ [+][-][↑][↓]       │  │ │ [ ] 在编辑器中打开                          │ │ │
│  └─────────────────────┘  │ │     默认通过终端工具窗口打开                 │ │ │
│                           │ │ Working Directory: [                    ]   │ │ │
│                           │ │ Env Variables: [                   ][…]   │ │ │
│                           │ │ Quick Params:                               │ │ │
│                           │ │ [Sonnet | Opus                     ][✏️]   │ │ │
│                           │ │ (只读摘要 + 编辑按钮，简易模式下隐藏)        │ │ │
│                           │ └──────────────────────────────────────────────┘ │ │
│                           └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**直接命令模式**（CommandBar.command 不为空）：

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: CCBar                                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Toolbar Buttons     │  │ CommandBar Details                               │ │
│  │                     │  │                                                   │ │
│  │   Claude Code       │  │ Name:       [NPM Test                ] [✓] 启用 │ │
│  │ ▶ NPM Test          │  │ Icon:       [Browse...                      ]   │ │
│  │   Dev Tools         │  │ Icon:       [Browse...                      ]   │ │
│  │                     │  │ Term Name:  [NPM Test                       ]   │ │
│  │ [+][-][↑][↓]       │  │ [ ] 在编辑器中打开                              │ │
│  └─────────────────────┘  │     默认通过终端工具窗口打开                     │ │
│                           │ Work Dir:   [                              ]   │ │
│                           │ Env Vars:   [                         ][…]   │ │
│                           │                                                   │ │
│                           │ ─────────────────────────────────────────────────  │ │
│                           │ ℹ️ 直接命令模式下，Commands 配置不可用              │ │
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
1. 选中某个按钮/命令/快捷参数
2. 点击 `[↑]` 上移或 `[↓]` 下移

#### 6.3.5 新增命令（Command）
1. 在 Commands 列表下方点击 `[+]`
2. 输入 Command 名称、Base Command、Working Directory（可选）、Default Terminal Name
3. 点击 Apply 保存

#### 6.3.6 编辑快捷参数
1. 选中某个 Command
2. 在 Quick Params 区域点击编辑按钮打开 QuickParam 编辑对话框
3. 对话框中以表格形式（名称、参数两列）编辑快捷参数，支持添加/删除/上移/下移
4. 点击「确定」保存修改，点击「取消」放弃修改

#### 6.3.7 导入/导出配置
1. 点击 `[ Export ]` 将当前配置导出为 JSON 文件
2. 点击 `[ Import ]` 从 JSON 文件导入配置（覆盖当前配置）
3. 点击 `[ Reset ]` 恢复默认配置（需确认）

### 6.4 数据验证规则

| 字段 | 验证规则 |
|------|----------|
| CommandBar Name | 必填，唯一 |
| CommandBar Icon | 必填，内置图标名称或有效的文件路径 |
| CommandBar Command | 可选，为空时必须配置 Commands |
| CommandBar Work Dir | 可选，留空表示使用当前项目根目录；如填写须为有效路径 |
| CommandBar Term Name | 直接命令模式下必填 |
| Command Name | 必填，同一按钮下唯一 |
| Base Command | 必填 |
| Working Directory | 可选，留空表示使用当前项目根目录；如填写须为有效路径 |
| Default Terminal Name | 必填，命名弹窗中的默认终端名称 |
| QuickParam Name | 必填，同一 Command 下唯一 |
| Params | 可选，允许空字符串 |

**模式互斥规则**：
- 直接命令模式（CommandBar.command 不为空）：Commands 配置被忽略
- 命令列表模式（CommandBar.command 为空）：必须至少有一个启用的 Command

**禁用项验证规则**：
- 禁用的 CommandBar 跳过内容验证（仅验证名称和唯一性）
- 禁用的 Command 跳过必填字段验证
- 禁用的 QuickParam 跳过验证

---

## 7. 配置数据持久化

### 7.1 持久化机制

使用 IDEA 的 `PersistentStateComponent` 实现配置持久化。支持两级配置：

| 配置级别 | 存储位置 | 作用域 | 说明 |
|---------|---------|--------|------|
| 系统配置 | `<IDEA_CONFIG>/options/ccbar.xml` | 全局 | 所有项目共享，升级 IDEA 时可通过 Toolbox 自动迁移 |
| 项目配置 | `<PROJECT>/.idea/ccbar.xml` | 项目级 | 仅当前项目使用，跟随项目，可被版本控制 |

**配置加载优先级**：
1. 如果项目配置已启用 → 使用项目配置
2. 如果项目配置未启用/不存在 → 使用系统配置

### 7.2 数据模型

```kotlin
data class PluginConfig(
    var commandBars: List<CommandBarConfig> = emptyList()
)

data class CommandBarConfig(
    var id: String = "",
    var name: String = "",
    var icon: String = "",  // 内置图标名称或自定义图标文件路径
    // 直接命令模式字段
    var command: String = "",  // 直接命令，为空则使用命令列表模式
    var workingDirectory: String = "",  // 工作目录，留空使用项目根目录
    var defaultTerminalName: String = "",  // 直接命令模式的默认终端名称
    var terminalMode: String = "",  // 终端打开模式：""=工具窗口, "editor"=编辑器
    var simpleMode: Boolean = false,  // 简易模式：仅显示命令名称，隐藏命令预览和快捷参数
    var commands: List<CommandConfig> = emptyList(),
    var enabled: Boolean = true  // 是否启用，禁用后工具栏不显示该按钮
)

data class CommandConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",  // 可选，留空则使用当前项目根目录
    var defaultTerminalName: String = "",  // 命名弹窗中的默认终端名称
    var quickParams: List<QuickParamConfig> = emptyList(),
    var type: String = "",  // 可选，空值或"command"=普通命令, "separator"=分割线
    var terminalMode: String = "",  // 终端打开模式：""=工具窗口, "editor"=编辑器
    var enabled: Boolean = true  // 是否启用，禁用后弹出菜单不显示该命令
)

data class QuickParamConfig(
    var id: String = "",
    var name: String = "",
    var params: String = "",
    var icon: String = "",  // 可选，支持自定义快捷参数图标
    var enabled: Boolean = true  // 是否启用，禁用后弹出菜单不显示该快捷参数
)
```

### 7.3 图标处理

| 图标类型 | 存储格式 | 说明 |
|---------|---------|------|
| IDEA 内置图标 | `builtin:AllIcons.Actions.Execute` | 以 `builtin:` 前缀标识 |
| 自定义图标文件 | `file:/path/to/icon.svg` | 以 `file:` 前缀标识，支持 SVG、PNG、JPG、GIF、BMP、ICO |
| 网络图片 | `https://example.com/icon.svg` | 以 `http://` 或 `https://` 开头，异步下载并缓存到磁盘 |

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
    commandBars = listOf(
        CommandBarConfig(
            id = "claude-code-default",
            name = "Claude Code",
            icon = "builtin:AllIcons.Actions.Execute",
            commands = listOf(
                CommandConfig(
                    id = "model",
                    name = "Model",
                    baseCommand = "claude",
                    workingDirectory = "",
                    defaultTerminalName = "Claude - Model",
                    quickParams = listOf(
                        QuickParamConfig(id = "sonnet", name = "Sonnet", params = "--model sonnet"),
                        QuickParamConfig(id = "opus", name = "Opus", params = "--model opus")
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
| CommandBar 直接命令模式 | P0 | CommandBar 可绑定直接命令，点击后直接执行（不弹出菜单） |
| 下拉命令菜单（内联快捷参数） | P0 | 点击弹出菜单，快捷参数内联显示在 Command 行右侧 |
| Command 可点击 | P0 | 点击 Command 名称执行 baseCommand（不带参数） |
| 命令配置面板 | P0 | 可视化配置按钮、命令、快捷参数，支持排序 |
| 终端始终新建 | P0 | 每次执行都新建终端 Tab |
| 终端命名弹窗 | P0 | 每次执行前弹出命名对话框，默认名称来自 Command/CommandBar 配置 |
| 终端打开到工具窗口 | P0 | 在 Terminal 工具窗口中新建终端标签页 |
| 自定义图标 | P0 | 支持 IDEA 内置图标、自定义 SVG/PNG 和 HTTP/HTTPS 网络图片 |
| Command/CommandBar 工作目录 | P0 | 支持 Command/CommandBar 级自定义工作目录，默认项目根目录 |
| 导入/导出配置 | P1 | 支持 JSON 格式配置导入导出 |
| 项目级配置 | P1 | 支持为每个项目设置独立的按钮配置，存储在 .idea/ccbar.xml |
| 命令编辑支持 | P2 | 支持命令模板/变量替换 |
| 环境变量配置 | P2 | 支持为 Command/QuickParam 配置环境变量 |

---

## 11. 后续扩展方向

- 支持热键绑定
- 命令执行历史记录
- 动态变量（如 `${projectDir}`、`${fileName}` 等）
- 命令执行结果通知
- 支持多平台（VSCode、Cursor 等）
- 跨 Command 参数组合（同时选 Model + Workspace 生成组合命令）
- 环境变量配置
- 终端复用策略（同名终端检测与复用）
- ~~自定义 Shell 路径~~（**已搁置** — Story-08，技术调研已完成，详见 `docs/specs/story-08/`）

---