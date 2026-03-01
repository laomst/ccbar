# CCBar - 技术选型与架构设计

## 1. 技术栈总览

| 维度 | 选型 | 说明 |
|------|------|------|
| 开发语言 | **Kotlin** | JetBrains 官方推荐，支持 UI DSL v2、data class、协程 |
| 构建工具 | **Gradle + Kotlin DSL** | `build.gradle.kts`，IntelliJ 插件开发标准 |
| Gradle 插件 | **IntelliJ Platform Gradle Plugin 2.x** | `org.jetbrains.intellij.platform`，新项目推荐 |
| 最低兼容版本 | **IntelliJ IDEA 2024.2** (Build 242) | 兼容 New UI，Classic Terminal API 可用 |
| JDK | **JDK 17** | IntelliJ 2023.1+ 的运行时要求 |

---

## 2. 项目结构

```
ccbar/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/com/github/ccbar/
│       │   ├── actions/                    # Action 相关
│       │   │   ├── CCBarToolbarActionGroup.kt  # 工具栏动态 ActionGroup
│       │   │   ├── CCBarCommandBarAction.kt    # 工具栏按钮 Action
│       │   │   └── CCBarPopupBuilder.kt        # 自定义弹出菜单构建
│       │   ├── terminal/                   # 终端相关
│       │   │   └── CCBarTerminalService.kt     # 终端创建服务（工具窗口 API）
│       │   ├── settings/                   # 设置相关
│       │   │   ├── CCBarSettings.kt            # PersistentStateComponent
│       │   │   ├── CCBarSettingsConfigurable.kt # Configurable 入口
│       │   │   └── ui/                     # 设置界面组件
│       │   │       └── CCBarSettingsPanel.kt       # 主设置面板
│       │   └── icons/                      # 图标工具
│       │       └── CCBarIcons.kt               # 图标加载与管理
│       └── resources/
│           └── META-INF/
│               └── plugin.xml              # 插件描述文件
└── docs/
    ├── spec.md                             # 需求文档
    ├── tech-design.md                      # 本文档
    └── dev-plan.md                         # 开发计划
```

---

## 3. 构建配置

### 3.1 `gradle.properties`

```properties
pluginGroup = com.github.ccbar
pluginName = CCBar
pluginVersion = 1.0.0

platformType = IC
platformVersion = 2024.2
platformBundledPlugins = org.jetbrains.plugins.terminal

kotlin.stdlib.default.dependency = false
org.gradle.caching = true
```

### 3.2 `build.gradle.kts`

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.plugins.terminal")
        pluginVerifier()
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }  // 不限制上限，兼容未来版本
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}
```

### 3.3 `plugin.xml`

```xml
<idea-plugin>
    <id>com.github.ccbar</id>
    <name>CCBar</name>
    <vendor>ccbar</vendor>
    <description>Quick Command Launcher for IDEA Toolbar</description>

    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.plugins.terminal</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- 配置持久化 -->
        <applicationService
            serviceImplementation="com.github.ccbar.settings.CCBarSettings"/>

        <!-- 设置页面 -->
        <applicationConfigurable
            parentId="tools"
            instance="com.github.ccbar.settings.CCBarSettingsConfigurable"
            id="com.github.ccbar.settings"
            displayName="CCBar"/>

        <!-- 通知组 -->
        <notificationGroup id="CCBar" displayType="BALLOON"/>
    </extensions>

    <actions>
        <!-- 动态注册：工具栏按钮通过 DynamicActionGroup 在运行时根据配置创建 -->
        <group id="CCBar.ToolbarGroup"
               class="com.github.ccbar.actions.CCBarToolbarActionGroup"
               text="CCBar" description="CCBar Quick Command Launcher">
            <add-to-group group-id="MainToolbarRight" anchor="first"/>
        </group>
    </actions>
</idea-plugin>
```

---

## 4. 核心技术方案

### 4.1 工具栏按钮（Action System）

**方案：DynamicActionGroup 动态注册**

工具栏按钮数量由用户配置决定，使用 `ActionGroup` 动态生成子 Action。

```
CCBarToolbarActionGroup (ActionGroup, 注册到 MainToolbarRight)
  ├── CCBarCommandBarAction("Claude Code")   ← 动态生成，每个 CommandBar 对应一个
  ├── CCBarCommandBarAction("Dev Tools")
  └── ...
```

- `CCBarToolbarActionGroup` 继承 `ActionGroup`，重写 `getChildren()` 根据配置动态返回按钮 Action
- 每个 `CCBarCommandBarAction` 是一个独立的 toolbar 按钮
- 点击按钮时通过 `JBPopup` 弹出自定义菜单（见 4.2）
- 按钮图标通过配置的 `icon` 字段加载（支持内置图标和自定义文件）

**关键类：**
- `com.intellij.openapi.actionSystem.ActionGroup`
- `com.intellij.openapi.actionSystem.AnAction`
- `com.intellij.openapi.actionSystem.ActionManager`

### 4.2 弹出菜单（自定义 JBPopup）

**方案：JBPopupFactory + 自定义 Swing 面板**

为匹配 spec 中快捷参数内联显示的布局，使用 `JBPopupFactory.createComponentPopupBuilder()` 构建自定义弹出面板。

```
┌──────────────────────────────────────┐
│ Model     [Default][Sonnet][Opus]    │  ← 每行是一个 JPanel (FlowLayout)
│ Workspace [Home][Work]               │  ← Command 名称是 JLabel（可点击）
│ System    [Dev]                      │  ← QuickParam 是 JButton
└──────────────────────────────────────┘
```

**面板结构：**

```
JPanel (BoxLayout.Y_AXIS)            ← 弹出菜单主面板
  ├── CommandRowPanel (FlowLayout)   ← 每个 Command 一行
  │   ├── JLabel("Model")           ← 点击执行 baseCommand
  │   ├── JButton("Default")        ← 点击执行 baseCommand + params
  │   ├── JButton("Sonnet")
  │   └── JButton("Opus")
  ├── CommandRowPanel
  │   ├── JLabel("Workspace")
  │   ├── JButton("Home")
  │   └── JButton("Work")
  └── ...
```

**关键 API：**

```kotlin
val popup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(menuPanel, preferredFocusComponent)
    .setRequestFocus(true)
    .setCancelOnClickOutside(true)
    .setCancelOnWindowDeactivation(true)
    .setMovable(false)
    .setResizable(false)
    .createPopup()

// 显示在按钮下方
popup.showUnderneathOf(actionEvent.inputEvent.component)
```

**交互逻辑：**
1. 点击 Command 名称 → 弹出终端命名对话框 → 执行 `baseCommand`
2. 点击 QuickParam → 弹出终端命名对话框 → 执行 `baseCommand + params`
3. 点击后自动关闭弹出菜单

### 4.3 终端命名对话框

**方案：Messages.showInputDialog**

```kotlin
val terminalName = Messages.showInputDialog(
    project,
    "Enter terminal name:",
    "Terminal Name",
    null,                          // icon
    option.defaultTerminalName,    // 默认值
    null                           // validator
)
// terminalName 为 null 表示用户点击了 Cancel
if (terminalName != null) {
    // 创建终端
}
```

### 4.4 终端创建与管理

**方案：Terminal 工具窗口 API（Reworked Terminal 优先，Classic 回退）**

使用 IntelliJ 内置的 Terminal 工具窗口 API 创建终端标签页。终端在底部 Terminal 工具窗口中打开（而非编辑器区域）。

针对不同 IntelliJ 版本，采用两套 API 方案：

- **2025.2+（Reworked Terminal）**：使用 `TerminalToolWindowTabsManager.createTabBuilder()` 创建终端标签
- **2024.2 - 2025.1（Classic Terminal）**：使用 `TerminalView.createLocalShellWidget()` 创建终端标签

通过运行时检测可用 API 来决定使用哪套方案。

**核心流程：**

```
用户点击 → 命名对话框 → CCBarTerminalService.openTerminal()
    → 检测 Terminal API 版本
    ├── Reworked Terminal（2025.2+）：
    │   1. TerminalToolWindowTabsManager.createTabBuilder()
    │   2. .withWorkingDirectory(dir)
    │   3. .withTabName(name)
    │   4. .withShellCommand(ShellStartupOptions)
    │   5. .build()
    │   6. TerminalView.sendText() / createSendTextBuilder().shouldExecute() 发送命令
    └── Classic Terminal（2024.2 - 2025.1）：
        1. TerminalView.createLocalShellWidget(dir, name)
        2. ShellTerminalWidget.executeCommand(command)
```

#### 4.4.1 版本检测与 API 分发

```kotlin
object CCBarTerminalService {

    fun openTerminal(project: Project, command: CommandConfig, quickParam: QuickParamConfig?) {
        val cmd = buildCommand(command, quickParam)
        val terminalName = showNameDialog(project, command) ?: return
        val workingDir = resolveWorkingDirectory(project, command)

        if (isReworkedTerminalAvailable()) {
            openWithReworkedTerminal(project, command, terminalName, workingDir)
        } else {
            openWithClassicTerminal(project, command, terminalName, workingDir)
        }
    }

    private fun isReworkedTerminalAvailable(): Boolean {
        return try {
            Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowTabsManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
```

#### 4.4.2 Reworked Terminal 方式（2025.2+）

```kotlin
private fun openWithReworkedTerminal(
    project: Project, command: String, tabName: String, workingDir: String
) {
    val tabsManager = TerminalToolWindowTabsManager.getInstance(project)
    val tab = tabsManager.createTabBuilder()
        .withTabName(tabName)
        .withWorkingDirectory(workingDir)
        .build()

    // 发送命令
    val terminalView = TerminalView.getInstance(project)
    terminalView.createSendTextBuilder(command)
        .shouldExecute(true)
        .sendTo(tab)
}
```

#### 4.4.3 Classic Terminal 方式（2024.2 - 2025.1）

```kotlin
private fun openWithClassicTerminal(
    project: Project, command: String, tabName: String, workingDir: String
) {
    ApplicationManager.getApplication().invokeLater {
        val terminalView = TerminalView.getInstance(project)
        val widget = terminalView.createLocalShellWidget(workingDir, tabName)
        widget.executeCommand(command)
    }
}
```

**关键依赖类：**

| 类名 | 包 | 用途 | 可用版本 |
|------|-----|------|---------|
| `TerminalView` | `org.jetbrains.plugins.terminal` | 终端工具窗口视图 | 2024.2+ |
| `ShellTerminalWidget` | `org.jetbrains.plugins.terminal` | Classic Terminal Widget | 2024.2 - 2025.1 |
| `TerminalToolWindowTabsManager` | `org.jetbrains.plugins.terminal` | Reworked Terminal 标签管理 | 2025.2+ |

### 4.5 配置持久化

**方案：PersistentStateComponent（应用级）**

```kotlin
@State(
    name = "com.github.ccbar.settings.CCBarSettings",
    storages = [Storage("ccbar.xml")]
)
class CCBarSettings : PersistentStateComponent<CCBarSettings.State> {

    data class State(
        var commandBars: MutableList<CommandBarConfig> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): CCBarSettings =
            ApplicationManager.getApplication().getService(CCBarSettings::class.java)
    }
}
```

**序列化要求：**
- 所有 data class 必须有无参构造函数（所有属性有默认值）
- 使用 `var`（非 `val`）以支持 XML 序列化
- 集合使用 `MutableList`
- 存储文件路径：`<IDEA_CONFIG>/options/ccbar.xml`

**导入/导出：** 使用 `Gson` 进行 JSON 序列化/反序列化，通过 `FileChooser` / `FileSaverDescriptor` 选择文件。

### 4.6 设置界面

**方案：Swing 手动布局 + Kotlin UI DSL v2 混合**

整体布局（JBSplitter、ToolbarDecorator、JBList）使用手动 Swing 构建，详情表单使用 Kotlin UI DSL v2 声明式构建。

**原因：** Kotlin UI DSL v2 官方明确说明不适用于构建通用 UI（如 master-detail 面板），但非常适合表单类组件。

**界面组件映射：**

```
CCBarSettingsPanel (Configurable + NoScroll)
├── JBSplitter (左右分割)
│   ├── 左侧：CommandBarListPanel
│   │   └── JBList<CommandBarConfig> + ToolbarDecorator [+][-][↑][↓]
│   └── 右侧：CommandBarDetailPanel
│       ├── CommandBar 基本信息 (Kotlin UI DSL v2 panel)
│       │   ├── Name: TextField
│       │   └── Icon: TextFieldWithBrowseButton
│       ├── CommandListPanel
│       │   └── JBList<CommandConfig> + ToolbarDecorator [+][-][↑][↓]
│       ├── CommandDetailPanel (Kotlin UI DSL v2 panel)
│       │   ├── Name: TextField
│       │   ├── Base Command: TextField
│       │   ├── Working Directory: TextFieldWithBrowseButton
│       │   └── Default Terminal Name: TextField
│       └── QuickParamTablePanel
│           └── TableView<QuickParamConfig> + ToolbarDecorator [+][-][↑][↓]
│               ├── 列: Name (可编辑)
│               └── 列: Params (可编辑)
└── ActionButtonsPanel
    ├── [Import] [Export] [Reset]
    └── FileChooser / FileSaverDescriptor
```

**关键 UI 组件：**

| 组件 | IntelliJ API | 用途 |
|------|-------------|------|
| 左右分割 | `JBSplitter` | CommandBar 列表与详情分割 |
| 列表 + 工具栏 | `JBList` + `CollectionListModel` + `ToolbarDecorator` | CommandBar/Command 列表的增删排序 |
| 可编辑表格 | `TableView` + `ListTableModel` + `ColumnInfo` | QuickParam 表格编辑 |
| 表单区域 | Kotlin UI DSL v2 `panel { }` | 详情字段绑定 |
| 文件浏览 | `TextFieldWithBrowseButton` | 图标文件选择、工作目录选择 |
| 确认对话框 | `Messages.showYesNoDialog()` | 删除/重置确认 |

**编辑模式：** 设置面板加载时对 State 进行深拷贝（deep copy），所有编辑操作在副本上进行。点击 Apply 时将副本写回 PersistentStateComponent，点击 Cancel 时丢弃副本。

### 4.7 图标处理

| 图标类型 | 存储格式 | 加载方式 |
|---------|---------|---------|
| IDEA 内置图标 | `builtin:AllIcons.Actions.Execute` | 反射加载 `AllIcons` 类的静态字段 |
| 自定义 SVG/PNG | `file:/path/to/icon.svg` | `IconLoader.findIcon(url)` 或 `ImageIcon` |

```kotlin
fun loadIcon(iconPath: String): Icon {
    return when {
        iconPath.startsWith("builtin:") -> {
            val fieldPath = iconPath.removePrefix("builtin:")
            // 通过反射加载 AllIcons 字段
            resolveBuiltinIcon(fieldPath)
        }
        iconPath.startsWith("file:") -> {
            val filePath = iconPath.removePrefix("file:")
            val file = File(filePath)
            if (file.exists()) {
                IconLoader.findIcon(file.toURI().toURL()) ?: AllIcons.Actions.Execute
            } else {
                AllIcons.Actions.Execute
            }
        }
        else -> AllIcons.Actions.Execute  // 降级默认图标
    }
}
```

---

## 5. 版本兼容性说明

### 5.1 Terminal API 演进

| 版本范围 | API 状态 | 本插件策略 |
|---------|---------|-----------|
| 2024.2 - 2025.1 | Classic Terminal：`TerminalView.createLocalShellWidget()` + `ShellTerminalWidget.executeCommand()` | 使用 Classic API 创建终端并执行命令 |
| 2025.2+ | Reworked Terminal 成为默认，Classic API deprecated | 优先使用 Reworked API（`TerminalToolWindowTabsManager`），运行时检测后回退 Classic |

**兼容策略：** 本插件通过运行时 `Class.forName()` 检测 Reworked Terminal API 是否可用。2025.2+ 优先使用 Reworked Terminal API，2024.2 - 2025.1 回退使用 Classic Terminal API。两套方案均在 Terminal 工具窗口中创建标签页。

### 5.2 New UI 兼容

IntelliJ 2024.2+ 默认启用 New UI。本插件需要确保：
- 工具栏按钮在 New UI 的 `MainToolbarRight` 中正确显示
- 自定义弹出菜单适配 New UI 主题样式（使用 JBUI 边距和颜色 API）

---

## 6. 依赖清单

| 依赖 | 类型 | 说明 |
|------|------|------|
| `com.intellij.modules.platform` | 平台插件 | IntelliJ 平台核心 |
| `org.jetbrains.plugins.terminal` | 捆绑插件 | Terminal 功能（TerminalView、ShellTerminalWidget、TerminalToolWindowTabsManager） |
| `com.google.code.gson:gson` | 第三方库 | JSON 导入/导出（可选，也可用 kotlinx.serialization） |

> 注意：IntelliJ Platform 已内置 Gson，无需额外声明依赖。

---

## 7. 关键技术风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| Reworked Terminal API 在 2025.2 尚不稳定或接口变更 | 中 | 运行时检测 + Classic 回退，两套方案互为备份 |
| Classic Terminal API 在未来版本被移除 | 低 | 关注 JetBrains 废弃计划，及时迁移到 Reworked API |
| 自定义弹出菜单在 New UI 下样式不一致 | 低 | 使用 JBUI API 获取主题颜色和边距 |

---

## 8. 开发阶段规划

### Phase 1: 基础框架
- 项目脚手架搭建（Gradle + plugin.xml）
- 配置数据模型 + PersistentStateComponent
- 默认配置初始化

### Phase 2: 核心功能
- 工具栏动态按钮（DynamicActionGroup）
- 自定义 JBPopup 弹出菜单
- 终端命名对话框
- 终端创建（Terminal 工具窗口 API，Reworked + Classic 双路径）

### Phase 3: 设置界面
- 设置面板主框架（JBSplitter + JBList）
- CommandBar / Command / QuickParam 的 CRUD 操作
- 数据验证

### Phase 4: 完善功能
- 配置导入/导出（JSON）
- 配置重置
- 自定义图标支持（内置 + SVG/PNG）
- 错误处理与通知

### Phase 5: 测试与发布
- 多版本兼容测试（2024.2 / 2025.x）
- New UI 兼容测试
- Plugin Verifier 检查
- JetBrains Marketplace 发布