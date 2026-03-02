# Story-13: CommandBar 级别公共快捷参数 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前快捷参数（QuickParam）仅在 Command 级别配置，每个 Command 独立维护自己的快捷参数列表。如果多个 Command 需要相同的快捷参数（如 `--verbose`、`--dry-run` 等通用开关），需要在每个 Command 下重复配置。

### 1.2 用户需求

用户希望在 CommandBar 级别新增公共快捷参数，作为"全局默认"对其下所有 Command 统一生效，减少重复配置。同时：
- 公共快捷参数**仅对 Command 列表模式生效**，对直接命令模式不生效
- 如果 Command 中存在与公共快捷参数**同名**的参数，则 Command 级别的覆盖公共的

## 2. 需求分析

### 2.1 新增字段

在 `CommandBarConfig` 数据模型中新增 `commonQuickParams` 字段：

- `commonQuickParams`：公共快捷参数列表，仅 Command 列表模式时生效
- 数据类型与 `Command.quickParams` 相同（`MutableList<QuickParamConfig>`）

### 2.2 合并规则

对每个 Command，弹出菜单展示的快捷参数为合并后的结果：

1. 以 `CommandBar.commonQuickParams` 为基础
2. 以 `Command.quickParams` 为覆盖层
3. **按 `name` 字段判断是否同名**：Command 中存在同名快捷参数时，使用 Command 的版本（完全替换，包括 params、icon、enabled 等所有字段）
4. 合并后的顺序：公共快捷参数（未被覆盖的）在前，Command 自身的快捷参数在后

**示例**：
```
CommandBar.commonQuickParams = [
  { name: "Verbose", params: "--verbose" },
  { name: "DryRun",  params: "--dry-run" }
]

Command "Deploy".quickParams = [
  { name: "Staging", params: "--env staging" },
  { name: "Verbose", params: "--verbose --debug" }  // 覆盖公共的 Verbose
]

合并结果 = [
  { name: "DryRun",  params: "--dry-run" },            // 公共（未被覆盖）
  { name: "Staging", params: "--env staging" },         // Command 自身
  { name: "Verbose", params: "--verbose --debug" },     // Command 覆盖了公共的
]
```

### 2.3 生效范围

| 模式 | 公共快捷参数是否生效 |
|------|:---:|
| Command 列表模式 | ✅ |
| Command 列表模式（简易模式） | ❌（简易模式不显示快捷参数） |
| 直接命令模式 | ❌ |

### 2.4 设置界面变化

CommandBar 详情面板新增"快捷参数(公共)"区域：
- 位置：放在现有字段之后、Command 列表之前（或与 Command 列表同级的独立区域）
- 使用与 Command 下快捷参数相同的列表编辑组件（ToolbarDecorator + 表格/列表）
- 仅在非直接命令模式时可见（因为公共快捷参数不对直接命令生效）

### 2.5 弹出菜单变化

- 每个 Command 行的第二行快捷参数列表展示**合并后**的结果
- 公共快捷参数的外观与 Command 自身的快捷参数一致，用户无需区分来源
- 命令预览宽度计算需考虑合并后的快捷参数

### 2.6 命令拼接

命令拼接逻辑不变：`Command.baseCommand + QuickParam.params`。公共快捷参数的 params 与 Command 自身的 params 使用方式完全一致。

## 3. 技术方案

### 3.1 数据模型修改

`CommandBarConfig` 新增字段：

```kotlin
data class CommandBarConfig(
    // ... 现有字段 ...
    var commonQuickParams: MutableList<QuickParamConfig> = mutableListOf(),  // 新增
    // ... 现有字段 ...
)
```

`deepCopy()` 同步更新。

### 3.2 快捷参数合并逻辑

新增合并方法（建议放在 `CCBarPopupBuilder` 或工具类中）：

```kotlin
fun mergeQuickParams(
    commonParams: List<QuickParamConfig>,
    commandParams: List<QuickParamConfig>
): List<QuickParamConfig> {
    val commandNames = commandParams.filter { it.enabled }.map { it.name }.toSet()
    val filteredCommon = commonParams.filter { it.enabled && it.name !in commandNames }
    return filteredCommon + commandParams.filter { it.enabled }
}
```

### 3.3 弹出菜单修改

`CCBarPopupBuilder` 中：
- `createCommandBlock()` 使用合并后的快捷参数列表
- `calculateMaxPreviewWidth()` 使用合并后的快捷参数计算宽度

### 3.4 设置界面修改

`CCBarSettingsPanel` 中新增公共快捷参数编辑区域，复用现有的 QuickParam 编辑组件模式。

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|---------|
| `CCBarSettings.kt` | `CommandBarConfig` 新增 `commonQuickParams` 字段及 deepCopy |
| `CCBarPopupBuilder.kt` | 合并快捷参数逻辑；`createCommandBlock` 和 `calculateMaxPreviewWidth` 使用合并结果 |
| `CCBarSettingsPanel.kt` | 新增公共快捷参数编辑 UI |

### 4.2 不受影响的部分

- `CCBarTerminalService.kt`：命令拼接逻辑不变，仍然是 `baseCommand + quickParam.params`
- `CommandPreviewDialog.kt`：不涉及快捷参数展示
- `CommandConfig` 数据模型：无需修改
- `QuickParamConfig` 数据模型：无需修改
- 环境变量相关逻辑：不受影响

## 5. 验收标准

### 5.1 功能验收

- [ ] CommandBar 的 commonQuickParams 在弹出菜单中对每个 Command 生效
- [ ] Command 同名快捷参数覆盖公共快捷参数
- [ ] 直接命令模式下公共快捷参数不生效
- [ ] 简易模式下公共快捷参数不显示（简易模式本身不显示快捷参数）
- [ ] 点击合并后的公共快捷参数能正确执行命令
- [ ] 悬浮合并后的公共快捷参数时命令预览正确更新

### 5.2 UI 验收

- [ ] 设置面板中 CommandBar 详情显示公共快捷参数编辑区域
- [ ] 公共快捷参数编辑支持增删改和排序
- [ ] 直接命令模式时公共快捷参数编辑区域不可见

### 5.3 兼容性验收

- [ ] 未配置 commonQuickParams 时，行为与之前完全一致
- [ ] 已有配置文件升级后 commonQuickParams 默认为空列表，不影响现有功能

## 6. 风险评估

- **低风险**：新增字段默认为空列表，不影响现有行为
- **向后兼容**：PersistentStateComponent 对新增字段自动使用默认值

## 7. 后续优化建议

- 可考虑在弹出菜单中用不同样式（如斜体或浅色标记）区分公共快捷参数和 Command 自身的快捷参数
- 可考虑支持公共快捷参数的全局禁用开关
