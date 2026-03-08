---
name: Bug Report
description: 报告一个 Bug 或问题
title: "[Bug] 请在此填写简短描述]"
labels: ["bug"]
assignees: []
body:
  - type: markdown
    attributes:
      value: |
        感谢反馈！请填写以下信息帮助我们定位问题。

  - type: textarea
    id: description
    attributes:
      label: Bug 描述
      description: 请清晰、简洁地描述 Bug
      placeholder: 例如：点击工具栏按钮后没有反应...
    validations:
      required: true

  - type: textarea
    id: reproduction
    attributes:
      label: 复现步骤
      description: 如何复现这个 Bug
      placeholder: |
        1. 打开设置...
        2. 点击...
        3. 观察到错误...
    validations:
      required: true

  - type: input
    id: environment
    attributes:
      label: 环境信息
      description: IntelliJ IDEA 版本和插件版本
      placeholder: 例如：IDEA 2024.2.1, CCBar 1.0.0
    validations:
      required: true

  - type: textarea
    id: expected
    attributes:
      label: 期望行为
      description: 你期望发生什么？
    validations:
      required: true

  - type: textarea
    id: actual
    attributes:
      label: 实际行为
      description: 实际发生了什么？
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: 错误日志或截图
      description: 如有错误日志、截图或录屏，请附在这里
