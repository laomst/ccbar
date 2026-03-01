# CCBar - 开发计划与任务列表

## 任务状态说明

| 状态 | 标记 | 说明 |
|------|------|------|
| 待开发 | `[ ]` | 尚未开始 |
| 进行中 | `[~]` | 正在开发 |
| 已完成 | `[x]` | 开发完成 |
| 已跳过 | `[-]` | 决定不做或推迟 |

---

## 阶段依赖关系

```
Phase 1: 基础框架
    │
    ├──→ Phase 2: 核心功能
    │       │
    │       └──→ Phase 5: 测试与发布（需 Phase 2~4 全部完成）
    │
    ├──→ Phase 3: 设置界面
    │       │
    │       └──→ Phase 5
    │
    └──→ Phase 4: 完善功能（依赖 Phase 2 + Phase 3 的部分任务）
            │
            └──→ Phase 5
```

> Phase 2（核心功能）和 Phase 3（设置界面）可以**并行开发**，两者都只依赖 Phase 1。
> Phase 4 中的部分任务（如导入/导出）依赖 Phase 3 的设置面板，图标功能可提前开发。

---

## Phase 1: 基础框架

> 目标：搭建可编译运行的空插件项目，完成数据模型和持久化层。

### 1.1 项目脚手架搭建

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 1.1.1 | 创建 `settings.gradle.kts`，配置项目名称和插件仓库 | `[ ]` | 无 | `settings.gradle.kts` |
| 1.1.2 | 创建 `gradle.properties`，配置插件元信息和平台版本 | `[ ]` | 无 | `gradle.properties` |
| 1.1.3 | 创建 `build.gradle.kts`，配置 IntelliJ Platform Gradle Plugin 2.x、Kotlin、依赖 Terminal 捆绑插件 | `[ ]` | 1.1.1, 1.1.2 | `build.gradle.kts` |
| 1.1.4 | 创建 `plugin.xml`，声明插件 ID、名称、依赖（platform + terminal）、通知组 | `[ ]` | 1.1.3 | `src/main/resources/META-INF/plugin.xml` |
| 1.1.5 | 配置 Gradle Wrapper（gradlew），确保 `./gradlew build` 可通过 | `[ ]` | 1.1.3 | `gradle/wrapper/*`, `gradlew`, `gradlew.bat` |
| 1.1.6 | 更新 `.gitignore`，排除 build 产物和 IDE 文件 | `[ ]` | 无 | `.gitignore` |

**验收标准：** `./gradlew runIde` 能启动沙箱 IDEA 实例，插件出现在已安装列表中。

### 1.2 配置数据模型

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 1.2.1 | 定义 `QuickParamConfig` data class（id, name, params, icon） | `[ ]` | 1.1.4 | `settings/CCBarSettings.kt` |
| 1.2.2 | 定义 `CommandConfig` data class（id, name, baseCommand, workingDirectory, defaultTerminalName, quickParams） | `[ ]` | 1.2.1 | `settings/CCBarSettings.kt` |
| 1.2.3 | 定义 `CommandBarConfig` data class（id, name, icon, commands） | `[ ]` | 1.2.2 | `settings/CCBarSettings.kt` |
| 1.2.4 | 定义 `State` data class（buttons），作为 PersistentStateComponent 的状态容器 | `[ ]` | 1.2.3 | `settings/CCBarSettings.kt` |
| 1.2.5 | 实现各 data class 的 `deepCopy()` 扩展方法，用于设置界面的编辑隔离 | `[ ]` | 1.2.4 | `settings/CCBarSettings.kt` |

**验收标准：** 所有 data class 有无参构造函数，属性均为 `var` 且有默认值，集合类型为 `MutableList`。

### 1.3 PersistentStateComponent 实现

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 1.3.1 | 实现 `CCBarSettings` 类，添加 `@State` 和 `@Storage("ccbar.xml")` 注解 | `[ ]` | 1.2.5 | `settings/CCBarSettings.kt` |
| 1.3.2 | 实现 `getState()` / `loadState()` 方法 | `[ ]` | 1.3.1 | `settings/CCBarSettings.kt` |
| 1.3.3 | 实现 `getInstance()` 伴生方法，通过 `ApplicationManager.getApplication().getService()` 获取实例 | `[ ]` | 1.3.1 | `settings/CCBarSettings.kt` |
| 1.3.4 | 在 `plugin.xml` 中注册 `<applicationService>` | `[ ]` | 1.3.1 | `plugin.xml` |

**验收标准：** 沙箱 IDEA 中修改配置后重启，配置能正确从 `ccbar.xml` 恢复。

### 1.4 默认配置初始化

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 1.4.1 | 定义默认配置常量（包含一个 "Claude Code" CommandBar、"Model" Command、"Sonnet"/"Opus" QuickParam） | `[ ]` | 1.3.1 | `settings/CCBarSettings.kt` |
| 1.4.2 | 在 `CCBarSettings` 中实现首次加载时自动填充默认配置的逻辑 | `[ ]` | 1.4.1 | `settings/CCBarSettings.kt` |

**验收标准：** 首次安装插件后，配置自动填充为默认值；已有配置时不覆盖。

---

## Phase 2: 核心功能

> 目标：实现从工具栏点击到终端执行命令的完整链路。
> 依赖：Phase 1 全部完成。

### 2.1 工具栏动态按钮

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 2.1.1 | 实现 `CCBarToolbarActionGroup`，继承 `ActionGroup`，重写 `getChildren()` 从 `CCBarSettings` 读取按钮配置动态生成子 Action | `[ ]` | 1.3 | `actions/CCBarToolbarActionGroup.kt` |
| 2.1.2 | 实现 `CCBarCommandBarAction`，每个实例对应一个 `CommandBarConfig`，`actionPerformed()` 中触发弹出菜单 | `[ ]` | 2.1.1 | `actions/CCBarCommandBarAction.kt` |
| 2.1.3 | 在 `plugin.xml` 中注册 `CCBarToolbarActionGroup` 到 `MainToolbarRight` | `[ ]` | 2.1.1 | `plugin.xml` |
| 2.1.4 | 按钮图标加载：先使用固定的内置图标（`AllIcons.Actions.Execute`），图标管理系统在 Phase 4 实现 | `[ ]` | 2.1.2 | `actions/CCBarCommandBarAction.kt` |

**验收标准：** 工具栏出现配置中定义的按钮，点击后能触发事件（暂时可 log 输出验证）。

### 2.2 自定义 JBPopup 弹出菜单

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 2.2.1 | 实现 `CCBarPopupBuilder`，负责根据 `CommandBarConfig` 构建弹出面板 | `[ ]` | 2.1.2 | `actions/CCBarPopupBuilder.kt` |
| 2.2.2 | 实现 Command 行布局：左侧 Command 名称（可点击 JLabel），右侧内联排列 QuickParam（JButton） | `[ ]` | 2.2.1 | `actions/CCBarPopupBuilder.kt` |
| 2.2.3 | 实现快捷参数过多时的自动换行布局（使用 `FlowLayout` 或 `WrapLayout`） | `[ ]` | 2.2.2 | `actions/CCBarPopupBuilder.kt` |
| 2.2.4 | 使用 `JBPopupFactory.createComponentPopupBuilder()` 创建弹出窗口，配置 `requestFocus`、`cancelOnClickOutside` 等属性 | `[ ]` | 2.2.2 | `actions/CCBarPopupBuilder.kt` |
| 2.2.5 | 弹出菜单的主题适配：使用 `JBUI.Borders`、`JBColor` 确保 Light/Dark/New UI 下样式正确 | `[ ]` | 2.2.4 | `actions/CCBarPopupBuilder.kt` |
| 2.2.6 | 将弹出菜单集成到 `CCBarCommandBarAction.actionPerformed()`，点击按钮时显示在按钮下方 | `[ ]` | 2.1.2, 2.2.4 | `actions/CCBarCommandBarAction.kt` |

**验收标准：** 点击工具栏按钮弹出菜单，每行显示 Command 名称和内联快捷参数，点击后菜单关闭。

### 2.3 终端命名对话框

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 2.3.1 | 实现终端命名对话框，使用 `Messages.showInputDialog()`，默认名称取自 `CommandConfig.defaultTerminalName` | `[ ]` | 1.2.3 | `terminal/CCBarTerminalService.kt` |
| 2.3.2 | 处理用户点击 Cancel 的情况（返回 null 时终止流程，不创建终端） | `[ ]` | 2.3.1 | `terminal/CCBarTerminalService.kt` |

**验收标准：** 弹出对话框显示默认名称，用户可编辑后确认或取消。

### 2.4 终端创建（Terminal 工具窗口 API）

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 2.4.1 | 实现 Terminal API 版本检测：通过 `Class.forName()` 判断 Reworked Terminal API 是否可用 | `[ ]` | 1.1.4 | `terminal/CCBarTerminalService.kt` |
| 2.4.2 | 实现 Classic Terminal 创建方式：使用 `TerminalView.createLocalShellWidget(workingDir, tabName)` 创建终端标签 | `[ ]` | 2.4.1 | `terminal/CCBarTerminalService.kt` |
| 2.4.3 | 实现 Classic Terminal 命令执行：通过 `ShellTerminalWidget.executeCommand(command)` 发送命令 | `[ ]` | 2.4.2 | `terminal/CCBarTerminalService.kt` |
| 2.4.4 | 实现 Reworked Terminal 创建方式：使用 `TerminalToolWindowTabsManager.createTabBuilder()` 创建终端标签 | `[ ]` | 2.4.1 | `terminal/CCBarTerminalService.kt` |
| 2.4.5 | 实现 Reworked Terminal 命令执行：通过 `TerminalView.createSendTextBuilder().shouldExecute()` 发送命令 | `[ ]` | 2.4.4 | `terminal/CCBarTerminalService.kt` |
| 2.4.6 | 实现工作目录解析：优先使用 `CommandConfig.workingDirectory`，为空时使用当前项目根目录 | `[ ]` | 2.4.2 | `terminal/CCBarTerminalService.kt` |

**验收标准：** 调用 `CCBarTerminalService.openTerminal()` 后，Terminal 工具窗口中出现新标签页，终端中自动执行指定命令。Classic 和 Reworked 两种路径均可正常工作。

### 2.5 完整链路集成

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 2.5.1 | 实现 `CCBarTerminalService.openTerminal(project, command, quickParam?)`，串联完整流程：命令拼接 → 命名对话框 → 创建终端标签 → 执行命令 | `[ ]` | 2.3, 2.4 | `terminal/CCBarTerminalService.kt` |
| 2.5.2 | 在弹出菜单中绑定点击事件：点击 Command 名称调用 `openTerminal(command, null)`，点击 QuickParam 调用 `openTerminal(command, quickParam)` | `[ ]` | 2.2.6, 2.5.1 | `actions/CCBarPopupBuilder.kt` |

**验收标准：** 从工具栏点击到终端执行命令的完整流程可走通——点击按钮 → 弹出菜单 → 选择 Command/QuickParam → 命名对话框 → Terminal 工具窗口中新建终端标签 → 执行命令。

---

## Phase 3: 设置界面

> 目标：实现完整的可视化配置面板，支持 CommandBar/Command/QuickParam 的 CRUD 和排序。
> 依赖：Phase 1 全部完成。可与 Phase 2 并行开发。

### 3.1 设置入口

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.1.1 | 实现 `CCBarSettingsConfigurable`，继承 `Configurable` + `Configurable.NoScroll` | `[ ]` | 1.3 | `settings/CCBarSettingsConfigurable.kt` |
| 3.1.2 | 实现 `createComponent()` 返回主设置面板，`isModified()` / `apply()` / `reset()` 委托给面板 | `[ ]` | 3.1.1 | `settings/CCBarSettingsConfigurable.kt` |
| 3.1.3 | 在 `plugin.xml` 中注册 `<applicationConfigurable>`，`parentId="tools"`，`displayName="CCBar"` | `[ ]` | 3.1.1 | `plugin.xml` |

**验收标准：** Settings → Tools → CCBar 页面可打开，显示空白设置面板。

### 3.2 主面板框架

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.2.1 | 实现 `CCBarSettingsPanel`，使用 `JBSplitter` 构建左右分栏布局 | `[ ]` | 3.1.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.2.2 | 实现 State 深拷贝编辑模式：`reset()` 时从持久化层深拷贝，`apply()` 时写回，`isModified()` 比较差异 | `[ ]` | 3.2.1, 1.2.6 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 设置面板显示左右分栏，Apply/Cancel 按钮行为正确（Apply 保存修改，Cancel 丢弃修改）。

### 3.3 CommandBar 列表面板

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.3.1 | 实现 `CommandBarListPanel`，使用 `JBList` + `CollectionListModel<CommandBarConfig>` + `ToolbarDecorator` 构建按钮列表，支持增删和上下排序 | `[ ]` | 3.2.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.3.2 | 实现自定义 `ColoredListCellRenderer`，显示 CommandBar 名称 | `[ ]` | 3.3.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.3.3 | 实现列表选中事件监听：选中 CommandBar 时右侧面板切换显示对应 CommandBar 的详情 | `[ ]` | 3.3.1, 3.2.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.3.4 | 添加按钮时自动生成 UUID 作为 id，设置默认名称 "New CommandBar" | `[ ]` | 3.3.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.3.5 | 删除按钮时弹出确认对话框（`Messages.showYesNoDialog`） | `[ ]` | 3.3.1 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 左侧列表可增删、排序 CommandBar，选中后右侧联动显示详情。

### 3.4 CommandBar 详情面板

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.4.1 | 使用 Kotlin UI DSL v2 构建 CommandBar 详情表单：Name 文本框、Icon 文本框（Phase 4 升级为带浏览按钮） | `[ ]` | 3.3.3 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.4.2 | 实现表单字段与选中 `CommandBarConfig` 的双向绑定（选中切换时刷新，编辑时回写） | `[ ]` | 3.4.1 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 选中 CommandBar 后右侧显示其 Name 和 Icon，修改后切换回来数据不丢失。

### 3.5 Command 列表与详情面板

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.5.1 | 在 CommandBar 详情面板下方构建 Command 列表，使用 `JBList` + `CollectionListModel<CommandConfig>` + `ToolbarDecorator`，支持增删和排序 | `[ ]` | 3.4.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.5.2 | 选中 CommandBar 时刷新 Command 列表内容为该 CommandBar 的 commands | `[ ]` | 3.5.1, 3.3.3 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.5.3 | 使用 Kotlin UI DSL v2 构建 Command 详情表单：Name、Base Command、Working Directory、Default Terminal Name | `[ ]` | 3.5.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.5.4 | 实现 Command 详情字段的双向绑定，选中 Command 时刷新表单 | `[ ]` | 3.5.3 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.5.5 | Working Directory 字段添加目录浏览功能（`TextFieldWithBrowseButton` + `FileChooserDescriptorFactory.createSingleFolderDescriptor()`） | `[ ]` | 3.5.3 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 选中 CommandBar 后显示其 Command 列表，选中 Command 后显示其详情表单，所有字段可编辑。

### 3.6 QuickParam 表格面板

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.6.1 | 实现 `QuickParamTablePanel`，使用 `TableView` + `ListTableModel` + `ColumnInfo` 构建可编辑表格，列为 Name 和 Params | `[ ]` | 3.5.3 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.6.2 | 使用 `ToolbarDecorator` 包装表格，提供增删和排序按钮 | `[ ]` | 3.6.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.6.3 | 实现表格行内编辑：点击单元格直接编辑 Name 和 Params | `[ ]` | 3.6.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.6.4 | 选中 Command 时刷新 QuickParam 表格内容 | `[ ]` | 3.6.1, 3.5.4 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** Command 详情下方显示 QuickParam 表格，可增删、排序、行内编辑。

### 3.7 数据验证

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 3.8.1 | CommandBar Name 验证：必填，全局唯一 | `[ ]` | 3.4.2 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.2 | Command Name 验证：必填，同一 CommandBar 下唯一 | `[ ]` | 3.5.4 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.3 | Base Command 验证：必填 | `[ ]` | 3.5.4 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.4 | Default Terminal Name 验证：必填 | `[ ]` | 3.5.4 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.5 | QuickParam Name 验证：必填，同一 Command 下唯一 | `[ ]` | 3.6.3 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.6 | Working Directory 验证：如填写须为有效路径 | `[ ]` | 3.5.5 | `settings/ui/CCBarSettingsPanel.kt` |
| 3.8.7 | 验证不通过时在 `ConfigurationException` 中给出明确错误提示 | `[ ]` | 3.8.1 ~ 3.8.6 | `settings/CCBarSettingsConfigurable.kt` |

**验收标准：** Apply 时自动执行验证，不合法数据弹出错误提示并阻止保存。

---

## Phase 4: 完善功能

> 目标：补全配置管理、图标系统和错误处理。
> 依赖：Phase 2 和 Phase 3 的核心部分完成。

### 4.1 图标加载与管理

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 4.1.1 | 实现 `CCBarIcons.loadIcon(iconPath)`，支持 `builtin:` 前缀（反射加载 `AllIcons` 字段）和 `file:` 前缀（`IconLoader` 加载 SVG/PNG） | `[ ]` | 1.1.4 | `icons/CCBarIcons.kt` |
| 4.1.2 | 实现图标加载失败时的降级策略：文件不存在或路径无效时使用默认图标，弹出 Notification 通知用户 | `[ ]` | 4.1.1 | `icons/CCBarIcons.kt` |
| 4.1.3 | 将图标系统集成到工具栏按钮：`CCBarCommandBarAction` 使用 `CCBarIcons.loadIcon()` 加载配置中的图标 | `[ ]` | 4.1.1, 2.1.4 | `actions/CCBarCommandBarAction.kt` |
| 4.1.4 | 将图标系统集成到弹出菜单的 QuickParam（如果 QuickParam 配置了 icon） | `[ ]` | 4.1.1, 2.2.2 | `actions/CCBarPopupBuilder.kt` |

**验收标准：** 工具栏按钮显示配置的图标，内置图标和自定义 SVG/PNG 均可正确加载，加载失败时降级且有通知。

### 4.2 设置界面图标浏览

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 4.2.1 | 将 CommandBar 详情中的 Icon 文本框升级为 `TextFieldWithBrowseButton`，支持浏览选择 SVG/PNG 文件 | `[ ]` | 3.4.1, 4.1.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 4.2.2 | Icon 字段提供内置图标的下拉预选（可选增强，或保持手动输入 `builtin:` 路径） | `[ ]` | 4.2.1 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 设置界面可通过浏览按钮选择图标文件，或手动输入内置图标路径。

### 4.3 配置导入/导出

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 4.3.1 | 实现配置导出：将当前 State 序列化为 JSON，通过 `FileSaverDescriptor` 选择保存路径 | `[ ]` | 1.3, 3.2.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 4.3.2 | 实现配置导入：通过 `FileChooserDescriptorFactory` 选择 JSON 文件，反序列化为 State 并加载到编辑面板 | `[ ]` | 1.3, 3.2.1 | `settings/ui/CCBarSettingsPanel.kt` |
| 4.3.3 | 导入 JSON 格式错误时弹出错误对话框（`Messages.showErrorDialog`），不覆盖当前配置 | `[ ]` | 4.3.2 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** Export 生成格式正确的 JSON 文件，Import 能正确加载并刷新设置面板，格式错误时有友好提示。

### 4.4 配置重置

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 4.4.1 | 实现 Reset 功能：弹出确认对话框，确认后将编辑状态恢复为默认配置（复用 1.4.1 的默认配置常量） | `[ ]` | 1.4.1, 3.2.1 | `settings/ui/CCBarSettingsPanel.kt` |

**验收标准：** 点击 Reset 后确认，设置面板恢复为默认配置（未点 Apply 前不影响已保存配置）。

### 4.5 错误处理与通知

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 4.5.1 | 终端创建失败时弹出 IDEA Notification 提示错误原因 | `[ ]` | 2.4.2 | `terminal/CCBarTerminalService.kt` |
| 4.5.2 | 自定义工作目录不存在时回退到项目根目录，弹出 Notification 通知 | `[ ]` | 2.4.6 | `terminal/CCBarTerminalService.kt` |
| 4.5.3 | 配置文件损坏时自动回退到默认配置，弹出 Notification 告知用户 | `[ ]` | 1.3, 1.4.2 | `settings/CCBarSettings.kt` |
| 4.5.4 | 创建统一的 `NotificationGroup` 用于所有插件通知 | `[ ]` | 1.1.4 | `plugin.xml` |

**验收标准：** 各异常场景有明确的通知提示，不会静默失败或抛出未捕获异常。

---

## Phase 5: 测试与发布

> 目标：确保跨版本兼容性，通过 Plugin Verifier，发布到 Marketplace。
> 依赖：Phase 2 ~ Phase 4 全部完成。

### 5.1 兼容性测试

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 5.1.1 | IntelliJ IDEA 2024.2（最低版本）功能测试 | `[ ]` | Phase 2~4 | - |
| 5.1.2 | IntelliJ IDEA 2024.3 功能测试 | `[ ]` | Phase 2~4 | - |
| 5.1.3 | IntelliJ IDEA 2025.x 功能测试（重点验证 Reworked Terminal 路径） | `[ ]` | Phase 2~4 | - |
| 5.1.4 | New UI 模式下的 UI 显示测试（工具栏按钮、弹出菜单、设置面板） | `[ ]` | Phase 2~4 | - |

### 5.2 功能回归测试

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 5.2.1 | 测试完整流程：工具栏 → 弹出菜单 → 命名对话框 → 终端创建 → 命令执行 | `[ ]` | Phase 2 | - |
| 5.2.2 | 测试设置界面：CommandBar/Command/QuickParam 的增删改排序、Apply/Cancel 行为 | `[ ]` | Phase 3 | - |
| 5.2.3 | 测试配置导入/导出/重置 | `[ ]` | Phase 4 | - |
| 5.2.4 | 测试错误场景：无效命令、不存在的工作目录、损坏的配置文件、无效的 JSON 导入 | `[ ]` | Phase 4 | - |
| 5.2.5 | 测试配置持久化：修改配置后重启 IDEA，验证配置恢复 | `[ ]` | Phase 1 | - |

### 5.3 发布准备

| ID | 任务 | 状态 | 依赖 | 产出文件 |
|----|------|------|------|---------|
| 5.3.1 | 运行 `./gradlew verifyPlugin`，修复所有 Plugin Verifier 报告的问题 | `[ ]` | Phase 2~4 | - |
| 5.3.2 | 编写 `plugin.xml` 中的 `<description>` 和 `<change-notes>` | `[ ]` | 5.3.1 | `plugin.xml` |
| 5.3.3 | 准备 Marketplace 发布所需的插件图标（pluginIcon.svg，40x40 和 80x80） | `[ ]` | 5.3.1 | `src/main/resources/META-INF/pluginIcon.svg` |
| 5.3.4 | 运行 `./gradlew buildPlugin` 生成可分发 ZIP 包 | `[ ]` | 5.3.1 | `build/distributions/` |
| 5.3.5 | 提交到 JetBrains Marketplace 审核 | `[ ]` | 5.3.2, 5.3.3, 5.3.4 | - |

---

## 任务统计

| 阶段 | 任务数 | 状态 |
|------|--------|------|
| Phase 1: 基础框架 | 16 | `[ ]` |
| Phase 2: 核心功能 | 16 | `[ ]` |
| Phase 3: 设置界面 | 18 | `[ ]` |
| Phase 4: 完善功能 | 13 | `[ ]` |
| Phase 5: 测试与发布 | 14 | `[ ]` |
| **合计** | **77** | |