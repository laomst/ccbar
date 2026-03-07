# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

CCBar 是一个 IntelliJ IDEA 插件，在工具栏中添加可配置的快捷按钮，用于在终端标签页中快速启动命令。主要使用场景是以不同参数组合启动 AI 编程助手（如 Claude Code）。

## 构建命令

```bash
./gradlew build              # 构建插件（编译 + 打包）
./gradlew runIde             # 启动沙箱 IDEA 实例并加载插件（开发调试用）
./gradlew buildPlugin        # 构建可分发的插件 ZIP 包
./gradlew verifyPlugin       # 运行 JetBrains Plugin Verifier 兼容性检查
```

无单元测试。验证方式为 `./gradlew runIde` 手动测试。

## 架构

### 三层配置模型

**CommandBar** → **Command** → **QuickParam**

- **CommandBar**：工具栏入口按钮，支持两种模式：
  - 直接命令模式（`command` 不为空）：点击直接执行
  - 命令列表模式（`command` 为空）：点击弹出 Command 列表菜单
- **Command**：绑定 `baseCommand` + 可选 `workingDirectory`
- **QuickParam**：绑定 `params` 文本

最终命令 = 环境变量注入语句 + `Command.baseCommand + QuickParam.params`

### 环境变量

支持两层环境变量配置，格式为 `KEY1=val1;KEY2=val2`：
- **CommandBar.commonEnvVariables**：公共环境变量，对所有 Command 和直接命令生效
- **CommandBar.envVariables**：直接命令专用环境变量
- **Command.envVariables**：Command 级环境变量

合并规则：`commonEnvVariables` 为基础，`Command.envVariables`（或直接命令的 `CommandBar.envVariables`）同名覆盖。执行时根据 OS 注入（macOS/Linux 用 `export KEY=val;`，Windows 用 `$env:KEY="val";`）。相关 UI 组件：`EnvVariablesDialog`、`CommandPreviewDialog`。

### 两级配置持久化

- **系统配置**：`CCBarSettings`（应用级 `PersistentStateComponent`，存储于 `ccbar.xml`）
- **项目配置**：`CCBarProjectSettings`（项目级，存储于 `.idea/ccbar.xml`）
- 项目配置启用时优先使用项目配置，否则回退系统配置

### 核心模块

| 模块 | 关键类 | 职责 |
|------|--------|------|
| actions | `CCBarToolbarActionGroup` | 动态 ActionGroup，根据配置在工具栏生成按钮 |
| actions | `CCBarCommandBarAction` | 单个工具栏按钮的 Action |
| actions | `CCBarPopupBuilder` | 自定义 JBPopup 弹出菜单（三列布局：命令预览/快捷参数/命令名） |
| terminal | `CCBarTerminalService` | 终端创建与命令执行（支持工具窗口和编辑器两种模式） |
| terminal/editor | `TerminalEditorProvider` + `TerminalFileEditor` + `TerminalVirtualFile` | 编辑器模式终端实现（自定义 FileEditor 嵌入 JBTerminalWidget） |
| settings | `CCBarSettings` / `CCBarProjectSettings` | 配置持久化（PersistentStateComponent） |
| settings/ui | `CCBarSettingsPanel` | 设置界面主面板（手动 Swing 构建） |
| settings/ui/panels | `CommandBarListPanel` / `CommandBarDetailPanel` / `CommandListPanel` / `CommandDetailPanel` | 设置界面各子面板 |
| icons | `CCBarIcons` | 图标加载（支持 `builtin:` 内置图标、`file:` 本地文件、HTTP/HTTPS 网络图片） |

### 技术选型

- **开发语言**：Kotlin，**构建工具**：Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin 2.x
- **最低兼容**：IntelliJ IDEA 2024.2 (Build 242)，JDK 17（jvmToolchain 配置为 21）
- **终端编辑器模式**：自定义 `FileEditor` + `FileEditorProvider` 嵌入终端（不使用 TerminalToolWindowManager）
- **弹出菜单**：自定义 `JBPopup` + Swing 面板（非标准 ActionGroup 子菜单）
- **设置界面**：手动 Swing（JBSplitter、ToolbarDecorator、JBList）构建
- **插件依赖**：`org.jetbrains.plugins.terminal`（捆绑插件）
- **代码插桩已禁用**（`instrumentCode = false`）

### 插件注册（plugin.xml）

- 工具栏按钮通过 `DynamicActionGroup`（`CCBarToolbarActionGroup`）运行时动态创建
- 注册到 `MainToolbarRight`（New UI）和 `MainToolBar`（Classic UI）
- 设置页面注册为 `projectConfigurable`（parentId="tools"）

## 关键文档

- `docs/spec.md` — 完整产品需求文档（包含配置数据结构、界面布局、交互流程等）
- `docs/tech-design.md` — 技术选型与架构设计文档
- `docs/dev-plan.md` — 开发计划文档
- `docs/specs/story-XX/` — 各 Story 的特性规格文档

## 开发新特性流程

使用 `/ccbar-feature` skill 来规范地开发新特性，包括在 `docs/specs/story-xx/` 下创建规格文档并同步更新 `docs/spec.md`。
