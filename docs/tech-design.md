# CCTab - 技术选型与架构设计

## 1. 技术栈总览

| 维度 | 选型 | 说明 |
|------|------|------|
| 开发语言 | **Kotlin** | JetBrains 官方推荐，支持 UI DSL v2、data class、协程 |
| 构建工具 | **Gradle + Kotlin DSL** | `build.gradle.kts`，IntelliJ 插件开发标准 |
| Gradle 插件 | **IntelliJ Platform Gradle Plugin 2.x** | `org.jetbrains.intellij.platform`，新项目推荐 |
| 最低兼容版本 | **IntelliJ IDEA 2023.1** (Build 231) | 覆盖近 3 年版本，Terminal API 稳定可用 |
| JDK | **JDK 17** | IntelliJ 2023.1+ 的运行时要求 |

---

## 2. 项目结构

```
cctab/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/com/github/cctab/
│       │   ├── actions/                    # Action 相关
│       │   │   ├── CCTabToolbarAction.kt       # 工具栏按钮 Action
│       │   │   └── CCTabPopupBuilder.kt        # 自定义弹出菜单构建
│       │   ├── terminal/                   # 终端相关
│       │   │   ├── CCTabVirtualFile.kt         # 自定义 VirtualFile
│       │   │   ├── CCTabFileType.kt            # 自定义 FileType（控制图标）
│       │   │   ├── CCTabTerminalEditor.kt      # 自定义 FileEditor（嵌入终端）
│       │   │   ├── CCTabTerminalEditorProvider.kt  # FileEditorProvider
│       │   │   └── CCTabTerminalService.kt     # 终端创建服务
│       │   ├── settings/                   # 设置相关
│       │   │   ├── CCTabSettings.kt            # PersistentStateComponent
│       │   │   ├── CCTabSettingsConfigurable.kt # Configurable 入口
│       │   │   └── ui/                     # 设置界面组件
│       │   │       ├── CCTabSettingsPanel.kt       # 主设置面板
│       │   │       ├── ButtonListPanel.kt          # 按钮列表面板
│       │   │       ├── OptionDetailPanel.kt        # Option 详情面板
│       │   │       └── SubButtonTablePanel.kt      # 子按钮表格面板
│       │   └── icons/                      # 图标工具
│       │       └── CCTabIcons.kt               # 图标加载与管理
│       └── resources/
│           └── META-INF/
│               └── plugin.xml              # 插件描述文件
└── docs/
    ├── spec.md                             # 需求文档
    └── tech-design.md                      # 本文档
```

---

## 3. 构建配置

### 3.1 `gradle.properties`

```properties
pluginGroup = com.github.cctab
pluginName = CCTab
pluginVersion = 1.0.0

platformType = IC
platformVersion = 2023.1
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
        instrumentationTools()
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
            sinceBuild = "231"
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
    <id>com.github.cctab</id>
    <name>CCTab</name>
    <vendor>cctab</vendor>
    <description>Quick Command Launcher for IDEA Toolbar</description>

    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.plugins.terminal</depends>

    <extensions defaultExtensionNs="com.intellij">
        <!-- 配置持久化 -->
        <applicationService
            serviceImplementation="com.github.cctab.settings.CCTabSettings"/>

        <!-- 设置页面 -->
        <applicationConfigurable
            parentId="tools"
            instance="com.github.cctab.settings.CCTabSettingsConfigurable"
            id="com.github.cctab.settings"
            displayName="CCTab"/>

        <!-- 终端编辑器 -->
        <fileEditorProvider
            implementation="com.github.cctab.terminal.CCTabTerminalEditorProvider"/>
    </extensions>

    <actions>
        <!-- 动态注册：工具栏按钮通过 DynamicActionGroup 在运行时根据配置创建 -->
        <group id="CCTab.ToolbarGroup"
               class="com.github.cctab.actions.CCTabToolbarActionGroup"
               text="CCTab" description="CCTab Quick Command Launcher">
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
CCTabToolbarActionGroup (ActionGroup, 注册到 MainToolbarRight)
  ├── CCTabButtonAction("Claude Code")   ← 动态生成，每个 Button 对应一个
  ├── CCTabButtonAction("Dev Tools")
  └── ...
```

- `CCTabToolbarActionGroup` 继承 `ActionGroup`，重写 `getChildren()` 根据配置动态返回按钮 Action
- 每个 `CCTabButtonAction` 是一个独立的 toolbar 按钮
- 点击按钮时通过 `JBPopup` 弹出自定义菜单（见 4.2）
- 按钮图标通过配置的 `icon` 字段加载（支持内置图标和自定义文件）

**关键类：**
- `com.intellij.openapi.actionSystem.ActionGroup`
- `com.intellij.openapi.actionSystem.AnAction`
- `com.intellij.openapi.actionSystem.ActionManager`

### 4.2 弹出菜单（自定义 JBPopup）

**方案：JBPopupFactory + 自定义 Swing 面板**

为匹配 spec 中子按钮内联显示的布局，使用 `JBPopupFactory.createComponentPopupBuilder()` 构建自定义弹出面板。

```
┌──────────────────────────────────────┐
│ Model     [Default][Sonnet][Opus]    │  ← 每行是一个 JPanel (FlowLayout)
│ Workspace [Home][Work]               │  ← Option 名称是 JLabel（可点击）
│ System    [Dev]                      │  ← SubButton 是 JButton
└──────────────────────────────────────┘
```

**面板结构：**

```
JPanel (BoxLayout.Y_AXIS)            ← 弹出菜单主面板
  ├── OptionRowPanel (FlowLayout)    ← 每个 Option 一行
  │   ├── JLabel("Model")           ← 点击执行 baseCommand
  │   ├── JButton("Default")        ← 点击执行 baseCommand + params
  │   ├── JButton("Sonnet")
  │   └── JButton("Opus")
  ├── OptionRowPanel
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
1. 点击 Option 名称 → 弹出终端命名对话框 → 执行 `baseCommand`
2. 点击 SubButton → 弹出终端命名对话框 → 执行 `baseCommand + params`
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

**方案：自定义 FileEditor 嵌入终端 Widget**

不使用 `TerminalToolWindowManager`（终端工具窗口 API），而是直接创建 PTY 进程并嵌入自定义 FileEditor，这样终端天然就在编辑器区域打开。

**核心流程：**

```
用户点击 → 命名对话框 → 创建 CCTabVirtualFile
    → FileEditorManager.openFile()
    → CCTabTerminalEditorProvider.createEditor()
    → CCTabTerminalEditor 内部：
        1. LocalTerminalDirectRunner 创建 PtyProcess
        2. PtyProcessTtyConnector 连接进程
        3. JBTerminalWidget 渲染终端 UI
        4. executeCommand() 执行命令
    → EditorWindow.setFilePinned() 固定标签页
```

**关键组件说明：**

#### 4.4.1 CCTabVirtualFile

继承 `LightVirtualFile`（内存虚拟文件），携带终端所需的元数据。

```kotlin
class CCTabVirtualFile(
    name: String,                    // 标签页显示名称
    val command: String,             // 要执行的命令
    val workingDirectory: String     // 工作目录
) : LightVirtualFile(name, CCTabFileType.INSTANCE, "") {

    override fun isWritable(): Boolean = false
    override fun isValid(): Boolean = true

    // 每次创建都是新实例，确保不复用编辑器标签
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
```

#### 4.4.2 CCTabFileType

自定义 FileType，控制编辑器标签的图标。

```kotlin
class CCTabFileType private constructor() : FileType {
    companion object {
        val INSTANCE = CCTabFileType()
    }
    override fun getName(): String = "CCTab Terminal"
    override fun getDefaultExtension(): String = "cctab"
    override fun getIcon(): Icon = AllIcons.Nodes.Console
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
}
```

#### 4.4.3 CCTabTerminalEditorProvider

判断 VirtualFile 类型并创建对应的 FileEditor。

```kotlin
class CCTabTerminalEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file is CCTabVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        CCTabTerminalEditor(project, file as CCTabVirtualFile)

    override fun getEditorTypeId(): String = "CCTabTerminalEditor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
```

#### 4.4.4 CCTabTerminalEditor

核心组件，在 FileEditor 内嵌入终端 Widget。

```kotlin
class CCTabTerminalEditor(
    private val project: Project,
    private val file: CCTabVirtualFile
) : FileEditor, Disposable {

    private val mainPanel = JPanel(BorderLayout())

    init {
        createTerminal()
    }

    private fun createTerminal() {
        // 1. 创建终端设置
        val settingsProvider = JBTerminalSystemSettingsProvider()

        // 2. 创建终端 Widget
        val widget = JBTerminalWidget(project, settingsProvider, this)

        // 3. 创建 PTY 进程
        val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
        val process = runner.createProcess(
            ShellStartupOptions.Builder()
                .workingDirectory(file.workingDirectory)
                .build()
        )

        // 4. 连接进程到终端
        val connector = PtyProcessTtyConnector(process, Charsets.UTF_8)
        widget.createTerminalSession(connector)
        widget.start()

        // 5. 添加到面板
        mainPanel.add(widget.component, BorderLayout.CENTER)

        // 6. 延迟执行命令（等终端 shell 就绪）
        if (file.command.isNotBlank()) {
            widget.executeCommand(file.command)
        }
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = mainPanel
    override fun getName(): String = "CCTab Terminal"
    override fun getFile(): VirtualFile = file
    override fun isValid(): Boolean = true
    override fun isModified(): Boolean = false
    // ... 其他 FileEditor 方法
}
```

#### 4.4.5 标签页固定

```kotlin
fun openAndPinTerminal(project: Project, file: CCTabVirtualFile) {
    ApplicationManager.getApplication().invokeLater {
        // 打开编辑器标签
        FileEditorManager.getInstance(project).openFile(file, true)

        // 根据全局设置决定是否固定
        val settings = CCTabSettings.getInstance()
        if (settings.state.globalSettings.pinTerminalTab) {
            val managerEx = FileEditorManagerEx.getInstanceEx(project)
            managerEx.currentWindow?.setFilePinned(file, true)
        }
    }
}
```

**关键依赖类：**

| 类名 | 包 | 用途 |
|------|-----|------|
| `JBTerminalWidget` | `com.intellij.terminal` | 终端 UI 组件 |
| `JBTerminalSystemSettingsProvider` | `com.intellij.terminal` | 终端设置 |
| `LocalTerminalDirectRunner` | `org.jetbrains.plugins.terminal` | 创建 PTY 进程 |
| `ShellStartupOptions` | `org.jetbrains.plugins.terminal` | Shell 启动配置 |
| `PtyProcessTtyConnector` | `com.jediterm.terminal` | 进程与终端的连接器 |
| `LightVirtualFile` | `com.intellij.testFramework` | 内存虚拟文件 |
| `FileEditorManager` | `com.intellij.openapi.fileEditor` | 编辑器管理 |
| `FileEditorManagerEx` | `com.intellij.openapi.fileEditor.ex` | 扩展编辑器管理（支持固定标签） |

### 4.5 配置持久化

**方案：PersistentStateComponent（应用级）**

```kotlin
@State(
    name = "com.github.cctab.settings.CCTabSettings",
    storages = [Storage("cctab.xml")]
)
class CCTabSettings : PersistentStateComponent<CCTabSettings.State> {

    data class State(
        var buttons: MutableList<ButtonConfig> = mutableListOf(),
        var globalSettings: GlobalSettingsConfig = GlobalSettingsConfig()
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): CCTabSettings =
            ApplicationManager.getApplication().getService(CCTabSettings::class.java)
    }
}
```

**序列化要求：**
- 所有 data class 必须有无参构造函数（所有属性有默认值）
- 使用 `var`（非 `val`）以支持 XML 序列化
- 集合使用 `MutableList`
- 存储文件路径：`<IDEA_CONFIG>/options/cctab.xml`

**导入/导出：** 使用 `Gson` 进行 JSON 序列化/反序列化，通过 `FileChooser` / `FileSaverDescriptor` 选择文件。

### 4.6 设置界面

**方案：Swing 手动布局 + Kotlin UI DSL v2 混合**

整体布局（JBSplitter、ToolbarDecorator、JBList）使用手动 Swing 构建，详情表单使用 Kotlin UI DSL v2 声明式构建。

**原因：** Kotlin UI DSL v2 官方明确说明不适用于构建通用 UI（如 master-detail 面板），但非常适合表单类组件。

**界面组件映射：**

```
CCTabSettingsPanel (Configurable + NoScroll)
├── JBSplitter (左右分割)
│   ├── 左侧：ButtonListPanel
│   │   └── JBList<ButtonConfig> + ToolbarDecorator [+][-][↑][↓]
│   └── 右侧：ButtonDetailPanel
│       ├── Button 基本信息 (Kotlin UI DSL v2 panel)
│       │   ├── Name: TextField
│       │   └── Icon: TextFieldWithBrowseButton
│       ├── OptionListPanel
│       │   └── JBList<OptionConfig> + ToolbarDecorator [+][-][↑][↓]
│       ├── OptionDetailPanel (Kotlin UI DSL v2 panel)
│       │   ├── Name: TextField
│       │   ├── Base Command: TextField
│       │   ├── Working Directory: TextFieldWithBrowseButton
│       │   └── Default Terminal Name: TextField
│       └── SubButtonTablePanel
│           └── TableView<SubButtonConfig> + ToolbarDecorator [+][-][↑][↓]
│               ├── 列: Name (可编辑)
│               └── 列: Params (可编辑)
├── GlobalSettingsPanel (Kotlin UI DSL v2 panel)
│   ├── [✓] Open in editor area
│   └── [✓] Pin terminal tab
└── ActionButtonsPanel
    ├── [Import] [Export] [Reset]
    └── FileChooser / FileSaverDescriptor
```

**关键 UI 组件：**

| 组件 | IntelliJ API | 用途 |
|------|-------------|------|
| 左右分割 | `JBSplitter` | Button 列表与详情分割 |
| 列表 + 工具栏 | `JBList` + `CollectionListModel` + `ToolbarDecorator` | Button/Option 列表的增删排序 |
| 可编辑表格 | `TableView` + `ListTableModel` + `ColumnInfo` | SubButton 表格编辑 |
| 表单区域 | Kotlin UI DSL v2 `panel { }` | 详情字段绑定 |
| 文件浏览 | `TextFieldWithBrowseButton` | 图标文件选择、工作目录选择 |
| 确认对话框 | `Messages.showYesNoDialog()` | 删除/重置确认 |

**编辑模式：** 设置面板加载时对 State 进行深拷贝（deep copy），所有编辑操作在副本上进行。点击 Apply 时将副本写回 PersistentStateComponent，点击 Cancel 时丢弃副本。

### 4.7 图标处理

| 图标类型 | 存储格式 | 加载方式 |
|---------|---------|---------|
| IDEA 内置图标 | `builtin:AllIcons.Actions.Execute` | 反射加载 `AllIcons` 类的静态字段 |
| 自定义 SVG/PNG | `file:/path/to/icon.svg` | `IconLoader.findIcon(path)` 或 `ImageIcon` |

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
            IconLoader.findIcon(Path.of(filePath)) ?: AllIcons.Actions.Execute
        }
        else -> AllIcons.Actions.Execute  // 降级默认图标
    }
}
```

---

## 5. 版本兼容性说明

### 5.1 Terminal API 演进

| 版本范围 | API 状态 | 影响 |
|---------|---------|------|
| 2023.1 - 2024.x | `ShellTerminalWidget.executeCommand()` 可用 | 本插件的主要支持范围 |
| 2025.2+ | Classic Terminal API deprecated，Reworked Terminal 成为默认 | `JBTerminalWidget` 仍然可用 |
| 2025.3+ | 新 `TerminalToolWindowTabsManager` API | 不影响本插件（未使用工具窗口 API） |

**兼容策略：** 本插件采用自定义 FileEditor + 直接创建 PTY 进程的方式，不依赖 `TerminalToolWindowManager`（工具窗口 API），因此对 Terminal API 的版本变更有较好的抗性。核心依赖的 `JBTerminalWidget`（来自 JediTerm 库）在各版本中保持稳定。

### 5.2 New UI 兼容

IntelliJ 2024.2+ 默认启用 New UI。本插件需要确保：
- 工具栏按钮在 New UI 的 `MainToolbarRight` 中正确显示
- 自定义弹出菜单适配 New UI 主题样式（使用 JBUI 边距和颜色 API）

---

## 6. 依赖清单

| 依赖 | 类型 | 说明 |
|------|------|------|
| `com.intellij.modules.platform` | 平台插件 | IntelliJ 平台核心 |
| `org.jetbrains.plugins.terminal` | 捆绑插件 | Terminal 功能（JBTerminalWidget、LocalTerminalDirectRunner） |
| `com.google.code.gson:gson` | 第三方库 | JSON 导入/导出（可选，也可用 kotlinx.serialization） |

> 注意：IntelliJ Platform 已内置 Gson，无需额外声明依赖。

---

## 7. 关键技术风险

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| Terminal API 在 2025.2+ 发生重大变更 | 中 | 采用自定义 FileEditor 方案，不依赖 TerminalToolWindowManager |
| JBTerminalWidget 内部 API 变更 | 中 | 关注 JetBrains Platform 发布日志，必要时通过版本条件分支适配 |
| 自定义弹出菜单在 New UI 下样式不一致 | 低 | 使用 JBUI API 获取主题颜色和边距 |
| 编辑器标签固定 API（EditorWindow）属于 impl 包 | 低 | 该 API 多年稳定，且无替代公共 API |

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
- 终端创建（FileEditor + JBTerminalWidget）
- 终端标签固定

### Phase 3: 设置界面
- 设置面板主框架（JBSplitter + JBList）
- Button / Option / SubButton 的 CRUD 操作
- 全局设置（Open in editor area / Pin terminal tab）
- 数据验证

### Phase 4: 完善功能
- 配置导入/导出（JSON）
- 配置重置
- 自定义图标支持（内置 + SVG/PNG）
- 错误处理与通知

### Phase 5: 测试与发布
- 多版本兼容测试（2023.1 / 2024.x / 2025.x）
- New UI 兼容测试
- Plugin Verifier 检查
- JetBrains Marketplace 发布
