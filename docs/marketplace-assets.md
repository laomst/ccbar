# CCBar Marketplace 素材

本文档包含发布到 JetBrains Marketplace 所需的素材和描述。

---

## 插件图标

已在 `src/main/resources/META-INF/` 目录：
- `pluginIcon.svg` - 亮色主题图标（80x80）
- `pluginIcon_dark.svg` - 暗色主题图标（80x80）

---

## 功能截图（推荐顺序）

JetBrains Marketplace 支持上传多张截图，建议按以下顺序展示：

| 序号 | 文件 | 说明 | 尺寸 |
|------|------|------|------|
| 1 | `docs/images/ccbar 整体效果.png` | 工具栏按钮整体效果展示 | 大 |
| 2 | `docs/images/ccbar 命令菜单.png` | 命令菜单界面（两行布局） | 中 |
| 3 | `docs/images/命令预览对话框.png` | 命令预览与参数配置对话框 | 中 |
| 4 | `docs/images/ccbarSettings.png` | 设置界面全景 | 大 |

### 备选截图

以下截图可用于丰富展示内容：

| 文件 | 说明 |
|------|------|
| `docs/images/编辑器中打开的 claudeCode.png` | 编辑器模式终端展示 |
| `docs/images/终端工具栏中打开的标签页.png` | 终端标签页前缀效果 |
| `docs/images/建议模式下的命令列表菜单.png` | 简易模式菜单展示 |
| `docs/images/启用项目配置 -1.png` | 项目配置功能展示 |

---

## 插件描述（用于 Marketplace 页面）

```
CCBar 是一个 IntelliJ IDEA 插件，在工具栏中添加可配置的快捷按钮，用于在终端标签页中快速启动命令。

核心特性：
• 三层配置结构（CommandBar → Command → QuickParam）
• 两种工作模式（直接命令/命令列表）
• 环境变量支持（公共 + 命令级，自动适配 Shell）
• 终端模式可选（工具窗口/编辑器模式）
• 自定义图标（内置/本地/网络图片）
• 配置导入导出（JSON 格式）

主要使用场景：
• AI 编程助手快速启动（Claude Code、Codex 等）
• 项目构建脚本（npm、docker compose 等）
• 常用命令一键执行
• 多项目差异化配置

安装后工具栏自动出现默认按钮，进入 Settings → Tools → CCBar 可自定义配置。
```

---

## 分类标签（Tags）

建议在 Marketplace 中使用以下标签：

```
terminal
command
launcher
ai
coding-agent
claude-code
codex
cursor
productivity
toolbar
quick-access
cli
```

**标签说明：**

| 标签 | 说明 |
|------|------|
| `coding-agent` | Coding Agent 类工具（通用） |
| `claude-code` | Claude Code 用户搜索 |
| `codex` | Codex CLI 用户搜索 |
| `cursor` | Cursor 等 AI IDE 用户搜索 |
| `cli` | 命令行工具爱好者 |

---

## 更新说明（Change Notes）

### 1.0.0
- 初始版本发布
- 支持 CommandBar → Command → QuickParam 三层配置
- 支持直接命令模式和命令列表模式
- 支持环境变量配置（公共 + 命令级）
- 支持终端工具窗口和编辑器两种模式
- 支持自定义图标（内置/本地/网络）
- 支持系统配置和项目配置
- 支持配置导入导出

---

## 外部链接

| 类型 | 文本 | URL |
|------|------|-----|
| 源代码 | GitHub | `https://github.com/laomst/ccbar` |
| 问题反馈 | Issues | `https://github.com/laomst/ccbar/issues` |
| 使用文档 | README | `https://github.com/laomst/ccbar/blob/main/README.md` |

---

## 发布检查清单

- [ ] 在 Marketplace 上传 4 张功能截图
- [ ] 上传插件图标（已打包在 ZIP 中）
- [ ] 填写插件描述（使用上方文案）
- [ ] 设置 Tags 分类标签
- [ ] 添加外部链接（GitHub、Issues）
- [ ] 填写更新说明（使用上方文案）
- [ ] 选择插件分类：`Tools` → `Productivity`
