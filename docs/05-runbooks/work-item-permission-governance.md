# WorkItem 权限治理与恢复 Runbook

1. 先冻结 workspace、space、published configuration version/hash、subject version 与 caller-stable request id；不得从 active draft 或私表拼策略。
2. 用户 explanation 只返回安全来源和申请入口。治理 trace 必须同时具备 governance permission 与目标内容 view decision；任一不足统一 `NOT_FOUND_OR_HIDDEN`。
3. role mutation 先验证 active subject、role key、expected aggregate version、最后 owner、到期时间和危险确认，再在同一事务写 binding/receipt/audit/outbox。
4. 临时授权最长 365 天；到期扫描只回收对应授权，不修改发布策略或其他来源角色。
5. consistency scan 将 subject unavailable、role definition missing、expired grant 分开记录。只有缓存/投影漂移可自动 rebuild；业务绑定必须人工修正。
6. legacy plan 必须锚定 manifest。任何可能扩权、`review_required` 或未知 disposition 项进入失败清单；resume 只重试失败单元，rollback 不覆盖后续人工授权。
7. 停止条件：最后 owner 缺口、跨空间、policy/hash 漂移、未知 subject/role、扩权项或审计/outbox 故障。发生任一条件时整项回滚，不继续批量。

本 runbook 不授予企业管理员私有内容访问，不允许直接更新 project/permission/identity 私表，也不构成 M5 UI 已完成。
