---
title: Colla Platform 使用说明入口
status: active
last_code_check: 2026-08-01
code_commit: 0822e945ddc0
---

# Colla Platform 使用说明

本目录存放面向 Colla Platform 使用者和本地试用者的操作说明。

它与 `docs/` 的职责不同：

- `instruction/` 解释功能是什么、怎样配置、怎样使用，以及本地试用时需要启动哪些服务。
- `docs/` 保存产品范围、架构、路线图、AI 工作循环、工程规范、运行手册和历史报告，是 AI 工作台的知识基线。

将使用说明单独放在这里，可以避免用户手册和 AI 工作台生成、维护的工程文档混在一起。

## 文档目录

| 目录 | 内容 | 入口 |
| --- | --- | --- |
| `project_space/` | 从零认识、配置和使用项目空间 | [项目空间使用手册](./project_space/README.md) |
| `local_development/` | 本地启动、服务边界和试用说明 | [本地运行与服务说明](./local_development/README.md) |

## 阅读建议

- 第一次使用项目空间：从“项目空间使用手册”开始，按章节顺序阅读。
- 需要在本机启动项目或判断某个服务是否必需：进入“本地运行与服务说明”。
- 需要部署、故障恢复、质量门禁或架构细节：再查阅 `docs/` 中相应的工程文档。
