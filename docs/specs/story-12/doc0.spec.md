# Story-12: CommandBar 级别全局环境变量 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前 CommandBar 和 Command 各自独立配置环境变量（`envVariables` 字段），但两者互不关联：

- **直接命令模式**：使用 CommandBar 自身的 `envVariables`
- **Command 列表模式**：仅使用 Command 自身的 `envVariables`，CommandBar 的 `envVariables` 被忽略

设置界面中，CommandBar 的 `envVariables` 字段仅在直接命令模式（`command` 非空）时可见。

### 1.2 用户需求

用户希望在 CommandBar 级别新增一个全局环境变量字段，作为"全局默认"对其下所有 Command 和直接命令统一生效，减少重复配置。同时允许 Command/直接命令级别覆盖同名变量。

## 2. 需求分析

### 2.1 新增字段

在 `CommandBarConfig` 数据模型中新增 `commonEnvVariables` 字段，与现有 `envVariables` 字段独立共存：

- `commonEnvVariables`：全局环境变量，始终可见，对所有 Command 和直接命令生效
- `envVariables`：直接命令模式专用环境变量，仅直接命令模式时可见（保持不变）

### 2.2 环境变量合并规则

统一合并规则：`commonEnvVariables` 作为基础，具体环境变量覆盖同名变量。

**直接命令模式**：最终环境变量 = `CommandBar.commonEnvVariables` 合并 `CommandBar.envVariables`
**Command 列表模式**：最终环境变量 = `CommandBar.commonEnvVariables` 合并 `Command.envVariables`

覆盖规则：后者的同名变量覆盖前者。

**示例**：
```
CommandBar.commonEnvVariables = "MODEL=sonnet;API_KEY=abc123"
Command.envVariables          = "MODEL=opus;DEBUG=true"
合并结果                       = "MODEL=opus;API_KEY=abc123;DEBUG=true"
```

### 2.3 设置界面变化

- CommandBar 详情面板新增 "环境变量(公共):" 字段，始终可见（不受直接命令模式限制）
- 原有 "环境变量:" 字段保持不变（直接命令模式时可见）
- 新字段位置：放在 CommandBar 名称/图标字段之后、直接命令相关字段之前

### 2.4 命令确认弹框变化

CommandPreviewDialog 中显示的环境变量应为**合并后的结果**，用户在弹框中的临时修改基于合并后的值进行。

## 3. 技术方案

### 3.1 数据模型修改

`CommandBarConfig` 新增字段：

```kotlin
data class CommandBarConfig(
    // ... 现有字段 ...
    var commonEnvVariables: String = "",  // 新增：全局环境变量
    // ... 现有字段 ...
)
```

### 3.2 环境变量合并逻辑

在 `CCBarTerminalService` 中新增合并方法：

```kotlin
private fun mergeEnvVariables(baseEnvVars: String, overrideEnvVars: String): String {
    val baseVars = parseEnvVariables(baseEnvVars)
    val overrideVars = parseEnvVariables(overrideEnvVars)
    val merged = LinkedHashMap<String, String>()
    baseVars.forEach { (k, v) -> merged[k] = v }
    overrideVars.forEach { (k, v) -> merged[k] = v }
    return merged.entries.joinToString(";") { "${it.key}=${it.value}" }
}
```

### 3.3 调用链修改

**直接命令模式** (`openTerminalForCommandBar`)：合并 `commandBar.commonEnvVariables` + `commandBar.envVariables` 后传给 CommandPreviewDialog。

**Command 列表模式** (`openTerminal`)：新增 `globalEnvVars` 参数，合并 `globalEnvVars` + `command.envVariables` 后传给 CommandPreviewDialog。

**CCBarPopupBuilder**：将 `commandBarConfig.commonEnvVariables` 传递到 `openTerminal` 调用。

### 3.4 设置界面修改

`CCBarSettingsPanel` 中新增 `commandBarGlobalEnvVariablesPanel`，始终可见，包含文本框 + `[…]` 编辑按钮。

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|---------|
| `CCBarSettings.kt` | `CommandBarConfig` 新增 `commonEnvVariables` 字段及 deepCopy |
| `CCBarTerminalService.kt` | 新增 `mergeEnvVariables`；修改 `openTerminal` 和 `openTerminalForCommandBar` |
| `CCBarPopupBuilder.kt` | 传递 `commandBarConfig.commonEnvVariables` 到 Command 执行路径 |
| `CCBarSettingsPanel.kt` | 新增全局环境变量 UI 字段 |

### 4.2 不受影响的部分

- `CommandConfig` 数据模型：无需修改
- 环境变量编辑对话框（`EnvVariablesDialog`）：复用现有组件
- 原有 `envVariables` 字段的 UI 和行为：保持不变

## 5. 验收标准

### 5.1 功能验收

- [ ] CommandBar 的 commonEnvVariables 在 Command 执行时生效
- [ ] Command 的同名环境变量覆盖 commonEnvVariables 的值
- [ ] 直接命令模式下 commonEnvVariables 与 envVariables 正确合并
- [ ] 命令确认弹框显示合并后的环境变量
- [ ] 用户可在弹框中临时修改合并后的环境变量

### 5.2 UI 验收

- [ ] CommandBar 详情面板始终显示 "环境变量(公共):" 字段
- [ ] 原有 "环境变量:" 字段行为不变（仅直接命令模式可见）

### 5.3 兼容性验收

- [ ] 未配置 commonEnvVariables 时，行为与之前完全一致
- [ ] 已有配置文件升级后 commonEnvVariables 默认为空，不影响现有功能

## 6. 风险评估

- **低风险**：新增字段默认为空，不影响现有行为
- **向后兼容**：PersistentStateComponent 对新增字段自动使用默认值
