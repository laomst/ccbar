# Story-04: 终端命名弹框增加追加参数能力 - 需求分析文档

## 1. 需求背景

### 1.1 当前实现

当前点击 Option 或 QuickParam 后，会弹出一个简单的 `Messages.showInputDialog` 对话框，仅支持：
- 输入终端标签页名称
- 默认名称来自 `Option.defaultTerminalName` 或 `Button.defaultTerminalName`

代码位置：`CCBarTerminalService.kt`
```kotlin
private fun showNameDialog(project: Project, option: CommandConfig): String? {
    return Messages.showInputDialog(
        project,
        "请输入终端标签名称：",
        "终端命名",
        null,
        option.defaultTerminalName,
        null
    )
}
```

### 1.2 用户需求

在实际使用场景中，用户经常需要在预设命令的基础上临时追加一些参数，例如：
- 预设命令：`claude --model sonnet`
- 追加参数：`--dangerously-skip-permissions`
- 最终执行：`claude --model sonnet --dangerously-skip-permissions`

需要：
1. 在弹框中展示当前将要执行的命令内容
2. 提供一个输入框让用户输入追加参数
3. 实时预览追加参数后的完整命令
4. 执行时将追加参数附加到命令末尾

---

## 2. 需求分析

### 2.1 布局结构

弹框采用简洁的两行布局：

```
┌─────────────────────────────────────────────────────────────┐
│  命令预览与参数配置                                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  终端标签名称: [Claude - Model                        ]    │
│                                                             │
│  claude --model sonnet  [追加参数输入框...            ]    │
│  ↑ 基础命令(只读)        ↑ 可编辑追加参数                  │
│                                                             │
│                        [ 取消 ]  [ 执行 ]                   │
└─────────────────────────────────────────────────────────────┘
```

**布局说明：**
- **第一行**：终端标签名称输入框（可编辑，默认值来自配置）
- **第二行**：基础命令文本（只读）+ 追加参数输入框（可编辑）
- 基础命令和追加参数在同一行，基础命令在前，追加参数输入框在后

### 2.2 交互行为

| 交互 | 行为 |
|------|------|
| 打开弹框 | 显示默认终端名称、基础命令文本、空的追加参数输入框 |
| 输入追加参数 | 参数追加到基础命令末尾 |
| 点击"执行" | 关闭弹框，创建终端并执行完整命令（基础命令 + 空格 + 追加参数） |
| 点击"取消" | 关闭弹框，不执行任何操作 |

### 2.3 视觉规范

| 元素 | 样式 |
|------|------|
| 基础命令文本 | 只读标签，普通文字样式 |
| 追加参数输入框 | 可编辑输入框 |
| 终端名称输入框 | 可编辑输入框 |
| 标签文字 | 右对齐 |

---

## 3. 技术方案

### 3.1 UI 组件选型

使用自定义 `DialogWrapper` 替代 `Messages.showInputDialog`：

```kotlin
class CommandPreviewDialog(
    project: Project,
    private val baseCommand: String,
    private val defaultTerminalName: String
) : DialogWrapper(project) {
    // UI 组件
    private val terminalNameField: JTextField  // 终端名称输入框
    private val baseCommandLabel: JLabel  // 基础命令文本（只读）
    private val additionalParamsField: JTextField  // 追加参数输入框

    // 输出结果
    val fullCommand: String  // 基础命令 + 追加参数
        get() = buildFullCommand()
    val terminalName: String  // 终端名称
        get() = terminalNameField.text.trim()

    private fun buildFullCommand(): String {
        val additional = additionalParamsField.text.trim()
        return if (additional.isNotEmpty()) {
            "$baseCommand $additional"
        } else {
            baseCommand
        }
    }
}
```

### 3.2 布局结构

```kotlin
override fun createCenterPanel(): JComponent {
    return panel {
        // 第一行：终端标签名称
        row("终端标签名称:") {
            textField()
                .bindText(terminalName)
                .also { terminalNameField = it.component }
        }

        // 第二行：基础命令 + 追加参数输入框
        row {
            label(baseCommand)  // 基础命令文本
                .also { baseCommandLabel = it.component }
            textField()
                .apply { emptyText.text = "追加参数..." }
                .also { additionalParamsField = it.component }
        }
    }
}
```

### 3.3 事件处理

- **OK 按钮**：验证终端名称非空，返回 `OK_EXIT_CODE`
- **Cancel 按钮**：返回 `CANCEL_EXIT_CODE`
- **Enter 键**：等同于点击 OK
- **Escape 键**：等同于点击 Cancel

---

## 4. 影响范围

### 4.1 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| `CCBarTerminalService.kt` | 替换 `showNameDialog` 和 `showNameDialogForButton` 方法，使用新的 `CommandPreviewDialog` |

### 4.2 需新增的文件

| 文件 | 内容 |
|------|------|
| `CommandPreviewDialog.kt` | 新增命令预览对话框组件 |

### 4.3 不受影响的部分

- 配置数据结构（`CommandBarConfig`, `CommandConfig`, `QuickParamConfig`）
- 设置界面
- 终端创建逻辑（`createTerminalWidget`, `executeCommandOnWidget`）
- 弹出菜单（`CCBarPopupBuilder`）

---

## 5. 验收标准

### 5.1 功能验收

- [x] 弹框正确显示基础命令文本
- [x] 追加参数输入框可正常输入
- [x] 终端名称输入框正常工作
- [x] 点击"执行"后正确执行完整命令（基础命令 + 追加参数）
- [x] 点击"取消"后不执行任何操作
- [x] 直接命令模式和选项列表模式均正常工作

### 5.2 UI 验收

- [x] 布局简洁，两行结构清晰
- [x] 明暗主题下颜色显示正常
- [x] 输入框支持键盘导航

### 5.3 兼容性验收

- [x] IntelliJ IDEA 2023.1+ 兼容
- [x] 不影响现有功能

---

## 6. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 用户习惯了旧的简单弹框 | 低 | 新弹框功能更丰富，操作仍然简单 |
| 追加参数格式错误 | 低 | 参数原样追加，由 shell 处理错误 |

---

## 7. 后续优化建议

1. **参数历史记录**：记住最近使用的追加参数
2. **参数模板**：预设常用的追加参数供快速选择
3. **参数验证**：对特定命令提供参数补全和验证
