# S10 WorkItem 关系迁移与恢复 Runbook

## 适用范围

本手册只用于把已由 S07 显式映射到同一 Project Space 的 legacy `issue_relations` 承接为规范 WorkItem relation。它不用于跨空间同步，不把 message、knowledge 或其他平台对象伪造成 WorkItem。

## 权限与前置条件

- 仅 Project Space owner/admin 可操作；enterprise-admin 若不是空间成员不会获得内容访问。
- 先确认 S07 `project_legacy_work_item_maps` 为 active，目标 relation definition 已发布，且 source/target type matrix 允许对应端点。
- 每次操作使用新的 caller request id、明确原因、最新 batch expected version。执行确认必须精确为 `MIGRATE_RELATIONS`，回退确认为 `ROLLBACK_RELATIONS`。

## 标准流程

1. 调用 `POST /api/project-spaces/{spaceId}/relation-migrations:plan`，先使用 `dryRun=true`。保存返回的 manifest hash、分类计数和失败/保留单元。
2. 非 WorkItem、已删除、跨空间和未解析目标必须保持 `preserved`；不得通过改表把它们改成 canonical。
3. 使用新的 request id 创建 `dryRun=false` plan，核对 manifest hash 未漂移。
4. 对 batch 调用 `:execute`。失败后先处理映射/定义问题，再以响应中的最新 version 调用 `:resume`；不要重复 plan 规避失败。
5. 完成后调用 `:verify`。只有 `verified` 且 failure count 为 0 才可声明承接完成。
6. 如需恢复，先停止关系编辑，使用最新 expected version、原因和 `ROLLBACK_RELATIONS` 调用 `:rollback`。任何已被后续改变的边都会失败关闭，必须人工复核，禁止直接删表。

## 校准与故障处理

- 409/version conflict：刷新 batch 和单元，保留当前原因/选择，再由操作者确认是否续跑。
- 422/定义或类型矩阵失败：修复并发布配置后 resume；历史 batch manifest 不变。
- timeout/offline：先 GET batch 校准。确定 unit 仍为 planned/failed 后再 resume；稳定 relation command request id 会精确重放。
- verify failure：以安全 failure unit id 清单定位，不从数据库错误正文复制 WorkItem 标题或隐藏内容。
- rollback conflict：说明 canonical relation 在迁移后发生变化；停止自动恢复，按关系历史和审计人工决策。

## 禁止项

- 禁止直接写 `project_work_item_relations`、migration unit/status 或 hierarchy path。
- 禁止从 `work_item_relation.changed` payload 拼接关系正文或计数。
- 禁止把 migration batch 当第二套关系事实；最终权威始终是 canonical relation edge/history。
