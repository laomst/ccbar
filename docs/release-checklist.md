# 发布前检查清单

在推送代码到 GitHub 和发布插件之前，请确认以下项目已完成。

---

## 本地检查清单

### 代码质量

- [ ] 代码已通过编译 (`./gradlew build`)
- [ ] 插件验证通过 (`./gradlew verifyPlugin`)
- [ ] 已在沙箱 IDEA 中手动测试主要功能 (`./gradlew runIde`)
- [ ] 没有未解决的编译警告
- [ ] 没有 TODO 标记的未完成功能

### 文档检查

- [ ] README.md 内容准确且完整
- [ ] LICENSE 文件存在
- [ ] 更新说明 (change-notes) 已更新
- [ ] 版本号已更新 (gradle.properties)

### 配置文件检查

- [ ] plugin.xml 中的 vendor 信息正确
- [ ] plugin.xml 中的描述完整
- [ ] 插件图标文件存在 (pluginIcon.svg, pluginIcon_dark.svg)
- [ ] gradle.properties 中的版本号正确

### Git 检查

- [ ] .gitignore 配置正确（不包含敏感文件）
- [ ] 没有提交敏感信息（token、密码等）
- [ ] 提交信息清晰、规范
- [ ] 代码已提交到本地仓库

---

## GitHub 仓库设置

### 仓库创建后配置

- [ ] 仓库设置为 Public
- [ ] 添加仓库描述
- [ ] 添加主题标签（topics）：
  - `intellij-plugin`
  - `idea-plugin`
  - `terminal`
  - `cli`
  - `productivity`

### Secrets 配置

在 GitHub 仓库 Settings → Secrets and variables → Actions 中添加：

- [ ] `PUBLISH_TOKEN` - JetBrains Marketplace Plugin Token

### Branch Protection（可选）

- [ ] 配置 main 分支保护规则
- [ ] 要求 PR 审查
- [ ] 要求 CI 通过

---

## JetBrains Marketplace 准备

### 账号准备

- [ ] 已注册 JetBrains Account
- [ ] 已生成 Plugin Token
- [ ] Token 已保存到安全位置

### 插件页面信息

- [ ] 准备好插件描述文本
- [ ] 准备好 2-5 张功能截图
- [ ] 确定插件分类标签（Tags）
- [ ] 准备好外部链接（GitHub、Issues）

---

## 发布流程

### 1. 推送到 GitHub

```bash
# 推送代码
git remote add github https://github.com/laomst/ccbar.git
git push -u github main
```

### 2. 触发首次构建

- 推送后 GitHub Actions 会自动运行构建
- 检查 Actions 页面确认构建成功
- 下载构建产物验证

### 3. 发布到 JetBrains Marketplace

**方式一：使用 Gradle 发布**
```bash
./gradlew publishPlugin -PpublishToken=your_token
```

**方式二：手动上传**
1. 访问 https://plugins.jetbrains.com
2. 创建新插件页面
3. 上传 `build/distributions/*.zip`
4. 填写插件信息、上传截图
5. 提交审核

### 4. 创建 Git Tag（发布版本时）

```bash
# 更新版本号后
git tag v1.0.0
git push origin v1.0.0
# 或
git push github v1.0.0
```

---

## 发布后检查

- [ ] GitHub Release 页面显示新版本
- [ ] JetBrains Marketplace 插件页面可访问
- [ ] 下载链接正常工作
- [ ] README 中的徽章链接正确
- [ ] 在社交媒体/社区分享发布消息

---

## 联系支持

如有问题，请通过以下方式联系：

- GitHub Issues: https://github.com/laomst/ccbar/issues
- Email: laomst@163.com
