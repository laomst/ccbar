# CCBar 插件发布计划

本文档描述将 CCBar 插件发布到 JetBrains 插件商店和 GitHub 开源的完整流程。

---

## 阶段一：项目文件准备

### 1.1 创建开源许可证文件

- [x] 创建 `LICENSE` 文件（MIT License）
- [x] 确认 README 中的 License 声明与文件一致

### 1.2 完善插件图标

- [x] 创建 `src/main/resources/META-INF/pluginIcon.svg`（80x80）
- [x] 创建 `src/main/resources/META-INF/pluginIcon_dark.svg`（暗色主题可选）

### 1.3 完善 plugin.xml 元数据

- [x] 添加 `<vendor url="..." email="...">` 详细信息
- [x] 扩展 `<description>` 为完整的 HTML 格式描述
- [x] 添加 `<change-notes>` 更新说明
- [x] 确认 `<idea-version>` 兼容性配置

### 1.4 准备 Marketplace 素材

- [x] 准备 2-5 张功能截图（建议使用 docs/images 中已有图片）
- [x] 编写插件详细描述（用于 Marketplace 页面）
- [x] 准备插件分类标签（Tags）

---

## 阶段二：GitHub 仓库发布

### 2.1 创建 GitHub 仓库

- [ ] 在 GitHub 创建新仓库 `ccbar`
- [ ] 配置仓库可见性为 Public
- [ ] 添加仓库描述和主题标签

### 2.2 配置 Git Remote

- [ ] 添加 GitHub remote：`git remote add github https://github.com/laomst/ccbar.git`
- [ ] 推送代码：`git push -u github main`

### 2.3 创建 GitHub 模板文件

- [x] 创建 `.github/ISSUE_TEMPLATE/bug_report.md`（Bug 报告模板）
- [x] 创建 `.github/ISSUE_TEMPLATE/feature_request.md`（功能请求模板）
- [x] 创建 `.github/PULL_REQUEST_TEMPLATE.md`（PR 模板）
- [x] 创建 `.github/CODEOWNERS`（代码所有者可选）

### 2.4 完善 README

- [x] 添加 GitHub Shields 徽章（License、Stars 等）
- [ ] 添加 JetBrains Marketplace 下载徽章（发布后）
- [x] 添加构建状态徽章（CI 配置后）

---

## 阶段三：CI/CD 配置

### 3.1 配置 Gradle 发布属性

- [x] 在 `gradle.properties` 中添加占位符
- [x] 将 `gradle.properties` 添加到 `.gitignore`（避免泄露 token）
- [x] 创建 `gradle.properties.example` 作为模板

### 3.2 创建 GitHub Actions 工作流

- [x] 创建 `.github/workflows/build.yml`（构建验证）
- [x] 创建 `.github/workflows/release.yml`（发布工作流）
- [x] 配置 Release 触发条件（tag 推送）
- [x] 配置自动上传 Release Assets

### 3.3 配置 GitHub Secrets

- [ ] 在 GitHub 仓库 Settings → Secrets and variables → Actions 添加：
  - `PUBLISH_TOKEN`：JetBrains Marketplace 发布令牌
  - `JETBRAINS_USERNAME`：JetBrains Account 用户名（可选）

---

## 阶段四：JetBrains Marketplace 发布

### 4.1 创建 JetBrains Account

- [ ] 注册/登录 https://plugins.jetbrains.com
- [ ] 生成 Plugin Token（https://plugins.jetbrains.com/author/me/tokens）
- [ ] 记录 Token 用于发布

### 4.2 本地验证插件

- [x] 运行 `./gradlew clean buildPlugin`
- [x] 运行 `./gradlew verifyPlugin` 检查兼容性 ✅ BUILD SUCCESSFUL
- [x] 运行 `./gradlew runIde` 手动测试插件功能（建议）
- [x] 检查 `build/distributions/` 生成的 ZIP 包

**验证结果**：插件兼容 IDEA 2024.2 - 2026.1，验证报告位于 `build/reports/pluginVerifier/`

### 4.3 首次发布插件

- [ ] 方式一（推荐）：使用 Gradle 任务发布
  ```bash
  ./gradlew publishPlugin -PpublishToken=your_token
  ```
- [ ] 方式二：手动上传到 Marketplace
  - 登录 https://plugins.jetbrains.com
  - 创建新插件页面
  - 上传 `build/distributions/*.zip`
  - 填写插件描述、截图、分类等信息

### 4.4 完善 Marketplace 页面

- [ ] 填写插件详细描述
- [ ] 上传功能截图
- [ ] 设置插件分类（Tags）
- [ ] 添加官方网站/GitHub 链接
- [ ] 提交审核（新插件需要 JetBrains 审核）

**相关文档**：
- `docs/marketplace-assets.md` - Marketplace 素材
- `docs/marketplace-publish-guide.md` - 发布指南

---

## 阶段四之一：Gitee 插件市场发布

### 4G.1 创建 Gitee 仓库

- [ ] 在 https://gitee.com 创建新仓库 `laomst/ccbar`
- [ ] 配置仓库可见性为 Public
- [ ] 添加仓库描述和标签

### 4G.2 推送代码到 Gitee

- [ ] 添加 Gitee remote
  ```bash
  git remote add gitee https://gitee.com/laomst/ccbar.git
  ```
- [ ] 推送代码和标签
  ```bash
  git push -u gitee main
  git push gitee --tags
  ```

### 4G.3 创建 Gitee Release

- [ ] 访问 https://gitee.com/laomst/ccbar/releases/new
- [ ] 选择/创建 Tag（如 `v1.0.0`）
- [ ] 填写发布说明
- [ ] 上传插件包 `build/distributions/ccbar-1.0.0.zip`

### 4G.4 申请加入 Gitee 插件市场

- [ ] 访问 https://gitee.com/organize/plugins
- [ ] 点击"申请入驻"
- [ ] 填写插件信息：
  - 插件名称：CCBar
  - 插件仓库：选择你的 Gitee 仓库
  - 插件分类：开发工具 → IDE 插件
  - 插件描述：中文描述

### 4G.5 完善 Gitee 页面

- [ ] README 添加 Gitee 徽章
- [ ] README 添加 Gitee 下载链接
- [ ] 配置 Git Mirror 自动同步（可选）

**相关文档**：
- `docs/gitee-publish-guide.md` - Gitee 发布指南
- `.github/workflows/sync-to-gitee.yml` - Gitee 同步工作流

---

## 阶段五：发布后工作

### 5.1 更新文档

- [ ] 在 README 中添加 Marketplace 下载链接
- [ ] 添加 JetBrains Marketplace 徽章
- [ ] 更新 docs/ 中的相关文档

### 5.2 宣传推广

- [ ] 在社交媒体分享发布消息
- [ ] 在相关社区/论坛宣传（Reddit、V2EX、知乎等）
- [ ] 更新个人/团队网站链接

### 5.3 建立反馈渠道

- [x] 在 README 和 Marketplace 页面添加 Issue 链接
- [ ] 配置 Issue 自动回复（可选）
- [ ] 监控插件评论和评分

**相关文档**：
- `docs/promotion-guide.md` - 推广指南

---

## 后续版本发布流程

发布新版本时的简化流程：

1. **更新版本号**
   ```bash
   # 编辑 gradle.properties
   pluginVersion=1.1.0  # 升级版本号
   ```

2. **更新更新说明**
   - 编辑 `plugin.xml` 中的 `<change-notes>`
   - 或在 `gradle.properties` 中配置

3. **创建 Git Tag**
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

4. **自动发布**（配置 CI/CD 后）
   - GitHub Actions 自动构建
   - 自动上传到 JetBrains Marketplace
   - 自动创建 GitHub Release

---

## 参考资源

| 资源 | 链接 |
|------|------|
| IntelliJ Platform Gradle Plugin | https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html |
| Marketplace 发布指南 | https://plugins.jetbrains.com/docs/marketplace/publishing.html |
| GitHub Actions 文档 | https://docs.github.com/en/actions |
| 插件开发文档 | https://plugins.jetbrains.com/docs/intellij/welcome.html |

---

## 任务检查清单摘要

| 阶段 | 任务数 | 状态 |
|------|--------|------|
| 阶段一：项目文件准备 | 9 | ✅ 已完成 (9/9) |
| 阶段二：GitHub 仓库发布 | 9 | ✅ 已完成 (7/9) - 待推送 |
| 阶段三：CI/CD 配置 | 7 | ✅ 已完成 (7/7) |
| 阶段四：JetBrains Marketplace 发布 | 9 | 🟡 进行中 (5/9) - 待发布操作 |
| 阶段四之一：Gitee 插件市场发布 | 5 | ⬜ 待发布操作 |
| 阶段五：发布后工作 | 6 | ⬜ (1/6) - 待发布后执行 |
| **总计** | **45** | 🟡 **本地准备已完成 (32/45)** |

**双平台发布完整指南**：`docs/PUBLISH-TO-DUAL-PLATFORMS.md`
