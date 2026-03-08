# CCBar 推广与运营指南

本文档提供 CCBar 插件发布后的推广和运营建议。

---

## 发布后检查清单

### 立即检查（发布后 1 小时内）

- [ ] JetBrains Marketplace 插件页面可访问
- [ ] 插件包下载链接正常
- [ ] 所有截图显示正确
- [ ] 描述和标签显示正确
- [ ] GitHub Release 页面创建成功
- [ ] GitHub Actions 构建状态正常

### 24 小时内检查

- [ ] 收到 Marketplace 确认邮件
- [ ] 插件出现在搜索结果的合理位置
- [ ] 没有用户报告严重问题

---

## 推广渠道

### 1. 社交媒体

#### Twitter / X
```
🎉 发布了我的新 IntelliJ IDEA 插件 CCBar！

在工具栏添加快捷按钮，快速启动命令行工具（如 Claude Code、Codex 等 AI 助手）。

🔗 https://plugins.jetbrains.com/plugin/xxxxx-ccbar
📦 https://github.com/laomst/ccbar

#IntelliJ #IDEA #Plugin #DeveloperTools #AI
```

#### LinkedIn
发布更专业的介绍，包括：
- 插件解决的问题
- 目标用户群体
- 主要功能特性

#### 微博 / 朋友圈
```
【新作品发布】CCBar - IntelliJ IDEA 工具栏快捷命令插件

为 IDEA 添加工具栏快捷按钮，一键启动 AI 编程助手（Claude Code、Codex 等）
支持自定义命令、环境变量、快捷参数
https://github.com/laomst/ccbar
```

### 2. 技术社区

#### Reddit
- r/IntelliJIDEA
- r/java
- r/programming
- r/devops

**发帖模板：**
```
Title: [Showoff] I created a plugin to quickly launch CLI tools from IDEA toolbar

Body:
Hi everyone! I just released CCBar, an IntelliJ IDEA plugin that adds configurable
buttons to the toolbar for quickly launching commands in terminal tabs.

Main features:
- Three-level configuration (CommandBar → Command → QuickParam)
- Two modes: direct command or popup menu
- Environment variable support
- Terminal or editor mode

Would love to get your feedback!

🔗 Marketplace: [link]
📦 GitHub: https://github.com/laomst/ccbar
```

#### V2EX
```
标题：发布了 IntelliJ IDEA 插件 CCBar，可在工具栏快速启动命令行工具

内容：
如题，插件主要功能是在 IDEA 工具栏添加快捷按钮，用于快速启动各种命令行工具。

主要使用场景是 AI 编程助手（Claude Code、Codex 等）的快速启动，
也适用于任何需要频繁执行的命令（如 npm 脚本、docker compose 等）。

特性：
- 三层配置结构，灵活组织命令
- 支持环境变量自动注入
- 支持编辑器模式和工具窗口模式
- 支持项目级配置，可与团队共享

欢迎大家试用和反馈！

Marketplace: [link]
GitHub: https://github.com/laomst/ccbar
```

#### 知乎
- 关注相关话题：#IntelliJ IDEA #JetBrains #开发者工具
- 回答相关问题："有哪些好用的 IDEA 插件？"
- 发布文章介绍插件

#### Hacker News
```
Title: CCBar – Quick command launcher for IntelliJ IDEA toolbar
```

### 3. 即时通讯社群

- Slack: IntelliJ Platform 相关频道
- Discord: 开发者社区
- Telegram: 开发者频道
- 微信群/QQ 群：国内开发者群

### 4. 博客文章

撰写一篇详细介绍文章，发布到：
- 个人博客
- 掘金
- 思否
- 知乎专栏
- Medium
- Dev.to

**文章大纲：**
1. 为什么开发这个插件（痛点）
2. 插件功能介绍
3. 使用教程
4. 技术实现（可选）
5. 未来规划

---

## 用户反馈收集

### 渠道管理

| 渠道 | 负责内容 | 检查频率 |
|------|----------|----------|
| GitHub Issues | Bug 报告、功能建议 | 每天 |
| Marketplace 评论 | 用户评价 | 每周 |
| 社交媒体 | 用户反馈、讨论 | 每天 |
| 邮件 | 私人反馈 | 每周 |

### 反馈响应模板

**Bug 报告响应：**
```
感谢反馈！我们会尽快调查这个问题。

请问能否提供以下信息：
1. IDEA 版本
2. 插件版本
3. 复现步骤
4. 错误日志

这有助于我们更快定位和修复问题。
```

**功能建议响应：**
```
感谢建议！这个功能听起来很有用。

我们已经将此建议记录在案，会在后续版本中考虑实现。
如果你有兴趣参与开发，欢迎提交 PR！
```

---

## 版本迭代规划

### 版本节奏建议

- **小版本** (1.0.x)：Bug 修复、小幅改进，每月 1-2 次
- **中版本** (1.x.0)：新功能、较大改进，每季度 1 次
- **大版本** (x.0.0)：重大变更、重构，按需发布

### 收集需求

定期（如每季度）向用户征集需求：
- GitHub Discussion
- 社交媒体投票
- 用户访谈

### 路线图示例

```
v1.1.0 (2026 Q2)
├─ 支持更多终端类型
├─ 改进命令预览界面
└─ 添加命令历史记录

v1.2.0 (2026 Q3)
├─ 支持命令模板变量
├─ 支持多行命令
└─ 改进配置导入导出
```

---

## 指标监控

### 关键指标

| 指标 | 说明 | 目标 |
|------|------|------|
| 下载量 | Marketplace 下载统计 | 首月 1000+ |
| 评分 | 平均星级 | 4.5+ |
| GitHub Stars | 仓库 Star 数 | 首月 100+ |
| Issue 响应时间 | 平均响应时间 | < 24 小时 |
| 活跃用户 | 周活跃/月活跃用户 | - |

### 查看数据

- **JetBrains Marketplace**: https://plugins.jetbrains.com/plugin/xxxxx-ccbar/statistics
- **GitHub**: https://github.com/laomst/ccbar/insights

---

## 常见问题 FAQ

### Q: 如何处理负面评价？
**A:** 保持专业和礼貌，感谢用户反馈，积极解决问题。如果是误解放下，耐心解释。

### Q: 收到商业合作请求怎么办？
**A:** 评估合作价值和可信度。如有兴趣，进一步沟通细节。

### Q: 如何处理大量类似 Issue？
**A:** 完善文档和 FAQ，使用 Issue 模板引导用户提供更多信息。

---

## 长期维护建议

1. **定期更新**：保持活跃，及时响应反馈
2. **文档维护**：确保文档与最新版本一致
3. **依赖升级**：跟进 IDEA 版本更新
4. **社区建设**：欢迎贡献者，建立健康的社区氛围
5. **可持续性**：避免过度承诺，合理安排开发时间

---

## 资源链接

| 资源 | 链接 |
|------|------|
| GitHub 仓库 | https://github.com/laomst/ccbar |
| Marketplace | https://plugins.jetbrains.com/plugin/xxxxx-ccbar |
| 问题反馈 | https://github.com/laomst/ccbar/issues |
| 文档 | https://github.com/laomst/ccbar/blob/main/README.md |
