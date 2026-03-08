# CCBar 双平台发布完整指南

本文档汇总 CCBar 插件发布到 **JetBrains Marketplace** 和 **Gitee 插件市场** 的完整流程。

---

## 📋 发布平台

| 平台 | 链接 | 说明 |
|------|------|------|
| **JetBrains Marketplace** | https://plugins.jetbrains.com | 官方市场，全球用户 |
| **Gitee 插件市场** | https://gitee.com/organize/plugins | 国内市场，下载更快 |

---

## ✅ 本地准备工作（已完成）

以下本地准备工作已全部完成：

- [x] LICENSE 文件（MIT）
- [x] 插件图标（80x80，亮色/暗色）
- [x] plugin.xml 元数据（vendor、description、change-notes）
- [x] GitHub 模板文件（Issue、PR、CODEOWNERS）
- [x] GitHub Actions 工作流（build、release、sync-to-gitee）
- [x] README 徽章和文档
- [x] gradle.properties.example
- [x] .gitignore 配置
- [x] 本地构建验证通过（BUILD SUCCESSFUL）

---

## 🚀 发布操作流程

### 步骤 1：创建仓库

| 平台 | 操作 |
|------|------|
| **GitHub** | https://github.com/new → 创建 `laomst/ccbar` |
| **Gitee** | https://gitee.com/new → 创建 `laomst/ccbar` |

### 步骤 2：推送代码

```bash
# 添加 Remote（首次执行）
git remote add github https://github.com/laomst/ccbar.git
git remote add gitee https://gitee.com/laomst/ccbar.git

# 查看 remote
git remote -v

# 推送代码和标签
git push -u github main
git push -u gitee main
git push --tags
```

### 步骤 3：配置 Secrets

在 **GitHub 仓库 → Settings → Secrets and variables → Actions** 添加：

| Secret | 说明 | 获取方式 |
|--------|------|----------|
| `PUBLISH_TOKEN` | JetBrains 发布令牌 | https://plugins.jetbrains.com/author/me/tokens |
| `GITEE_SSH_PRIVATE_KEY` | Gitee SSH 私钥 | https://gitee.com/profile/ssh_keys |
| `GITEE_ACCESS_TOKEN` | Gitee API Token | https://gitee.com/personal_access_tokens |

### 步骤 4：发布到 JetBrains Marketplace

```bash
# 本地构建验证
./gradlew clean buildPlugin verifyPlugin

# 发布（使用 Gradle）
./gradlew publishPlugin -PpublishToken=your_token
```

或等待 GitHub Actions 自动发布（推送 tag 后）。

### 步骤 5：创建 Gitee Release

**方式一：手动创建**

1. 访问 https://gitee.com/laomst/ccbar/releases/new
2. 选择/创建 Tag（如 `v1.0.0`）
3. 填写发布说明
4. 上传插件包 `build/distributions/ccbar-1.0.0.zip`
5. 点击"确定发布"

**方式二：自动同步（已配置）**

推送 tag 后，GitHub Actions 会自动同步到 Gitee Release（需配置 `GITEE_ACCESS_TOKEN`）。

### 步骤 6：申请加入 Gitee 插件市场

1. 访问 https://gitee.com/organize/plugins
2. 点击"申请入驻"
3. 填写插件信息：
   - 插件名称：CCBar
   - 插件仓库：选择你的 Gitee 仓库
   - 插件分类：开发工具 → IDE 插件
   - 插件描述：中文描述（参考 `docs/marketplace-assets.md`）

### 步骤 7：等待审核

| 平台 | 审核时间 | 通知方式 |
|------|----------|----------|
| JetBrains | 1-3 工作日 | 邮件 |
| Gitee | 1-2 工作日 | 站内信/短信 |

---

## 📊 发布状态检查

### 发布后检查

- [ ] JetBrains 插件页面可访问
- [ ] Gitee Release 已创建
- [ ] 双平台版本号一致
- [ ] 下载链接正常
- [ ] README 徽章显示正确

### 查看链接

| 平台 | 链接 |
|------|------|
| GitHub | https://github.com/laomst/ccbar |
| GitHub Actions | https://github.com/laomst/ccbar/actions |
| GitHub Releases | https://github.com/laomst/ccbar/releases |
| Gitee | https://gitee.com/laomst/ccbar |
| Gitee Releases | https://gitee.com/laomst/ccbar/releases |
| JetBrains | https://plugins.jetbrains.com/plugin/xxxxx-ccbar |

---

## 📝 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 发布计划 | `docs/publish-plan.md` | 完整任务清单（45 项） |
| 双平台对照表 | `docs/DUAL-PLATFORM-PUBLISH.md` | 快速对照参考 |
| JetBrains 指南 | `docs/marketplace-publish-guide.md` | JB 市场发布步骤 |
| Gitee 指南 | `docs/gitee-publish-guide.md` | Gitee 发布步骤 |
| 快速命令 | `docs/RELEASE-COMMANDS.md` | 常用命令参考 |
| 准备总结 | `docs/PUBLISH-SUMMARY.md` | 本地准备情况 |

---

## 🎯 快速发布脚本

```bash
#!/bin/bash
# 双平台发布脚本

VERSION="1.0.0"

echo "🚀 开始发布 CCBar v${VERSION}"

# 1. 本地构建验证
echo "📦 构建和验证..."
./gradlew clean buildPlugin verifyPlugin

# 2. 提交代码
echo "📝 提交代码..."
git add -A
git commit -m "release: v${VERSION}"
git tag v${VERSION}

# 3. 推送到双平台
echo "📤 推送代码..."
git push origin main && git push origin v${VERSION}
git push gitee main && git push gitee v${VERSION}

# 4. 发布到 JetBrains
echo "🔧 发布到 JetBrains Marketplace..."
./gradlew publishPlugin -PpublishToken=${PUBLISH_TOKEN}

# 5. 提示
echo "✅ 发布完成！"
echo "📌 Gitee Release 请手动创建或等待 GitHub Actions 自动同步"
echo "📌 访问 https://gitee.com/laomst/ccbar/releases/new 创建 Release"
```

---

## ⚠️ 注意事项

1. **Token 安全**：不要将 `gradle.properties` 提交到 Git
2. **版本号一致**：保持双平台版本号一致
3. **同时发布**：建议同时发布，避免用户混淆
4. **文档语言**：Gitee 使用中文，JetBrains 可使用英文
5. **定期更新**：保持双平台同步更新

---

## 📞 获取帮助

- Issue 反馈：https://github.com/laomst/ccbar/issues
- 邮箱：laomst@163.com

---

**祝发布顺利！** 🎉
