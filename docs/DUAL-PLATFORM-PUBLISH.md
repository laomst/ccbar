# 双平台发布快速对照表

本文档对比 JetBrains Marketplace 和 Gitee 插件市场的发布流程。

---

## 发布平台对比

| 特性 | JetBrains Marketplace | Gitee 插件市场 |
|------|----------------------|---------------|
| **官网** | https://plugins.jetbrains.com | https://gitee.com/organize/plugins |
| **受众** | 全球用户 | 中国用户为主 |
| **下载速度** | 国际服务器 | 国内服务器（更快） |
| **审核时间** | 1-3 工作日 | 1-2 工作日 |
| **发布方式** | Gradle / 手动上传 | 手动上传 |
| **自动发布** | ✅ 支持（Gradle/GitHub Actions） | ⚠️ 有限支持（需配置同步） |
| **API/CLI** | ✅ Gradle Plugin | ❌ 无官方支持 |

---

## 发布前准备对比

| 准备工作 | JetBrains | Gitee | 状态 |
|----------|-----------|-------|------|
| LICENSE 文件 | 建议 | 必需 | ✅ 已完成 |
| README 文档 | 英文/中文 | 中文 | ✅ 已完成 |
| 插件图标 | 80x80 SVG | 可选 | ✅ 已完成 |
| 功能截图 | 2-5 张 | 2-5 张 | ✅ 已完成 |
| 插件描述 | HTML 格式 | Markdown | ✅ 已完成 |
| 分类标签 | 必需 | 必需 | ✅ 已完成 |

---

## 发布流程对比

### JetBrains Marketplace

| 步骤 | 操作 | 命令/链接 |
|------|------|-----------|
| 1. 账号 | 注册/登录 | https://plugins.jetbrains.com |
| 2. Token | 生成 Plugin Token | https://plugins.jetbrains.com/author/me/tokens |
| 3. 构建 | 本地构建验证 | `./gradlew buildPlugin verifyPlugin` |
| 4. 发布 | Gradle 发布或手动上传 | `./gradlew publishPlugin` |
| 5. 审核 | 等待官方审核 | 1-3 工作日 |
| 6. 上线 | 审核通过后自动上线 | - |

### Gitee 插件市场

| 步骤 | 操作 | 命令/链接 |
|------|------|-----------|
| 1. 账号 | 注册/登录 Gitee | https://gitee.com |
| 2. 仓库 | 创建公开仓库 | https://gitee.com/new |
| 3. 推送 | 推送代码到 Gitee | `git push -u gitee main` |
| 4. Release | 创建发布并上传 ZIP | https://gitee.com/laomst/ccbar/releases |
| 5. 申请 | 申请加入插件市场 | https://gitee.com/organize/plugins |
| 6. 审核 | 等待官方审核 | 1-2 工作日 |
| 7. 上线 | 审核通过后上线 | - |

---

##  Secrets 配置对比

### GitHub Actions Secrets

| Secret | 用途 | 平台 |
|--------|------|------|
| `PUBLISH_TOKEN` | JetBrains 发布令牌 | JetBrains |
| `GITEE_SSH_PRIVATE_KEY` | Gitee SSH 认证 | Gitee 同步 |
| `GITEE_ACCESS_TOKEN` | Gitee API 访问 | Gitee Release |

### 获取方式

| Token | 获取链接 |
|-------|----------|
| JetBrains Publish Token | https://plugins.jetbrains.com/author/me/tokens |
| Gitee SSH Key | https://gitee.com/profile/ssh_keys |
| Gitee Access Token | https://gitee.com/personal_access_tokens |

---

## 双平台发布命令速查

### 首次发布

```bash
# 1. 本地构建和验证
./gradlew clean buildPlugin verifyPlugin

# 2. 提交代码
git add -A
git commit -m "release: v1.0.0"
git tag v1.0.0

# 3. 推送到双平台
git push origin main && git push origin v1.0.0    # GitHub
git push gitee main && git push gitee v1.0.0      # Gitee

# 4. 发布到 JetBrains Marketplace
./gradlew publishPlugin -PpublishToken=your_token

# 5. Gitee Release 手动创建
# 访问 https://gitee.com/laomst/ccbar/releases/new
```

### 后续更新

```bash
# 更新版本号后
git add -A
git commit -m "release: v1.1.0 - 更新说明"
git tag v1.1.0

# 推送（双平台）
git push origin main && git push origin v1.1.0
git push gitee main && git push gitee v1.1.0

# 发布到 JetBrains
./gradlew publishPlugin -PpublishToken=your_token

# Gitee Release 手动或使用自动同步
```

---

## README 徽章对比

### JetBrains Marketplace

```markdown
[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-blue.svg)](https://plugins.jetbrains.com/plugin/xxxxx-ccbar)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/xxxxx)](https://plugins.jetbrains.com/plugin/xxxxx-ccbar)
```

### Gitee

```markdown
[![Gitee Repo](https://gitee.com/laomst/ccbar/badge/star.svg?theme=dark)](https://gitee.com/laomst/ccbar)
[![Gitee Release](https://img.shields.io/github/release/laomst/ccbar.svg)](https://gitee.com/laomst/ccbar/releases)
```

---

## 审核注意事项

### JetBrains Marketplace

- **审核严格**：会检查 API 使用、兼容性、描述质量
- **审核反馈**：通过邮件通知
- **修改重提**：如有问题需要修改后重新上传

### Gitee 插件市场

- **审核相对宽松**：主要检查合规性
- **审核反馈**：通过站内信或短信
- **中文要求**：建议使用中文文档和描述

---

## 推荐发布策略

### 方案 A：同步发布（推荐）

1. 先发布到 JetBrains Marketplace（全球用户）
2. 同步发布到 Gitee（国内用户）
3. 保持版本号一致
4. GitHub 作为代码主仓库

**优点**：
- 覆盖全球和国内用户
- 国内用户下载更快
- 分散风险，不依赖单一平台

### 方案 B：仅 JetBrains

- 专注 JetBrains 官方市场
- 减少维护成本
- 通过 GitHub Releases 提供备选下载

### 方案 C：仅 Gitee

- 仅面向国内用户
- 简化发布流程
- 依赖单一市场

---

## 相关文档

| 文档 | 路径 |
|------|------|
| JetBrains 发布指南 | `docs/marketplace-publish-guide.md` |
| Gitee 发布指南 | `docs/gitee-publish-guide.md` |
| 发布计划 | `docs/publish-plan.md` |
| 快速命令 | `docs/RELEASE-COMMANDS.md` |
| 发布总结 | `docs/PUBLISH-SUMMARY.md` |

---

## 检查清单

### 双平台发布前检查

- [ ] JetBrains Marketplace 账号已注册
- [ ] JetBrains Plugin Token 已生成
- [ ] Gitee 账号已注册
- [ ] Gitee 仓库已创建
- [ ] GitHub Secrets 已配置
- [ ] 本地构建验证通过
- [ ] README 已包含双平台下载链接

### 发布后检查

- [ ] JetBrains 插件页面可访问
- [ ] Gitee Release 已创建
- [ ] 双平台版本号一致
- [ ] 下载链接正常
- [ ] 徽章显示正确
