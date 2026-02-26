# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概述

CCBar 是一个 IntelliJ IDEA 插件，在工具栏中添加可配置的快捷按钮，用于在终端标签页中快速启动命令。主要使用场景是以不同参数组合启动 AI 编程助手（如 Claude Code）。

## 架构

三层配置结构：**Button**（工具栏入口，纯容器）→ **Option**（绑定 baseCommand + 可选 workingDirectory）→ **SubButton**（绑定 params 文本）。最终命令 = `Option.baseCommand + SubButton.params`。

核心技术选型：
- **开发语言**：Kotlin
- **构建工具**：Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin 2.x (`org.jetbrains.intellij.platform`)
- **最低兼容版本**：IntelliJ IDEA 2023.1 (Build 231)，JDK 17
- **终端在编辑器区域打开**：自定义 `FileEditor` + `FileEditorProvider` 嵌入 `JBTerminalWidget`（不使用 TerminalToolWindowManager）
- **弹出菜单**：自定义 `JBPopup` + Swing 面板（非标准 ActionGroup 子菜单）
- **设置界面**：手动 Swing（JBSplitter、ToolbarDecorator、JBList、TableView）+ Kotlin UI DSL v2 构建表单
- **配置持久化**：应用级 `PersistentStateComponent`，存储于 `ccbar.xml`
- **插件依赖**：`org.jetbrains.plugins.terminal`（捆绑插件）

## 构建命令

```bash
./gradlew build              # 构建插件
./gradlew runIde             # 启动沙箱 IDEA 实例并加载插件
./gradlew buildPlugin        # 构建可分发的插件 ZIP 包
./gradlew verifyPlugin       # 运行 JetBrains Plugin Verifier 兼容性检查
```

## 关键文档

- `docs/spec.md` — 完整产品需求文档
- `docs/tech-design.md` — 技术选型与架构设计文档
- `docs/dev-plan.md` - 开发计划文档
