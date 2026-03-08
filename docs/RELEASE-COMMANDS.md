# CCBar 发布快速命令参考

本文档汇总了发布 CCBar 插件所需的常用命令。

---

## 本地构建与验证

```bash
# 清理并构建插件
./gradlew clean buildPlugin

# 验证插件兼容性
./gradlew verifyPlugin

# 在沙箱 IDEA 中运行插件（手动测试）
./gradlew runIde

# 查看构建产物
ls -la build/distributions/
```

---

## 推送代码到 GitHub

```bash
# 添加 GitHub remote（首次执行）
git remote add github https://github.com/laomst/ccbar.git

# 查看所有 remote
git remote -v

# 推送 main 分支
git push -u github main

# 如果已有 origin 指向 GitHub，直接推送
git push origin main
```

---

## 发布到 JetBrains Marketplace

### 方式一：Gradle 发布（推荐）

```bash
# 临时指定 token 发布
./gradlew publishPlugin -PpublishToken=your_token_here

# 或先配置 gradle.properties（不推荐提交到 Git）
# publishToken=your_token_here
# publishChannel=stable
./gradlew publishPlugin
```

### 方式二：手动上传

1. 访问 https://plugins.jetbrains.com
2. My Plugins → 创建或选择插件
3. Upload New Version
4. 选择 `build/distributions/ccbar-1.0.0.zip`

---

## 版本发布流程

### 1. 更新版本号

```bash
# 编辑 gradle.properties
pluginVersion=1.1.0  # 修改版本号
```

### 2. 更新更新说明

编辑 `src/main/resources/META-INF/plugin.xml` 中的 `<change-notes>`。

### 3. 提交并打 Tag

```bash
# 提交变更
git add -A
git commit -m "release: v1.1.0 - 更新说明"

# 创建 Git tag
git tag v1.1.0

# 推送代码和 tag（双平台）
git push origin main && git push origin v1.1.0    # GitHub
git push gitee main && git push gitee v1.1.0      # Gitee
```

### 4. 自动发布

如果已配置 GitHub Actions，推送 tag 后会自动：
- 构建插件
- 创建 GitHub Release
- 发布到 JetBrains Marketplace

### 5. Gitee Release（手动）

Gitee 没有自动发布，需要手动创建 Release：

1. 访问 https://gitee.com/laomst/ccbar/releases/new
2. 选择/创建 Tag（如 `v1.0.0`）
3. 填写发布说明
4. 上传插件包 `build/distributions/ccbar-1.0.0.zip`
5. 点击"确定发布"

或者配置 GitHub Actions 自动同步（见 `.github/workflows/sync-to-gitee.yml`）

---

## GitHub 相关

### 配置 Secrets

在 GitHub 仓库 Settings → Secrets and variables → Actions 添加：
- `PUBLISH_TOKEN`: JetBrains Marketplace Plugin Token
- `GITEE_SSH_PRIVATE_KEY`: Gitee SSH 私钥（用于同步到 Gitee，可选）
- `GITEE_ACCESS_TOKEN`: Gitee API Token（可选）

### 查看构建状态

- Actions: https://github.com/laomst/ccbar/actions
- Releases: https://github.com/laomst/ccbar/releases

---

## Gitee 相关

### 配置 Remote

```bash
# 添加 Gitee remote
git remote add gitee https://gitee.com/laomst/ccbar.git

# 查看 remote
git remote -v

# 推送到 Gitee
git push gitee main
git push gitee --tags
```

### Gitee 链接

- 仓库：https://gitee.com/laomst/ccbar
- Releases: https://gitee.com/laomst/ccbar/releases
- 插件市场：https://gitee.com/organize/plugins

---

## 常用检查命令

```bash
# 查看当前版本号
cat gradle.properties | grep pluginVersion

# 查看插件描述
cat src/main/resources/META-INF/plugin.xml

# 查看构建产物
ls -lh build/distributions/

# 查看验证报告
cat build/reports/pluginVerifier/*/verification-report.html
```

---

## 文件结构参考

```
ccbar/
├── src/main/resources/META-INF/
│   ├── plugin.xml              # 插件描述和配置
│   ├── pluginIcon.svg          # 亮色主题图标
│   └── pluginIcon_dark.svg     # 暗色主题图标
├── build/distributions/
│   └── ccbar-1.0.0.zip         # 插件发布包
├── docs/
│   ├── publish-plan.md         # 发布计划
│   ├── marketplace-assets.md   # Marketplace 素材
│   ├── marketplace-publish-guide.md  # 发布指南
│   ├── release-checklist.md    # 发布检查清单
│   └── promotion-guide.md      # 推广指南
├── .github/
│   ├── ISSUE_TEMPLATE/         # Issue 模板
│   ├── workflows/              # GitHub Actions
│   └── PULL_REQUEST_TEMPLATE.md
├── gradle.properties           # Gradle 配置（含敏感信息勿提交）
├── gradle.properties.example   # 配置模板
└── LICENSE                     # MIT License
```

---

## 发布检查清单摘要

发布前请确认：

- [ ] 本地构建和验证通过
- [ ] 已手动测试主要功能
- [ ] 版本号已更新
- [ ] 更新说明已填写
- [ ] GitHub 仓库已创建
- [ ] 已配置 GitHub Secrets（可选，用于自动发布）
- [ ] JetBrains Plugin Token 已生成

---

## 获取帮助

- 详细发布流程：`docs/publish-plan.md`
- Marketplace 发布指南：`docs/marketplace-publish-guide.md`
- 发布前检查清单：`docs/release-checklist.md`
- 推广运营指南：`docs/promotion-guide.md`
