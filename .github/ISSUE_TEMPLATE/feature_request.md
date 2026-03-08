---
name: Feature Request
description: 提出新功能建议
title: "[Feature] 请在此填写简短描述]"
labels: ["enhancement"]
assignees: []
body:
  - type: markdown
    attributes:
      value: |
        感谢提出建议！请填写以下信息帮助我们了解你的需求。

  - type: textarea
    id: problem
    attributes:
      label: 相关痛点
      description: 你的功能请求是否与某个问题相关？请描述
      placeholder: 例如：我在使用...时遇到了困难，希望能够...

  - type: textarea
    id: proposal
    attributes:
      label: 功能建议
      description: 请清晰、简洁地描述你希望实现的功能
    validations:
      required: true

  - type: textarea
    id: usecase
    attributes:
      label: 使用场景
      description: 描述这个功能的使用场景和目标用户
    validations:
      required: true

  - type: textarea
    id: alternatives
    attributes:
      label: 其他替代方案
      description: 你是否考虑过其他替代方案？

  - type: textarea
    id: additional
    attributes:
      label: 补充信息
      description: 任何其他背景信息、截图或mockup可以在这里提供
