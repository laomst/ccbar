# Story-11: 图标支持 HTTP/HTTPS 网络图片 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

CCBar 插件目前支持两种图标来源：
- **IDEA 内置图标**：格式为 `builtin:AllIcons.Actions.Execute`，通过反射加载 AllIcons 静态字段
- **本地文件图标**：格式为 `file:/path/to/icon.svg` 或直接文件路径，支持 SVG、PNG、JPG、GIF、BMP、ICO

图标在三个层级使用：
- **CommandBar**（工具栏按钮图标）
- **Command**（弹出菜单中的命令图标）
- **QuickParam**（快捷参数图标，当前弹出菜单未实际渲染图标）

### 1.2 用户需求

用户希望能使用网络图片（HTTP/HTTPS URL）作为图标，典型场景：
- 使用在线图标库中的图标（如 CDN 上的 SVG/PNG 图标）
- 团队共享配置时使用统一的网络图标资源
- 直接粘贴网络图片 URL 作为图标

## 2. 需求分析

### 2.1 URL 格式

支持 `http://` 和 `https://` 开头的图片 URL，例如：
- `https://example.com/icon.svg`
- `https://cdn.jsdelivr.net/gh/user/repo/icon.png`
- `http://localhost:8080/icons/my-icon.png`

### 2.2 支持的图片格式

与本地文件图标一致：SVG、PNG、JPG、GIF、BMP、ICO

### 2.3 交互行为

- 用户在设置面板的图标输入框中直接输入/粘贴 HTTP/HTTPS URL 即可
- 无需新增任何 UI 控件，现有的图标输入框已支持自由输入文本
- 图标加载后缓存，避免重复网络请求

### 2.4 加载策略

- **异步加载**：网络图片在后台线程下载，避免阻塞 UI/EDT
- **加载中**：在网络图片下载完成前，显示默认图标作为占位符
- **加载完成**：替换为实际图标，触发 UI 刷新
- **加载失败**：使用默认图标，日志记录错误信息
- **缓存机制**：下载成功后缓存到内存和本地磁盘，后续使用从缓存加载
- **超时控制**：网络请求设置合理超时时间（如 10 秒）

### 2.5 磁盘缓存

- 缓存目录：`<IDEA_SYSTEM>/ccbar/icon-cache/`
- 文件名：使用 URL 的 MD5 哈希值 + 原始扩展名
- 缓存生命周期：启动时检查磁盘缓存，若存在则直接使用；过期策略暂不实现（用户可手动清除缓存或重启后自动更新）

## 3. 技术方案

### 3.1 图标路径格式扩展

在 `CCBarIcons.loadIconInternal()` 中新增 URL 协议判断：

```kotlin
private fun loadIconInternal(iconPath: String): Icon {
    return when {
        iconPath.startsWith("builtin:") -> loadBuiltinIcon(...)
        iconPath.startsWith("http://") || iconPath.startsWith("https://") -> loadUrlIcon(iconPath)
        iconPath.startsWith("file:") -> loadFileIcon(...)
        else -> loadFileIcon(iconPath)
    }
}
```

### 3.2 网络图标加载流程

```
loadUrlIcon(url)
  ├── 检查内存缓存 → 命中 → 返回缓存图标
  ├── 检查磁盘缓存 → 命中 → 加载到内存缓存 → 返回图标
  └── 缓存未命中
        ├── 立即返回默认图标（占位）
        └── 启动后台线程下载
              ├── 下载成功 → 保存到磁盘缓存 → 更新内存缓存 → 触发 UI 刷新
              └── 下载失败 → 记录日志，保留默认图标
```

### 3.3 异步下载与 UI 刷新

- 使用 `ApplicationManager.getApplication().executeOnPooledThread {}` 执行后台下载
- 下载完成后通过 `ApplicationManager.getApplication().invokeLater {}` 在 EDT 上刷新 UI
- 刷新方式：更新工具栏 ActionGroup 的 presentation，触发 `ActionToolbar.updateActionsImmediately()`

### 3.4 持久化格式

网络图标直接存储完整 URL 字符串：
- `https://example.com/icon.svg`
- `http://cdn.example.com/icon.png`

无需特殊前缀，通过 `http://` 或 `https://` 协议头自动识别。

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarIcons.kt` | 新增 `loadUrlIcon()`、磁盘缓存逻辑、异步下载逻辑 |
| `docs/spec.md` | 更新图标处理部分，新增网络图标格式说明 |

### 4.2 不受影响的部分

- `CCBarSettings.kt`：数据模型无需修改，icon 字段已支持任意字符串
- `CCBarSettingsPanel.kt`：设置面板无需修改，图标输入框已支持自由输入文本
- `BuiltinIconSelector.kt`：内置图标选择器不受影响
- `CCBarCommandBarAction.kt`：通过 `CCBarIcons.loadIcon()` 加载图标，无需修改调用方式
- `CCBarPopupBuilder.kt`：同上

## 5. 验收标准

### 5.1 功能验收

- [ ] 在图标输入框中输入 HTTPS URL（如 `https://cdn.jsdelivr.net/gh/xxx/icon.svg`），图标正确加载显示
- [ ] 在图标输入框中输入 HTTP URL，图标正确加载显示
- [ ] 网络不通时，显示默认图标，不崩溃
- [ ] URL 返回非图片内容时，显示默认图标，不崩溃
- [ ] 图标下载完成后，工具栏按钮图标自动更新
- [ ] 重启 IDE 后，从磁盘缓存加载图标，无需重新下载
- [ ] 支持 SVG、PNG、JPG、GIF 格式的网络图片

### 5.2 UI 验收

- [ ] 网络图标加载期间显示默认图标（无空白/闪烁）
- [ ] 网络图标加载完成后平滑切换为实际图标
- [ ] 图标缩放到 16x16 标准尺寸正确显示

### 5.3 兼容性验收

- [ ] 现有 `builtin:` 格式图标不受影响
- [ ] 现有 `file:` 格式和本地路径图标不受影响
- [ ] 现有配置的导入/导出功能不受影响

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 网络延迟导致图标显示慢 | 中 | 异步加载 + 默认图标占位 + 磁盘缓存 |
| 网络不可用 | 低 | 降级使用默认图标，磁盘缓存兜底 |
| 恶意 URL | 低 | 仅下载图片数据，通过 ImageIO/IconLoader 安全解析 |
| 磁盘缓存占用空间 | 低 | 图标文件通常很小（< 100KB） |

## 7. 后续优化建议

- 缓存过期策略（如 7 天自动更新）
- 缓存管理 UI（查看/清除缓存）
- 支持 `data:` URI 格式的内联图标
