# Story-01: 弹出选项框样式优化 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前的弹出选项框布局如下：

```
┌──────────────────────────────────────────┐
│ Model     [Default][Sonnet][Opus]        │
│ Workspace [Home][Work]                   │
│ System    [Dev]                          │
└──────────────────────────────────────────┘
```

每行由两部分组成：
- 左侧：命令名称（可点击执行基础命令）
- 右侧：快捷参数列表（内联显示）

### 1.2 用户需求

用户希望将弹出框样式改为**表单格式**，增加命令预览输入框：

```
┌────────────────────────────────────────────────────────────────────┐
│ Model     │ claude                    │ [Default][Sonnet][Opus]  │
│ Workspace │ claude                    │ [Home][Work]             │
│ System    │ claude                    │ [Dev]                    │
└────────────────────────────────────────────────────────────────────┘
```

**核心变化：**
1. 每行由**两列**变为**三列**布局
2. 新增中间列：命令预览输入框（只读）
3. 输入框动态显示命令：
   - 默认状态：显示 `Option.baseCommand`（基础命令）
   - 鼠标悬浮快捷参数时：显示完整命令 `Option.baseCommand + QuickParam.params`

---

## 2. 需求分析

### 2.1 布局结构

| 列号 | 内容 | 宽度策略 | 说明 |
|------|------|----------|------|
| 第一列 | 命令名称 | 固定/自适应 | 显示 Command.name，可点击执行基础命令 |
| 第二列 | 命令预览输入框 | 自适应拉伸 | 只读文本框，显示命令预览 |
| 第三列 | 快捷参数列表 | 固定/自适应 | 内联显示所有 QuickParam |

### 2.2 交互行为

#### 2.2.1 命令预览输入框

| 状态 | 显示内容 | 触发条件 |
|------|----------|----------|
| 默认 | `Command.baseCommand` | 弹出框打开时、鼠标离开所有快捷参数时 |
| 悬浮快捷参数 | `Command.baseCommand + " " + QuickParam.params` | 鼠标进入快捷参数区域 |

**示例：**
- Command: `{ name: "Model", baseCommand: "claude" }`
- QuickParam: `{ name: "Sonnet", params: "--model sonnet" }`

| 交互状态 | 输入框显示 |
|----------|-----------|
| 初始/默认 | `claude` |
| 悬浮 [Sonnet] | `claude --model sonnet` |
| 悬浮 [Opus] | `claude --model opus` |
| 离开快捷参数 | `claude` |

#### 2.2.2 点击行为

| 点击目标 | 执行命令 | 说明 |
|----------|----------|------|
| 命令名称 | `Command.baseCommand` | 点击文字区域执行基础命令 |
| 命令预览输入框 | `Command.baseCommand` | **新增**：点击输入框也执行基础命令 |
| 快捷参数 | `Command.baseCommand + QuickParam.params` | 执行完整命令 |

**交互逻辑**：
- 命令名称和命令预览输入框都是可点击的，点击后执行相同的基础命令
- 输入框虽然是只读的，但仍需响应鼠标点击事件
- 点击后弹出终端命名对话框，然后执行命令

### 2.3 视觉规范

#### 2.3.1 命令预览输入框

- **样式**：只读文本框（类似 `JTextField` 设置 `editable = false`）
- **背景色**：浅灰色或 IDE 默认只读背景色
- **边框**：细边框或无边框（保持简洁）
- **字体**：等宽字体（monospace），便于阅读命令
- **光标**：默认箭头（不可编辑）

#### 2.3.2 整体布局

- **列间距**：8-12px
- **行间距**：4-8px
- **内边距**：8px

---

## 3. 技术方案

### 3.1 UI 组件选型

| 组件 | 推荐实现 | 说明 |
|------|----------|------|
| 行容器 | `JPanel` + `GridBagLayout` 或 `BorderLayout` | 三列布局 |
| 命令名称 | `JBLabel` | 可点击，显示手型光标 |
| 命令预览 | `JTextField` (editable=false) | 只读文本框，**可点击**，显示手型光标 |
| 快捷参数 | `JButton` | 保持现有实现 |

### 3.2 状态管理

需要在每个 Command 行级别维护：
1. **命令预览输入框引用**：用于更新显示内容
2. **当前悬浮的快捷参数状态**：用于判断是否需要更新预览

### 3.3 事件处理

```kotlin
// 伪代码示意
subButton.addMouseListener(object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent) {
        // 更新命令预览为完整命令
        commandPreviewField.text = "${command.baseCommand} ${quickParam.params}"
    }

    override fun mouseExited(e: MouseEvent) {
        // 恢复为基础命令
        commandPreviewField.text = command.baseCommand
    }
})
```

### 3.4 布局示意代码

```kotlin
private fun createOptionRow(project: Project, command: CommandConfig): JPanel {
    val rowPanel = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(4, 0)
        isOpaque = false
    }

    val gbc = GridBagConstraints().apply {
        insets = JBUI.insets(4, 4)
        fill = GridBagConstraints.HORIZONTAL
    }

    // 第一列：命令名称（可点击）
    gbc.gridx = 0
    gbc.weightx = 0.0
    val optionLabel = JBLabel(command.name).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                CCBarTerminalService.openTerminal(project, command, null)
            }
        })
    }
    rowPanel.add(optionLabel, gbc)

    // 第二列：命令预览输入框（只读但可点击）
    gbc.gridx = 1
    gbc.weightx = 1.0  // 占用剩余空间
    val commandPreview = JTextField(command.baseCommand).apply {
        editable = false
        font = FontUtil.monospaced()
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "点击执行: ${command.baseCommand}"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                CCBarTerminalService.openTerminal(project, command, null)
            }
        })
    }
    rowPanel.add(commandPreview, gbc)

    // 第三列：快捷参数面板
    gbc.gridx = 2
    gbc.weightx = 0.0
    val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        isOpaque = false
    }
    for (quickParam in command.quickParams) {
        buttonsPanel.add(createQuickParam(project, command, quickParam, commandPreview))
    }
    rowPanel.add(buttonsPanel, gbc)

    return rowPanel
}
```

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarPopupBuilder.kt` | 重构 `createOptionRow` 方法，添加命令预览输入框，修改快捷参数鼠标事件 |

### 4.2 不受影响的部分

- 配置数据结构（`CommandBarConfig`, `CommandConfig`, `QuickParamConfig`）
- 终端服务（`CCBarTerminalService`）
- 设置界面
- 点击执行逻辑

---

## 5. 验收标准

### 5.1 功能验收

- [ ] 弹出框每行显示三列：命令名称、命令预览、快捷参数列表
- [ ] 命令预览输入框初始显示 `Command.baseCommand`
- [ ] 鼠标悬浮到快捷参数时，命令预览显示完整命令
- [ ] 鼠标离开快捷参数时，命令预览恢复为基础命令
- [ ] 点击命令名称执行基础命令
- [ ] **点击命令预览输入框执行基础命令**
- [ ] 点击快捷参数执行完整命令

### 5.2 UI 验收

- [ ] 命令预览输入框为只读状态（不可编辑）
- [ ] 命令预览使用等宽字体
- [ ] 命令预览输入框显示手型光标（表示可点击）
- [ ] 命令名称显示手型光标（表示可点击）
- [ ] 布局整齐，列对齐正确
- [ ] 响应 IDE 主题切换（亮色/暗色）

### 5.3 兼容性验收

- [ ] 兼容 IntelliJ IDEA 2023.1+
- [ ] 兼容 macOS/Windows/Linux

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 布局在某些主题下显示异常 | 低 | 使用 JBUI 获取主题相关尺寸和颜色 |
| 长命令导致输入框过宽 | 中 | 设置最大宽度或使用滚动 |
| 快捷参数过多导致行过高 | 低 | 已有换行机制，保持不变 |

---

## 7. 后续优化建议

1. **命令高亮**：对命令和参数进行语法高亮
2. **复制按钮**：在命令预览旁添加复制按钮
3. **自适应宽度**：根据命令长度动态调整输入框宽度
4. **键盘导航**：支持 Tab 键在选项间切换

---

## 8. 附录：UI 效果示意

### 8.1 默认状态

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Claude Code                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Model     │ claude                        │ [Default] [Sonnet] [Opus] │
│  Workspace │ claude                        │ [Home] [Work]             │
│  System    │ claude                        │ [Dev]                     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 8.2 悬浮 [Sonnet] 时

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Claude Code                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Model     │ claude --model sonnet        │ [Default] [Sonnet] [Opus] │
│             │                              │           ▲ 悬浮中         │
│  Workspace │ claude                        │ [Home] [Work]             │
│  System    │ claude                        │ [Dev]                     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 8.3 悬浮 [Work] 时

```
┌─────────────────────────────────────────────────────────────────────────┐
│  Claude Code                                                             │
├─────────────────────────────────────────────────────────────────────────┤
│  Model     │ claude                        │ [Default] [Sonnet] [Opus] │
│  Workspace │ claude --workspace ~/work     │ [Home] [Work]             │
│             │                              │           ▲ 悬浮中         │
│  System    │ claude                        │ [Dev]                     │
└─────────────────────────────────────────────────────────────────────────┘
```
