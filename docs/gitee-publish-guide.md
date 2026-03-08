# Gitee 插件市场发布指南

本文档描述如何将 CCBar 插件发布到 Gitee (码云) 插件市场。

---

## Gitee 与 JetBrains Marketplace 的区别

| 特性 | JetBrains Marketplace | Gitee 插件市场 |
|------|----------------------|---------------|
| 官方网站 | https://plugins.jetbrains.com | https://gitee.com |
| 审核方式 | 官方审核 (1-3 工作日) | 相对宽松 |
| 发布方式 | Gradle/手动上传 | 手动上传 |
| 更新方式 | Gradle/Git Release | Git Release |
| 覆盖范围 | 全球用户 | 中国用户为主 |
| 下载速度 | 国际服务器 | 国内服务器 |

---

## 发布方式

Gitee 插件市场**没有官方 Gradle 发布插件**，主要通过以下方式发布：

### 方式一：通过 Gitee 仓库 Release 发布（推荐）

这是最简单的方式，利用 GitHub Actions 的变体配置 Gitee 同步。

#### 步骤 1：在 Gitee 创建仓库

1. 访问 https://gitee.com
2. 点击 "+" → "新建仓库"
3. 仓库名：`ccbar`
4.  visibility：公开
5. 初始化选项：不勾选（我们推送现有代码）

#### 步骤 2：推送代码到 Gitee

```bash
# 添加 Gitee remote
git remote add gitee https://gitee.com/laomst/ccbar.git

# 推送到 Gitee
git push -u gitee main

# 推送所有分支和 tags
git push gitee --all
git push gitee --tags
```

#### 步骤 3：创建 Gitee Release

1. 进入仓库 → "发布" → "创建发布"
2. 选择/创建 Tag（如 `v1.0.0`）
3. 填写发布说明
4. 上传插件包：`build/distributions/ccbar-1.0.0.zip`
5. 点击"确定发布"

#### 步骤 4：申请加入 Gitee 插件市场

1. 访问 https://gitee.com/organize/plugins
2. 点击"申请入驻"
3. 填写插件信息：
   - 插件名称：CCBar
   - 插件仓库：选择你的 Gitee 仓库
   - 插件分类：开发工具
   - 插件描述：参考 `docs/marketplace-assets.md`

---

### 方式二：配置 Gitee 同步 GitHub Release

如果你使用 GitHub 作为主仓库，可以配置自动同步：

#### 在 GitHub Actions 中添加 Gitee 同步

创建 `.github/workflows/sync-to-gitee.yml`：

```yaml
name: Sync to Gitee

on:
  push:
    tags:
      - 'v*'

jobs:
  sync-to-gitee:
    runs-on: ubuntu-latest
    steps:
      - name: Mirror to Gitee
        uses: wearerequired/git-mirror-action@v1
        env:
          SSH_PRIVATE_KEY: ${{ secrets.GITEE_SSH_PRIVATE_KEY }}
        with:
          source-repo: git@github.com:laomst/ccbar.git
          destination-repo: git@gitee.com:laomst/ccbar.git

      - name: Create Gitee Release
        uses: ncipollo/release-action@v1
        with:
          tag: ${{ github.ref_name }}
          token: ${{ secrets.GITEE_ACCESS_TOKEN }}
          owner: laomst
          repo: ccbar
          bodyFile: CHANGELOG.md
```

#### 配置 Gitee Secrets

在 GitHub 仓库 Settings → Secrets and variables → Actions 添加：

| Secret | 说明 |
|--------|------|
| `GITEE_SSH_PRIVATE_KEY` | Gitee SSH 私钥 |
| `GITEE_ACCESS_TOKEN` | Gitee API Token |

---

## Gitee 插件市场入驻要求

### 基本条件

- [ ] 插件已开源在 Gitee
- [ ] 有完整的 README 文档（中文）
- [ ] 有 LICENSE 文件
- [ ] 至少有 1 个 Release 版本
- [ ] 代码无敏感信息

### 插件信息要求

| 字段 | 要求 |
|------|------|
| 插件名称 | 简洁明了，如 "CCBar" |
| 插件分类 | 开发工具 → IDE 插件 |
| 插件图标 | 建议提供（同 JetBrains） |
| 插件截图 | 2-5 张（同 JetBrains） |
| 插件描述 | 中文描述，突出核心功能 |
| 使用文档 |  README.md 中包含使用说明 |

---

## README.md Gitee 优化建议

### 添加 Gitee 徽章

```markdown
[![Gitee Repo](https://gitee.com/laomst/ccbar/badge/star.svg?theme=dark)](https://gitee.com/laomst/ccbar)
[![Gitee Release](https://img.shields.io/github/release/laomst/ccbar.svg)](https://gitee.com/laomst/ccbar/releases)
```

### 添加 Gitee 下载链接

在 README 中添加：

```markdown
## 下载

- **JetBrains Marketplace**: [下载](https://plugins.jetbrains.com/plugin/xxxxx-ccbar)
- **Gitee 插件市场**: [下载](https://gitee.com/laomst/ccbar/releases)
```

---

## 双平台发布流程

### 首次发布

```bash
# 1. 本地构建
./gradlew clean buildPlugin

# 2. 验证插件
./gradlew verifyPlugin

# 3. 提交代码
git add -A
git commit -m "release: v1.0.0"
git tag v1.0.0

# 4. 推送到 GitHub
git push origin main
git push origin v1.0.0

# 5. 推送到 Gitee
git push gitee main
git push gitee v1.0.0

# 6. 发布到 JetBrains Marketplace
./gradlew publishPlugin -PpublishToken=your_token

# 7. 在 Gitee 创建 Release
# 访问 https://gitee.com/laomst/ccbar/releases/new
# 上传 build/distributions/ccbar-1.0.0.zip
```

### 后续更新

```bash
# 更新版本号
# 编辑 gradle.properties → pluginVersion=1.1.0

# 提交并发布
git add -A
git commit -m "release: v1.1.0 - 更新说明"
git tag v1.1.0

# 推送（双平台）
git push origin main && git push origin v1.1.0
git push gitee main && git push gitee v1.1.0

# 发布到 JetBrains Marketplace
./gradlew publishPlugin -PpublishToken=your_token

# Gitee Release 手动创建或使用自动同步
```

---

## 配置双 Remote 管理

### 查看当前 Remote

```bash
git remote -v
```

### 期望输出

```
origin  https://github.com/laomst/ccbar.git (fetch)
origin  https://github.com/laomst/ccbar.git (push)
gitee   https://gitee.com/laomst/ccbar.git (fetch)
gitee   https://gitee.com/laomst/ccbar.git (push)
```

### 一键推送到双平台

在 `~/.gitconfig` 添加别名：

```ini
[alias]
    push-all = !git push origin && git push gitee
    push-all-tags = !git push origin --tags && git push gitee --tags
```

使用：
```bash
git push-all
git push-all-tags
```

---

## 注意事项

### 1. 网络问题
- Gitee 服务器在国内，访问速度快
- 建议使用 HTTPS 而非 SSH（SSH 有时需要额外配置）

### 2. 审核时间
- Gitee 插件市场审核通常 1-2 个工作日
- 比 JetBrains Marketplace 略快

### 3. 版本同步
- 建议保持双平台版本号一致
- 同时发布，避免用户混淆

### 4. 文档语言
- Gitee 建议使用中文文档
- JetBrains Marketplace 可使用英文或中英双语

### 5. 敏感内容
- 确保代码中无敏感信息（API Key、Token 等）
- `.gitignore` 已配置排除 `gradle.properties`

---

## 相关资源

| 资源 | 链接 |
|------|------|
| Gitee 插件市场 | https://gitee.com/organize/plugins |
| Gitee Releases | https://gitee.com/help/categories/35 |
| Git Mirror Action | https://github.com/wearerequired/git-mirror-action |
| Release Action | https://github.com/ncipollo/release-action |

---

## Gitee 发布检查清单

- [ ] 在 Gitee 创建仓库
- [ ] 推送代码到 Gitee
- [ ] 创建 Gitee Release 并上传插件包
- [ ] 申请加入 Gitee 插件市场
- [ ] README 添加 Gitee 徽章和下载链接
- [ ] 配置双 Remote（可选）
- [ ] 配置自动同步（可选）
- [ ] 提交插件市场入驻申请
