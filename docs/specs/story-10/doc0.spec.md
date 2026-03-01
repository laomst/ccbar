# Story-10: CommandBar / Command / QuickParam 禁用功能 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前三层配置（CommandBar → Command → QuickParam）只能通过删除来移除不需要的项目。用户如果暂时不需要某个配置项，只能删除后重新创建，无法保留数据。

相关代码文件：
- `CCBarSettings.kt` — 数据模型（`CommandBarConfig`、`CommandConfig`、`QuickParamConfig`）
- `CCBarToolbarActionGroup.kt` — 工具栏 ActionGroup，动态生成工具栏按钮
- `CCBarCommandBarAction.kt` — 单个 CommandBar 的 Action
- `CCBarPopupBuilder.kt` — 弹出菜单构建器
- `CCBarSettingsPanel.kt` — 设置面板
- `QuickParamEditDialog.kt` — 快捷参数编辑对话框

### 1.2 用户需求

1. 支持禁用 CommandBar、Command 和 QuickParam，禁用后数据保留但不在工具栏/弹出菜单中展示
2. 设置面板中禁用项以灰色文字显示，方便区分启用/禁用状态
3. 重新启用后恢复正常展示
4. 禁用项跳过必填字段验证，降低配置维护成本

---

## 2. 需求分析

### 2.1 数据模型变更

在三个 data class 中各添加 `enabled` 字段：

```kotlin
data class CommandBarConfig(
    // ... 现有字段 ...
    var enabled: Boolean = true
)

data class CommandConfig(
    // ... 现有字段 ...
    var enabled: Boolean = true
)

data class QuickParamConfig(
    // ... 现有字段 ...
    var enabled: Boolean = true
)
```

默认值为 `true`，确保已有配置向后兼容。

### 2.2 展示层过滤规则

| 层级 | 过滤规则 | 说明 |
|------|----------|------|
| CommandBar | `enabled = false` 的 CommandBar 不生成工具栏按钮 | 工具栏不显示该按钮 |
| Command | `enabled = false` 的普通 Command 不出现在弹出菜单中 | 分割线不受 enabled 影响 |
| QuickParam | `enabled = false` 的 QuickParam 不出现在弹出菜单中 | 无启用的 QuickParam 时不显示快捷参数行 |

### 2.3 交互行为

#### 2.3.1 工具栏按钮

| 状态 | 行为 |
|------|------|
| CommandBar 已禁用 | 工具栏不显示该按钮 |
| CommandBar 已启用但所有 Command 已禁用 | 按钮灰显（不可点击） |

#### 2.3.2 弹出菜单

| 状态 | 行为 |
|------|------|
| Command 已禁用 | 弹出菜单中不显示该命令行 |
| QuickParam 已禁用 | 弹出菜单中不显示该快捷参数 |
| 所有 QuickParam 已禁用 | 不显示快捷参数行（第二行） |

#### 2.3.3 设置面板

| 交互 | 行为 |
|------|------|
| 勾选/取消"启用"复选框 | 实时更新数据模型，列表渲染器即时刷新显示状态 |
| 禁用项在列表中 | 文字显示为灰色（`JBColor.GRAY`），选中时恢复正常前景色 |
| 分割线类型 | 不显示启用复选框（分割线始终显示） |

### 2.4 视觉规范

#### 2.4.1 列表渲染器（禁用状态）

- **文字颜色**：`JBColor.GRAY`（仅非选中状态）
- **选中状态**：使用系统默认选中前景色（不变灰）
- **图标**：保持不变

#### 2.4.2 启用复选框

- **位置**：详情面板名称输入框的右侧，与名称同一行
- **文字**："启用"
- **默认值**：勾选（`true`）

---

## 3. 技术方案

### 3.1 数据模型

三个 data class 各增加 `var enabled: Boolean = true`，`deepCopy()` 方法同步复制该字段。

### 3.2 展示层过滤

#### CCBarToolbarActionGroup

```kotlin
// getEffectiveCommandBars() 返回结果增加过滤
return commandBars.filter { it.enabled }
```

#### CCBarCommandBarAction

```kotlin
// update() 中按钮启用条件
e.presentation.isEnabled = commandBarConfig.command.isNotBlank()
    || commandBarConfig.commands.any { it.enabled && it.isCommand() }
```

#### CCBarPopupBuilder

```kotlin
// 遍历 commands 时跳过禁用的普通 Command
for (option in commandBarConfig.commands) {
    if (option.isSeparator()) { /* 分割线正常显示 */ }
    else if (!option.enabled) continue
    else { /* 正常渲染 */ }
}

// 快捷参数过滤
val enabledQuickParams = option.quickParams.filter { it.enabled }
if (enabledQuickParams.isNotEmpty()) { /* 渲染第二行 */ }

// calculateMaxLabelWidth / calculateMaxPreviewWidth 同步过滤
```

### 3.3 设置面板

#### CommandBar 详情

在名称输入框右侧添加 `JCheckBox("启用")`：

```kotlin
// CommandBar 名称行：名称输入框 + 启用复选框
namePanel.add(JLabel("名称:"), BorderLayout.WEST)
namePanel.add(buttonNameField, BorderLayout.CENTER)
namePanel.add(buttonEnabledCheckbox, BorderLayout.EAST)
```

#### Command 详情

同理在名称输入框右侧添加 `JCheckBox("启用")`，分割线类型时隐藏该复选框。

#### QuickParam 编辑对话框

表格新增第三列"启用"（`Boolean` 类型，渲染为 `JCheckBox`）：

```kotlin
private val tableModel = object : DefaultTableModel(arrayOf("名称", "参数", "启用"), 0) {
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return if (columnIndex == 2) java.lang.Boolean::class.java else String::class.java
    }
}
```

新增行默认 `enabled = true`。

#### 列表渲染器

```kotlin
// ButtonListCellRenderer / OptionListCellRenderer
if (!value.enabled && !isSelected) {
    foreground = JBColor.GRAY
}
```

### 3.4 验证规则调整

```kotlin
// 禁用的 CommandBar 跳过内容验证（仅验证名称和重复性）
if (!button.enabled) continue

// 命令列表模式下，至少需要一个启用的普通 Command
val enabledNormalOptions = button.commands.filter { !it.isSeparator() && it.enabled }
if (enabledNormalOptions.isEmpty()) { /* 报错 */ }

// 禁用的 Command 跳过必填字段验证
if (!option.enabled) continue

// 禁用的 QuickParam 跳过验证
if (!quickParam.enabled) continue
```

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `settings/CCBarSettings.kt` | 三个 data class 添加 `enabled` 字段 + `deepCopy` |
| `actions/CCBarToolbarActionGroup.kt` | 过滤禁用的 CommandBar |
| `actions/CCBarCommandBarAction.kt` | 更新按钮启用判断逻辑 |
| `actions/CCBarPopupBuilder.kt` | 过滤禁用的 Command 和 QuickParam |
| `settings/ui/CCBarSettingsPanel.kt` | 列表渲染器变灰 + 启用复选框 + 验证规则调整 |
| `settings/ui/QuickParamEditDialog.kt` | 表格添加"启用"列 |

### 4.2 不受影响的部分

- 终端创建与命令执行逻辑（`CCBarTerminalService`）
- 命令预览对话框（`CommandPreviewDialog`）
- 环境变量编辑对话框（`EnvVariablesDialog`）
- 项目级配置逻辑（`CCBarProjectSettings`）— 数据结构变更自动跟随
- 图标系统（`CCBarIcons`）
- 配置导入/导出 — `enabled` 字段自动序列化/反序列化

---

## 5. 验收标准

### 5.1 功能验收

- [x] 禁用 CommandBar → 工具栏不显示该按钮
- [x] 禁用 Command → 弹出菜单不显示该命令
- [x] 禁用 QuickParam → 弹出菜单中不显示该快捷参数
- [x] 所有 QuickParam 禁用时不显示快捷参数行
- [x] 所有 Command 禁用时 CommandBar 按钮不可点击
- [x] 重新启用后恢复正常展示
- [x] 禁用项跳过必填字段验证
- [x] 至少需要一个启用的普通 Command（命令列表模式下）
- [x] 配置导入/导出包含 `enabled` 字段
- [x] 已有配置（无 `enabled` 字段）正常加载（默认 `true`）

### 5.2 UI 验收

- [x] 禁用项在设置面板中显示为灰色
- [x] 选中禁用项时使用正常前景色（不影响可读性）
- [x] CommandBar 详情区顶部有"启用"复选框
- [x] Command 详情区有"启用"复选框
- [x] 分割线类型不显示"启用"复选框
- [x] QuickParam 编辑对话框有"启用"列
- [x] 明暗主题下显示正常

### 5.3 兼容性验收

- [x] 兼容 IntelliJ IDEA 2023.1+
- [x] `./gradlew build` 编译通过
- [x] 已有配置无 `enabled` 字段时默认启用，不影响现有功能

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 向后兼容性 | 低 | `enabled` 默认值为 `true`，已有配置无此字段时自动启用 |
| 分割线误禁用 | 低 | 分割线类型不显示启用复选框，始终显示 |
| 验证规则遗漏 | 低 | 禁用项跳过验证，但名称唯一性仍然检查 |

---

## 7. 后续优化建议

1. **批量启用/禁用**：支持在列表中多选并批量切换启用状态
2. **右键菜单**：在列表项右键菜单中添加"启用/禁用"选项
3. **禁用项折叠**：设置面板中支持折叠/隐藏禁用项，减少视觉干扰
