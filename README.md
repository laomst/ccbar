# CCBar

一个 IntelliJ IDEA 插件，在工具栏中添加可配置的快捷按钮，用于快速启动命令行工具（如 AI 编程助手）。

## 功能特性

- **多快捷按钮**：支持在工具栏添加多个按钮，每个按钮代表一个命令类别
- **命令预览**：弹出菜单中实时预览将要执行的命令
- **灵活配置**：支持配置基础命令、参数变体、工作目录等
- **终端集成**：自动在终端中执行命令，支持自定义终端名称

## 使用方式

### 基本操作

1. 点击工具栏上的按钮（如 `🤖 Claude`）
2. 弹出选项菜单，每行显示：
   - **选项名称**：点击执行基础命令
   - **命令预览输入框**：点击执行基础命令；悬浮子按钮时显示完整命令
   - **子按钮列表**：点击执行带参数的完整命令

```
┌──────────────────────────────────────────────────────────────────┐
│ Model     │ claude                    │ [Default][Sonnet][Opus] │
│ Workspace │ claude                    │ [Home][Work]            │
│ System    │ claude                    │ [Dev]                   │
└──────────────────────────────────────────────────────────────────┘
```

### 命令生成规则

```
最终执行命令 = Option.baseCommand + (SubButton.params 不为空 ? " " + SubButton.params : "")
```

| 点击目标 | 执行命令 |
|----------|----------|
| 选项名称 | `claude` |
| 命令预览输入框 | `claude` |
| 子按钮 [Sonnet] | `claude --model sonnet` |

## 配置

进入 **Settings → Tools → CCBar** 进行配置：

- 添加/编辑/删除工具栏按钮
- 为每个按钮配置选项（Option）和子按钮（SubButton）
- 设置基础命令、工作目录、终端名称等
- 支持导入/导出 JSON 格式配置

## 构建

```bash
./gradlew build              # 构建插件
./gradlew runIde             # 启动沙箱 IDEA 实例并加载插件
./gradlew buildPlugin        # 构建可分发的插件 ZIP 包
```

## 兼容性

- IntelliJ IDEA 2023.1+
- JDK 17+

## 文档

- [需求文档](docs/spec.md)
- [技术设计](docs/tech-design.md)
- [开发计划](docs/dev-plan.md)

## License

MIT
