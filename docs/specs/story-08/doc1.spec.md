# Story-08: 自定义 Shell 路径 — 已搁置

> 状态：**搁置 (Shelved)**
> 搁置日期：2026-03-01

## 1. 需求背景

### 1.1 当前实现

CCBar 打开终端时，Shell 路径固定来自 IDE 全局设置（`Settings > Tools > Terminal > Shell path`），无法按 Option 级别自定义。

### 1.2 用户需求

支持在 Option 级别配置自定义 Shell 路径（如 bash/zsh/fish），使不同的选项可以使用不同的 Shell 启动终端，同时保留 Shell Integration 能力。

## 2. 技术调研

技术调研已完成，详见 `doc0.tech-research.md`。

关键结论：
- `ShellStartupOptions.shellCommand` 是自定义 Shell 的正确入口
- Shell Integration 会由 `configureStartupOptions()` 自动注入
- 编辑器模式通过手动拆解 Runner 流程即可实现（推荐方案 A）
- 工具窗口模式需进一步验证 `startShellTerminalWidget()` API

## 3. 搁置说明

本 Story 技术调研已完成，实现方案已明确，但因优先级调整暂缓实现。后续恢复开发时可直接参考技术调研报告进行开发。
