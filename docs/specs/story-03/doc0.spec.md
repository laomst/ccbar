# Story-03: Option 分割线 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前 Option 列表中的所有项都是相同类型的配置项：

```
Button（工具栏按钮）
  └── Option（绑定 baseCommand + workingDirectory + defaultTerminalName）
        └── SubButton（绑定 params）
```

**Option 列表渲染**：
- 设置面板：所有 Option 项以相同样式渲染在 JBList 中
- 弹框：所有 Option 行以相同的三列布局渲染

**配置结构**：
```kotlin
data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var subButtons: MutableList<SubButtonConfig> = mutableListOf()
)
```

### 1.2 用户需求

用户希望在 Option 列表中添加分割线，实现分组效果：

- **视觉分组**：将相关的 Option 归为一组，提高可读性
- **最小改动**：复用现有数据结构，减少对现有代码的影响
- **兼容旧数据**：新增的类型字段应有默认值，确保旧配置能正常加载

---

## 2. 需求分析

### 2.1 数据模型变更

**OptionConfig 新增字段**：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `type` | String | "" | 选项类型，空值或 "option" 表示普通选项，"separator" 表示分割线 |

**配置结构**：
```kotlin
data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var subButtons: MutableList<SubButtonConfig> = mutableListOf(),
    // 新增字段
    var type: String = ""  // 空值或"option"=普通选项, "separator"=分割线
) {
    /**
     * 判断是否为分割线类型
     */
    fun isSeparator(): Boolean = type == "separator"

    /**
     * 判断是否为普通选项类型（默认）
     */
    fun isOption(): Boolean = type != "separator"
}
```

### 2.2 布局结构

#### 2.2.1 设置面板 Option 列表

| 项类型 | 渲染效果 | 可选中 | 可编辑 |
|--------|----------|--------|--------|
| 普通选项 | 显示名称文本 | ✅ 是 | ✅ 是 |
| 分割线 | 显示水平分隔线 + 可选标题 | ✅ 是 | ❌ 否（详情面板隐藏） |

#### 2.2.2 弹框 Option 列表

| 项类型 | 渲染效果 | 可点击 |
|--------|----------|--------|
| 普通选项 | 三列布局：选项名称 \| 命令预览 \| 子按钮 | ✅ 是 |
| 分割线 | 水平分隔线（占满宽度） | ❌ 否 |

### 2.3 交互行为

#### 2.3.1 设置面板 - 添加 Option

[+] 按钮悬浮时显示下拉菜单：

```
┌─────────────────────┐
│ [+] ▼              │  ← 点击或悬浮显示下拉菜单
└─────────────────────┘
        │
        ▼
┌─────────────────────┐
│ + 添加选项          │
│ ───────────────────│
│ + 添加分割线        │
└─────────────────────┘
```

| 操作 | 说明 |
|------|------|
| 点击"添加选项" | 创建 type="" 的 OptionConfig，可编辑所有字段 |
| 点击"添加分割线" | 创建 type="separator" 的 OptionConfig，详情面板隐藏 |

#### 2.3.2 设置面板 - 选中分割线

当用户选中分割线类型的项时：
- **Option 详情面板**：隐藏所有字段，显示提示信息
- **SubButton 表格**：隐藏，显示提示信息

#### 2.3.3 弹框 - 渲染分割线

分割线在弹框中渲染为：
- 占满宽度的水平分隔线
- 可选显示标题（来自 OptionConfig.name）

### 2.4 视觉规范

#### 2.4.1 设置面板分割线渲染

```
┌─────────────────────────────────┐
│ Model                           │  ← 普通选项
│ ────────── 分组标题 ────────── │  ← 分割线（带标题）
│ Workspace                       │  ← 普通选项
│ System                          │  ← 普通选项
│ ────────────────────────────── │  ← 分割线（无标题）
│ Other                           │  ← 普通选项
└─────────────────────────────────┘
```

- **分割线样式**：灰色水平线
- **标题样式**：灰色小字，居中显示在线条中间

#### 2.4.2 弹框分割线渲染

```
┌──────────────────────────────────────────────────────────────────┐
│ Model     │ claude                    │ [Default][Sonnet][Opus] │
│ ─────────────────────────────────────────────────────────────── │
│ Workspace │ claude                    │ [Home][Work]            │
│ System    │ claude                    │ [Dev]                   │
└──────────────────────────────────────────────────────────────────┘
```

- **分割线样式**：占满宽度的灰色水平线
- **上下间距**：8px

---

## 3. 技术方案

### 3.1 数据模型修改

**文件**：`CCBarSettings.kt`

```kotlin
// OptionConfig 类型常量
object OptionType {
    const val OPTION = "option"
    const val SEPARATOR = "separator"
}

data class OptionConfig(
    var id: String = "",
    var name: String = "",
    var baseCommand: String = "",
    var workingDirectory: String = "",
    var defaultTerminalName: String = "",
    var subButtons: MutableList<SubButtonConfig> = mutableListOf(),
    // 新增字段
    var type: String = ""  // 空值或"option"=普通选项, "separator"=分割线
) {
    fun deepCopy(): OptionConfig = OptionConfig(
        id = id,
        name = name,
        baseCommand = baseCommand,
        workingDirectory = workingDirectory,
        defaultTerminalName = defaultTerminalName,
        subButtons = subButtons.map { it.deepCopy() }.toMutableList(),
        type = type
    )

    /**
     * 判断是否为分割线类型
     */
    fun isSeparator(): Boolean = type == OptionType.SEPARATOR
}
```

### 3.2 设置面板修改

**文件**：`CCBarSettingsPanel.kt`

#### 3.2.1 Option 列表渲染器修改

```kotlin
private class OptionListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): JComponent {
        if (value is OptionConfig && value.isSeparator()) {
            // 分割线渲染
            return createSeparatorRenderer(value, isSelected)
        }

        // 普通选项渲染（原有逻辑）
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is OptionConfig) {
            text = value.name
        }
        return component as JComponent
    }

    private fun createSeparatorRenderer(option: OptionConfig, isSelected: Boolean): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = true
        panel.background = if (isSelected) list.selectionBackground else list.background

        if (option.name.isNotBlank()) {
            // 带标题的分割线
            val label = JLabel(option.name).apply {
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.CENTER
            }
            panel.add(JSeparator(), BorderLayout.NORTH)
            panel.add(label, BorderLayout.CENTER)
            panel.add(JSeparator(), BorderLayout.SOUTH)
        } else {
            // 无标题分割线
            panel.add(JSeparator(), BorderLayout.CENTER)
        }

        return panel
    }
}
```

#### 3.2.2 [+] 按钮改为下拉菜单

使用 `ToolbarDecorator` 的 `setAddAction` 改为显示下拉弹出菜单：

```kotlin
val decorator = com.intellij.ui.ToolbarDecorator.createDecorator(optionList)
    .setAddAction { addButton ->
        // 显示下拉菜单
        showAddOptionPopup(addButton.sourceComponent)
    }

private fun showAddOptionPopup(component: java.awt.Component) {
    val popup = JBPopupFactory.getInstance().createListPopup(
        object : BaseListPopupStep<String>("添加", listOf("添加选项", "添加分割线")) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupFactory.Action? {
                when (selectedValue) {
                    "添加选项" -> addOption()
                    "添加分割线" -> addSeparator()
                }
                return null
            }
        }
    )
    popup.showUnderneathOf(component)
}

private fun addSeparator() {
    val button = selectedButton ?: return
    val newSeparator = OptionConfig(
        id = UUID.randomUUID().toString(),
        name = "",
        type = OptionType.SEPARATOR
    )
    button.options.add(newSeparator)
    optionListModel.add(newSeparator)
    optionList.selectedIndex = optionListModel.size - 1
}
```

#### 3.2.3 分割线选中时隐藏详情面板

```kotlin
private fun onOptionSelected() {
    if (ignoreUpdate) return

    val index = optionList.selectedIndex
    if (index >= 0 && selectedButton != null) {
        selectedOption = selectedButton!!.options[index]

        if (selectedOption!!.isSeparator()) {
            // 分割线：隐藏详情面板，显示提示
            hideOptionDetailForSeparator()
        } else {
            // 普通选项：显示详情面板
            updateOptionDetail()
            updateSubButtonTable()
        }
    } else {
        selectedOption = null
        clearOptionDetail()
        updateSubButtonTable()
    }
}

private fun hideOptionDetailForSeparator() {
    // 隐藏 Option 详情面板的所有字段
    optionDetailPanel.isVisible = false
    // 显示分割线提示
    separatorHintPanel.isVisible = true
    // 隐藏 SubButton 表格
    subButtonPanel.isVisible = false
}
```

### 3.3 弹框修改

**文件**：`CCBarPopupBuilder.kt`

```kotlin
fun buildPopup(project: Project, buttonConfig: ButtonConfig): JBPopup {
    val mainPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        background = JBColor.PanelBackground
    }

    lateinit var popup: JBPopup

    for (option in buttonConfig.options) {
        if (option.isSeparator()) {
            // 分割线渲染
            mainPanel.add(createSeparatorRow(option))
        } else {
            // 普通选项渲染
            val optionRow = createOptionRow(project, option) { popup.closeOk(null) }
            mainPanel.add(optionRow)
        }
        mainPanel.add(Box.createVerticalStrut(8))
    }

    popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(mainPanel, null)
        // ... 其他配置
        .createPopup()

    return popup
}

private fun createSeparatorRow(option: OptionConfig): JComponent {
    val panel = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = JBUI.Borders.emptyVertical(4)
    }

    if (option.name.isNotBlank()) {
        // 带标题的分割线
        val leftLine = JSeparator(SwingConstants.HORIZONTAL)
        val label = JLabel(option.name).apply {
            foreground = JBColor.GRAY
            border = JBUI.Borders.emptyHorizontal(8)
        }
        val rightLine = JSeparator(SwingConstants.HORIZONTAL)

        val innerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(leftLine, BorderLayout.WEST)
            add(label, BorderLayout.CENTER)
            add(rightLine, BorderLayout.EAST)
        }
        panel.add(innerPanel, BorderLayout.CENTER)
    } else {
        // 无标题分割线
        panel.add(JSeparator(), BorderLayout.CENTER)
    }

    return panel
}
```

### 3.4 验证逻辑修改

**文件**：`CCBarSettingsPanel.kt`

```kotlin
fun validate(): List<String> {
    val errors = mutableListOf<String>()

    for ((buttonIndex, button) in editingState.buttons.withIndex()) {
        // ... Button 验证

        if (!button.isDirectCommandMode()) {
            // 过滤掉分割线，只验证普通选项
            val normalOptions = button.options.filter { !it.isSeparator() }

            if (normalOptions.isEmpty()) {
                errors.add("Button '${button.name}': 未配置直接命令时，必须至少有一个普通选项")
            }

            for (option in normalOptions) {
                // 原有 Option 验证逻辑...
            }
        }
    }

    return errors
}
```

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarSettings.kt` | OptionConfig 新增 type 字段；新增 isSeparator() 方法；修改 deepCopy() |
| `CCBarSettingsPanel.kt` | 修改 OptionListCellRenderer；[+] 按钮改为下拉菜单；分割线选中时隐藏详情；修改验证逻辑 |
| `CCBarPopupBuilder.kt` | 识别分割线类型并使用专用渲染；新增 createSeparatorRow() 方法 |

### 4.2 不受影响的部分

- Button 配置和逻辑
- SubButton 配置和逻辑
- 终端创建和命令执行的核心逻辑
- 配置导入/导出（JSON 格式向后兼容）
- CCBarButtonAction

### 4.3 向后兼容性

- 新增字段 `type` 有默认值（空字符串）
- 空字符串表示普通选项，与现有行为一致
- 现有配置可正常加载和使用

---

## 5. 验收标准

### 5.1 功能验收

- [x] OptionConfig 新增 type 字段，支持 "separator" 类型
- [x] 设置面板 [+] 按钮点击时显示选择对话框（添加选项/添加分割线）
- [x] 设置面板 Option 列表中分割线使用专用渲染
- [x] 选中分割线时，详情面板隐藏
- [x] 弹框中分割线使用专用渲染（水平分隔线）
- [x] 验证逻辑跳过分割线类型

### 5.2 UI 验收

- [ ] 设置面板分割线渲染效果符合视觉规范
- [ ] 弹框分割线渲染效果符合视觉规范
- [ ] 分割线可选中、可移动、可删除
- [ ] 带标题的分割线正确显示标题

### 5.3 兼容性验收

- [ ] 现有配置（无 type 字段）可正常加载
- [ ] 配置导入/导出正常工作
- [x] 兼容 IntelliJ IDEA 2023.1+
- [ ] 兼容 macOS/Windows/Linux

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 现有配置迁移问题 | 低 | 新增字段有默认值，旧配置自动兼容 |
| ToolbarDecorator 下拉菜单实现复杂度 | 低 | 使用 JBPopupFactory 创建简单下拉菜单 |
| 分割线在列表中的选中样式异常 | 低 | 使用自定义渲染器处理选中状态 |
| 弹框分割线布局问题 | 低 | 使用 JSeparator 组件，简单可靠 |

---

## 7. 后续优化建议

1. **分割线标题样式**：支持自定义分割线标题的字体和颜色
2. **分割线快捷操作**：双击分割线快速编辑标题
3. **拖拽排序**：支持拖拽调整分割线和选项的顺序

---

## 8. 附录：UI 效果示意

### 8.1 设置面板 - Option 列表（含分割线）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Settings: CCBar                                                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌───────────────────────────────────────────────────┐ │
│  │ Options             │  │ Option Details                                  │ │
│  │                     │  │                                                   │ │
│  │   Model             │  │ ┌──────────────────────────────────────────────┐ │ │
│  │ ──── Claude ────    │  ← │ Name:       [Model                        ]   │ │ │
│  │   Workspace         │  │ │ Base Command: ...                            │ │ │
│  │   System            │  │ └──────────────────────────────────────────────┘ │ │
│  │ ────────────────── │  ← 分割线（无标题）                                  │ │
│  │   Other             │  │                                                   │ │
│  │                     │  │                                                   │ │
│  │ [+]▼ [-][↑][↓]     │  ← [+] 带下拉菜单                                   │ │
│  └─────────────────────┘  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 [+] 按钮下拉菜单

```
┌─────────────────────┐
│ Options             │
│                     │
│   Model             │
│ ──── Claude ────    │
│   Workspace         │
│                     │
│ [+]▼ [-][↑][↓]     │
    │
    ▼ 点击 [+] 或悬浮
┌─────────────────────┐
│ 📝 添加选项         │
│ ─────────────────── │
│ ➖ 添加分割线        │
└─────────────────────┘
```

### 8.3 弹框（含分割线）

```
┌────────────────────────────────────────────────────────────────────────┐
│ Claude Code                                                             │
├────────────────────────────────────────────────────────────────────────┤
│ Model     │ claude                    │ [Default][Sonnet][Opus]        │
│ ─────────────────────────────────────────────────────────────────────  │
│ Workspace │ claude                    │ [Home][Work]                   │
│ System    │ claude                    │ [Dev]                          │
│ ─────────────────────────────────────────────────────────────────────  │
│ Other     │ npm                       │ [Test][Build]                  │
└────────────────────────────────────────────────────────────────────────┘
```

### 8.4 选中分割线时的详情面板

```
┌───────────────────────────────────────────────────────────────────────┐
│ Option Details                                                        │
│                                                                       │
│  ℹ️ 分割线类型无需配置详情                                             │
│     名称字段将作为分割线标题显示                                       │
│                                                                       │
└───────────────────────────────────────────────────────────────────────┘
```
