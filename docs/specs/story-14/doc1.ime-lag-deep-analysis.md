# CCBar 编辑器终端中文输入法卡顿 — 深入分析

> 调研日期：2026-03-02
> 基于 doc0 的审查结论，对根因进行深入调研，纠正原分析中的错误认知

## 1. 核心结论

卡顿的根因是 **焦点落点错误 + IME 客户端降级**，而非原文档所述的"事件传播链变长"。

CCBar 的 `getPreferredFocusedComponent()` 返回 `terminalWidget.component`（即 ShellTerminalWidget 这个 JPanel），而 IDE 原生实现返回 `terminalWidget.getPreferredFocusableComponent()`（即内部的 TerminalPanel）。焦点落在错误的组件上，导致 IME 框架将其视为"被动客户端"，走了一条性能更差的输入路径。

## 2. 关键事实：JediTerm 内部组件层次

通过反编译 Gradle 缓存中的 JediTerm 字节码，确认实际组件层次如下：

```
ShellTerminalWidget (extends JBTerminalWidget extends JediTermWidget extends JPanel)
│   .component → 返回 this（即 ShellTerminalWidget 自身，一个 JPanel）
│   .getPreferredFocusableComponent() → 返回内部的 TerminalPanel
│
└── myInnerPanel (JLayeredPane, 自定义 TerminalLayout)
      ├── myTerminalPanel (JBTerminalPanel extends TerminalPanel)  ← 真正处理 IME 的组件
      └── myScrollBar (JScrollBar)
```

关键发现：
- `terminalWidget.component` 返回的是 **JediTermWidget 自身**（一个 JPanel），不是 TerminalPanel
- `terminalWidget.getPreferredFocusableComponent()` 返回的是 **TerminalPanel**（真正的 IME 处理组件）
- TerminalPanel 在 `init()` 中调用了 `setFocusable(true)` 和 `enableInputMethods(true)`
- TerminalPanel 实现了 `getInputMethodRequests()` 返回自定义的 `MyInputMethodRequests`
- TerminalPanel 重写了 `processInputMethodEvent()` 处理组合文本和提交文本

## 3. 关键事实：IDE 原生 TerminalSessionEditor 的实现

反编译 terminal.jar 中的 `TerminalSessionEditor`，确认其实现：

```java
// IDE 原生实现
class TerminalSessionEditor implements FileEditor {
    // 直接返回 widget 组件，无 JPanel 包装
    getComponent() → file.getTerminalWidget().getComponent()

    // 返回 getPreferredFocusableComponent()，即 TerminalPanel
    getPreferredFocusedComponent() → file.getTerminalWidget().getPreferredFocusableComponent()
}
```

## 4. 关键事实：Swing IME 框架的工作机制

通过阅读 OpenJDK 21 源码（`sun.awt.im.InputContext`），确认 IME 事件分发机制：

### 4.1 IME 客户端分类

IME 框架根据焦点组件的能力将其分为两类：

```java
// sun.awt.im.InputContext
private boolean haveActiveClient() {
    Component client = getClientComponent();  // = 当前焦点组件
    return client != null
           && client.getInputMethodRequests() != null;  // 关键判断
}
```

| 客户端类型 | 条件 | 行为 |
|-----------|------|------|
| **主动客户端** | `getInputMethodRequests() != null` | InputMethodEvent 直接发送给组件，组件自行渲染组合文本（内联） |
| **被动客户端** | `getInputMethodRequests() == null` | 组合文本显示在系统合成窗口中，提交后转为逐字符 KeyEvent |

### 4.2 事件分发路径

```java
// sun.awt.im.InputMethodContext.dispatchInputMethodEvent()
public void dispatchInputMethodEvent(int id, ...) {
    Component source = getClientComponent();  // 焦点组件
    if (haveActiveClient() && !useBelowTheSpotInput()) {
        // 主动客户端：直接分发 InputMethodEvent
        source.dispatchEvent(event);
    } else {
        // 被动客户端：交给系统合成窗口处理
        getCompositionAreaHandler(true).processInputMethodEvent(event);
    }
}
```

### 4.3 被动客户端的提交文本处理

```java
// sun.awt.im.InputMethodContext.dispatchCommittedText()
// 被动客户端：将提交的文本拆成逐个字符的 KEY_TYPED 事件
char keyChar = text.first();
while (committedCharacterCount-- > 0) {
    KeyEvent keyEvent = new KeyEvent(client, KeyEvent.KEY_TYPED, time, 0,
                                     KeyEvent.VK_UNDEFINED, keyChar);
    client.dispatchEvent(keyEvent);
    keyChar = text.next();
}
```

### 4.4 enableInputMethods 的真实作用

```java
// java.awt.Component
boolean areInputMethodsEnabled() {
    return ((eventMask & AWTEvent.INPUT_METHODS_ENABLED_MASK) != 0)
        && ((eventMask & AWTEvent.KEY_EVENT_MASK) != 0 || keyListener != null);
}
```

`enableInputMethods(true)` 是所有 Component 的**默认值**。它只是一个开关，不影响主动/被动客户端的判定。真正决定 IME 行为的是 `getInputMethodRequests()` 的返回值。

## 5. 根因分析

### 5.1 CCBar 的焦点落点

CCBar 当前实现：

```kotlin
override fun getPreferredFocusedComponent(): JComponent = terminalWidget.component
```

`terminalWidget.component` 返回的是 `ShellTerminalWidget`（一个 JPanel）。当 IDE 激活编辑器 Tab 时：

1. IDE 调用 `getPreferredFocusedComponent()` → 得到 ShellTerminalWidget
2. IDE 对 ShellTerminalWidget 调用 `requestFocusInWindow()`
3. ShellTerminalWidget（JPanel）获得焦点
4. IME 框架检查焦点组件：`ShellTerminalWidget.getInputMethodRequests()` → **null**（JPanel 未实现此方法）
5. IME 判定为**被动客户端**

被动客户端的后果：
- 组合文本（拼音）显示在**系统合成窗口**中，而非终端光标位置内联显示
- 提交文本被拆成**逐字符 KEY_TYPED 事件**发送
- 每个字符独立经过 PTY → 终端模拟器 → 渲染 的完整链路
- 多字符输入（如一个中文词组）产生多次独立的终端刷新

### 5.2 IDE 原生的焦点落点

IDE 原生实现：

```java
getPreferredFocusedComponent() → terminalWidget.getPreferredFocusableComponent()
```

`getPreferredFocusableComponent()` 返回的是内部的 **TerminalPanel**。当 IDE 激活编辑器 Tab 时：

1. IDE 调用 `getPreferredFocusedComponent()` → 得到 TerminalPanel
2. IDE 对 TerminalPanel 调用 `requestFocusInWindow()`
3. TerminalPanel 获得焦点
4. IME 框架检查焦点组件：`TerminalPanel.getInputMethodRequests()` → **MyInputMethodRequests 实例**
5. IME 判定为**主动客户端**

主动客户端的行为：
- 组合文本（拼音）由 TerminalPanel 自行在光标位置**内联渲染**
- 提交文本作为**单个 InputMethodEvent** 发送
- TerminalPanel 的 `processInputMethodEvent()` 一次性将完整文本通过 `terminalStarter.sendString()` 发送到 PTY
- 只触发一次终端刷新

### 5.3 卡顿的具体机制

```
被动客户端路径（CCBar 当前）：
  用户输入 "你好"
  → OS IME 提交 "你好"
  → InputMethodContext 发现被动客户端
  → 拆分为 KEY_TYPED('你') + KEY_TYPED('好')
  → 每个 KeyEvent 独立经过：
      ShellTerminalWidget.processKeyEvent()
      → TerminalPanel.processKeyEvent()
      → TerminalStarter.sendString("你")  // 第一次 PTY 写入
      → PTY 回显 → 终端渲染                // 第一次渲染
      → TerminalStarter.sendString("好")  // 第二次 PTY 写入
      → PTY 回显 → 终端渲染                // 第二次渲染

主动客户端路径（IDE 原生）：
  用户输入 "你好"
  → OS IME 提交 "你好"
  → InputMethodContext 发现主动客户端
  → 发送单个 InputMethodEvent(committedText="你好")
  → TerminalPanel.processInputMethodEvent()
  → TerminalStarter.sendString("你好", true)  // 一次 PTY 写入
  → PTY 回显 → 终端渲染                        // 一次渲染
```

在 Claude Code CLI 等交互式终端中，每次 PTY 写入都可能触发 CLI 的输入处理逻辑（如自动补全、语法高亮等），逐字符发送会成倍放大延迟。

### 5.4 JPanel 包装层的实际影响

原文档将 JPanel 包装层视为主因，这是不准确的。JPanel 包装层的实际影响：

- **不影响 IME 事件传播**：InputMethodEvent 直接发送给焦点组件，不经过父容器传播
- **不影响 enableInputMethods**：所有 Component 默认已启用
- **间接影响**：增加了一层容器，使得 `getComponent()` 返回的组件与 `getPreferredFocusedComponent()` 返回的组件不一致，但这本身不是卡顿的原因

真正的问题是 `getPreferredFocusedComponent()` 返回了 `terminalWidget.component`（ShellTerminalWidget）而非 `terminalWidget.getPreferredFocusableComponent()`（TerminalPanel）。

## 6. 对原文档方案的评估

| 原方案 | 评估 | 理由 |
|--------|------|------|
| A. enableInputMethods on JPanel | **无效** | enableInputMethods 默认已启用；且 JPanel 不是焦点组件，在其上调用无意义 |
| B. 去掉 JPanel 包装 | **不能解决 IME 问题** | 去掉 JPanel 后 getComponent() 返回 terminalWidget.component，但焦点问题依然存在——焦点仍然落在 ShellTerminalWidget 上而非 TerminalPanel |
| C. 显式 requestFocus + enableInputMethods | **方向正确但目标错误** | 对 terminalWidget.component 调用 requestFocus 仍然是请求 ShellTerminalWidget 获得焦点 |
| D. 复用原生机制 | **有效但过度** | 能解决问题，但改动量大且引入对内部 API 的依赖 |

## 7. 修复方案

### 7.1 关键发现：JediTermWidget.requestFocusInWindow() 的行为

```java
// JediTermWidget.java
public boolean requestFocusInWindow() {
    return myTerminalPanel.requestFocusInWindow();  // 转发给 TerminalPanel
}
```

`JediTermWidget.requestFocusInWindow()` 会将焦点转发给内部的 `TerminalPanel`。这是正确的行为——只要调用了 `JediTermWidget.requestFocusInWindow()`，焦点最终会落在 `TerminalPanel` 上。

但问题是 `com.jediterm.terminal.ui.TerminalWidget.getPreferredFocusableComponent()` 的 **default 实现返回 `this`**：

```java
// TerminalWidget.java (JediTerm)
public default JComponent getPreferredFocusableComponent() {
    return getComponent();  // 返回 JediTermWidget 自身，不是 TerminalPanel
}
```

所以 `terminalWidget.preferredFocusableComponent` 返回的是 `JediTermWidget`，调用 `requestFocusInWindow()` 时会触发转发。

### 7.2 方案一：使用 IdeFocusManager（推荐）

简单调用 `requestFocusInWindow()` 可能因为对话框关闭后焦点状态不稳定而失效。需要使用 `IdeFocusManager` 来正确管理焦点：

```kotlin
import com.intellij.openapi.wm.IdeFocusManager

// 终端创建后
val focusableComponent = widget.preferredFocusableComponent
if (focusableComponent != null) {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        IdeFocusManager.getGlobalInstance().requestFocus(focusableComponent, true)
    }
}
```

`doWhenFocusSettlesDown` 确保在 IDE 焦点状态稳定后再请求焦点，`requestFocus(comp, true)` 强制请求焦点。

### 7.3 方案二：修正 FileEditor 的焦点落点

对于编辑器模式，还需要修正 `getPreferredFocusedComponent()`：

```kotlin
override fun getComponent(): JComponent = terminalWidget.component

override fun getPreferredFocusedComponent(): JComponent? {
    return terminalWidget.preferredFocusableComponent
}
```

### 7.4 完整修复

工具窗口模式（`CCBarTerminalService.kt`）：

```kotlin
ApplicationManager.getApplication().invokeLater {
    val manager = TerminalToolWindowManager.getInstance(project)
    val widget = manager.createShellWidget(workingDir, tabName, true, true)
    widget.sendCommandToExecute(command)

    val focusableComponent = widget.preferredFocusableComponent
    if (focusableComponent != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(focusableComponent, true)
        }
    }
}
```

编辑器模式（`TerminalFileEditor.kt`）：

```kotlin
// 去掉 mainPanel 包装
override fun getComponent(): JComponent = terminalWidget.component

// 返回正确的焦点组件
override fun getPreferredFocusedComponent(): JComponent? {
    return terminalWidget.preferredFocusableComponent
}

// 会话启动后请求焦点
ApplicationManager.getApplication().invokeLater {
    val focusableComponent = terminalWidget.preferredFocusableComponent
    if (focusableComponent != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(focusableComponent, true)
        }
    }
}
```

## 8. 验证方法

修复后可通过以下方式验证：

1. 在编辑器终端中使用中文输入法输入，确认组合文本（拼音）显示在终端光标位置而非系统合成窗口
2. 输入多字词组，确认提交后终端一次性显示完整文本而非逐字符出现
3. 在 Claude Code CLI 中测试中文输入，确认无明显卡顿
4. 对比 IDE 原生"移动到编辑器"的终端，确认行为一致

