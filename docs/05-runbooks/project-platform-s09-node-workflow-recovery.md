---
title: S09 复杂节点流恢复、升级与回填 Runbook
status: active
last_code_check: 2026-07-27
owner: project
---

# S09 复杂节点流恢复、升级与回填 Runbook

## 1. 适用范围

本 runbook 只处理节点流的显式 return/jump/terminate/correct、声明式补偿续跑、实例 binding upgrade，以及 pre-S09 WorkItem 的显式 backfill。它不授权直接修改节点私表，不用 WorkItem archive/restore 冒充业务终止，也不包含 S10 关系引擎、S16 工作量或 S17 自动化。

所有操作必须由目标空间 active owner/admin 在既有用户 API 边界执行。企业管理员若不是空间成员仍不可枚举内容。原因正文长度为 10-500 字，只持久化 canonical hash；操作者应在外部变更单保留批准、窗口和原因原文。

## 2. 执行前检查与停止条件

1. 读取 WorkItem 当前 `typeVersionId/configHash/version` 和 node workflow `instanceId/status/aggregateVersion/active token`，记录 freshness 时间。
2. 确认绑定 snapshot hash 完整，recovery command 或目标 snapshot 已发布/被 supersede 且仍可解释；不得查询 active draft 或“最新版本”替代绑定事实。
3. upgrade 必须先执行 compatibility 分析，并准备覆盖全部且仅覆盖 active source node 的显式 map。backfill 必须冻结 1-500 个唯一 WorkItem ID、目标 type version/hash、entry node 与每项 source binding/version。
4. 确认 PostgreSQL、审计和 outbox 可写，事件 worker 没有相关 permanent dead letter；生产操作另需备份、观测和变更批准。
5. 出现以下任一情况立即停止：`blocked` finding、snapshot/hash 不一致、expected version 漂移、来源节点不匹配、映射缺失/多余/重复、目标非 executable node、跨 workspace/space、隐藏对象、未知补偿动作、数据库/事件链异常或超过批次上限。

## 3. 单实例恢复

使用 `POST /api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow/recoveries/{commandKey}`。请求必须包含 reason、绑定 snapshot 声明的精确 confirmation、expected WorkItem/instance version 和 caller-stable request ID。

- `return_to`：只回到声明目标并重新创建目标 task/token；重新应用该节点冻结的处理人、表单、交付物和时间策略。
- `jump`：只跳到声明且可执行的同 snapshot 目标；不允许跨实例、任意 node key 或隐式跳过。
- `terminate`：原子关闭开放 task/token/join，把 instance 置为 terminated；WorkItem 仍可保持 active。
- `correct`：用于 owner/admin 修复事实错误，追加 corrected history；不删除、覆盖或伪造旧 history。

命令成功后应重新读取 presentation/history/activity，确认 WorkItem 与 instance version 各前进一次、旧开放 work 已关闭、目标 task/token 唯一、receipt completed 且公共事件只有一个。相同 request ID/相同输入应精确重放；异载荷复用、stale version 或来源不匹配必须拒绝且不留下 pending receipt。

## 4. 补偿与 dead-letter 续跑

补偿定义属于绑定 snapshot，动作只允许注册表中的 `record_audit_marker` 和 `close_open_work`，按 `sortOrder` 执行。每个 run/step 有稳定 identity、状态和 attempt；未知动作不得执行任意代码。

对 pending/failed run 使用 `POST .../node-workflow/compensations/{runId}:resume`，提供 10-500 字原因和确认串 `RESUME_NODE_COMPENSATION`。已 completed step 必须跳过，未完成 step 才可重试；完成后复核 run/step、audit 和开放 work。公共事件消费者的 dead letter 使用通用事件运维 replay，不直接改 compensation、delivery 或 receipt 表。

## 5. 实例配置版本升级

使用 `POST .../work-items/{workItemId}/node-workflow:upgrade`，提供目标 type version、node map、原因、确认串 `UPGRADE_NODE_WORKFLOW_BINDING`、双 expected version 和 request ID。

map 的每项使用稳定 `fromNodeKey`、`toNodeKeys` 和 `one_to_one|split|merge`。来源集合必须等于当前 active node 集合；目标必须是同一目标 snapshot 内的 manual/automatic node。label、阶段名称、数组位置和最近 history 不能作为映射依据。

成功事务同时更新 WorkItem binding/规范字段投影、instance binding、关闭旧开放 work、创建 mapped token、追加 history/activity/audit/receipt/outbox。发布新版本不会自动升级旧实例，未升级实例继续解释旧绑定。提交后的语义回退必须发布更高版本并执行新的显式反向升级，不能降低版本号、修改旧 published snapshot 或删除历史。

## 6. pre-S09 backfill

使用空间级 `POST /api/project-spaces/{spaceId}/node-workflow-backfills`，提供同一 type 的显式 WorkItem manifest、目标 type version、entry node、原因、确认串 `INITIALIZE_EXISTING_NODE_WORKFLOWS` 和 request ID。

batch 先冻结 immutable manifest/request/reason hash；每个 unit 以独立事务校验 source binding/version，再提交 WorkItem binding/field projection、唯一 instance/entry token、history/activity/audit/receipt/outbox。单元失败会完整回滚并单独记录稳定 failure，不影响兄弟单元。

排除根因后使用 `RESUME_NODE_WORKFLOW_BACKFILL` 续跑 pending/failed unit。随后调用 verify；只有 completed 数等于 requested 数、failure 为空，且 WorkItem/instance 的目标 version/hash 和 version 水位一致时才可宣布完成。禁止隐式扫描“所有旧实例”、修改 manifest、删除失败账本或手工把 unit 标为 completed。

## 7. 恢复边界与升级后验证

- outbox、history、activity、audit、字段投影或约束失败时，单实例命令全部回滚；backfill 只回滚当前 unit。
- archive/restore 保留 node instance 和当前位置；terminate/correct/return/jump 改变业务流程。对象 restore 不自动重启 terminated instance。
- 已提交 recovery/upgrade/backfill 没有“删除新事实恢复旧历史”的自动回退。错误语义通过新的显式恢复/升级追加事实；数据损坏使用已批准的数据库恢复流程并停止业务写。
- 复核 V001-V096 migration、table owner/boundary gate、六身份、跨空间、重放、stale、缺失映射、故障回滚和失败续跑证据；隔离 rehearsal 不等于生产容量、HA 或 cutover 批准。
- M5 的 UI 只能调用这些服务端入口、保留 request ID/用户输入并在 409/422/timeout/offline 后重新读取事实，不能在浏览器补写 history 或推算恢复位置。
