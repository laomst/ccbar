# JetBrains Marketplace 发布指南

本文档描述如何将 CCBar 插件发布到 JetBrains Marketplace。

---

## 准备工作

### 1. 创建 JetBrains Account

1. 访问 https://plugins.jetbrains.com
2. 点击右上角 **Log In**
3. 使用 JetBrains Account 登录（没有则先注册）

### 2. 生成 Plugin Token

1. 登录后访问 https://plugins.jetbrains.com/author/me/tokens
2. 点击 **Generate New Token**
3. 输入描述（如：CCBar Publish）
4. 选择权限：**Upload Plugin**（必需）
5. 复制 Token 并安全保存（只会显示一次）

---

## 方式一：使用 Gradle 发布（推荐）

### 配置发布 Token

**临时使用（推荐）：**
```bash
./gradlew publishPlugin -PpublishToken=your_token_here
```

**配置文件（仅限本地安全环境）：**
```properties
# gradle.properties（不要提交到 Git）
publishToken=your_token_here
publishChannel=stable
```

### 执行发布

```bash
# 确保已构建插件
./gradlew buildPlugin

# 发布到 Marketplace
./gradlew publishPlugin
```

发布成功后会输出插件页面 URL。

---

## 方式二：手动上传到 Marketplace

### 步骤 1：创建插件页面

1. 访问 https://plugins.jetbrains.com
2. 点击右上角用户名 → **My Plugins**
3. 点击 **Create Plugin**
4. 填写插件信息：

| 字段 | 填写内容 |
|------|----------|
| **Name** | CCBar |
| **Description** | 使用 `docs/marketplace-assets.md` 中的插件描述 |
| **Category** | Tools → Productivity |
| **Website** | https://github.com/laomst/ccbar |
| **Bug Tracker** | https://github.com/laomst/ccbar/issues |
| **Source Code** | https://github.com/laomst/ccbar |
| **Tags** | terminal, command, launcher, ai, coding-agent, claude-code, productivity, toolbar |

### 步骤 2：上传插件包

1. 创建插件页面后，进入插件管理页面
2. 点击 **Upload New Version** 或 **Upload Plugin**
3. 选择文件：`build/distributions/ccbar-1.0.0.zip`
4. 填写版本信息：
   - **Version**: 1.0.0
   - **Change Notes**: 使用 `docs/marketplace-assets.md` 中的更新说明
5. 上传功能截图（2-5 张）：
   - `docs/images/ccbar 整体效果.png`
   - `docs/images/ccbar 命令菜单.png`
   - `docs/images/命令预览对话框.png`
   - `docs/images/ccbarSettings.png`

### 步骤 3：提交审核

1. 确认所有信息填写完整
2. 点击 **Submit for Review**
3. 等待 JetBrains 团队审核（通常 1-3 个工作日）

审核通过后插件会出现在 Marketplace 中。

---

## 配置 Marketplace 页面

### 插件描述模板

```html
<h2>CCBar - Quick Command Launcher for IDEA Toolbar</h2>
<p>CCBar 是一个 IntelliJ IDEA 插件，在工具栏中添加可配置的快捷按钮，用于在终端标签页中快速启动命令。</p>

<h3>核心特性</h3>
<ul>
    <li><strong>三层配置结构</strong>：CommandBar → Command → QuickParam</li>
    <li><strong>两种工作模式</strong>：直接命令/命令列表</li>
    <li><strong>环境变量支持</strong>：自动适配不同 Shell</li>
    <li><strong>终端模式可选</strong>：工具窗口/编辑器模式</li>
    <li><strong>自定义图标</strong>：内置/本地/网络图片</li>
</ul>

<h3>快速开始</h3>
<ol>
    <li>安装后工具栏自动出现默认按钮</li>
    <li>点击按钮选择命令执行</li>
    <li>进入 <strong>Settings → Tools → CCBar</strong> 自定义配置</li>
</ol>
```

### 推荐标签（Tags）

```
terminal
command
launcher
ai
coding-agent
claude-code
codex
productivity
toolbar
quick-access
cli
```

### 截图上传顺序

1. **整体效果** - `ccbar 整体效果.png`（展示工具栏按钮）
2. **命令菜单** - `ccbar 命令菜单.png`（展示弹出菜单）
3. **参数配置** - `命令预览对话框.png`（展示配置界面）
4. **设置界面** - `ccbarSettings.png`（展示完整设置）

---

## 版本更新流程

### 1. 更新版本号

```properties
# gradle.properties
pluginVersion=1.1.0  # 升级版本号
```

### 2. 更新更新说明

编辑 `src/main/resources/META-INF/plugin.xml` 中的 `<change-notes>`。

### 3. 构建并发布

```bash
./gradlew clean buildPlugin
./gradlew publishPlugin -PpublishToken=your_token
```

### 4. 创建 Git Tag

```bash
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions 会自动发布（如已配置）。

---

## 常见问题

### Q: 发布失败 "Invalid token"
**A:** 检查 Token 是否正确复制，确保没有多余空格。Token 可以在 https://plugins.jetbrains.com/author/me/tokens 重新生成。

### Q: 审核需要多长时间？
**A:** 通常 1-3 个工作日。首次发布可能需要更长时间。

### Q: 如何查看审核状态？
**A:** 登录 https://plugins.jetbrains.com → My Plugins → 查看插件状态。

### Q: 可以同时支持多个 IDEA 版本吗？
**A:** 是的，在 `build.gradle.kts` 中配置 `sinceBuild` 和 `untilBuild`。当前配置为 242+（IDEA 2024.2+）。

---

## 参考链接

| 资源 | 链接 |
|------|------|
| Marketplace 发布指南 | https://plugins.jetbrains.com/docs/marketplace/publishing.html |
| 插件上传 | https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html |
| IntelliJ Platform Gradle Plugin | https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html |
