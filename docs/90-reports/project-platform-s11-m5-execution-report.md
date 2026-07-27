# PROJECT-PLATFORM-S11-M5 Execution Report

## Scope
PROJECT-PLATFORM-S11-M5-T01 到 PROJECT-PLATFORM-S11-M5-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M5-T01 | non-core | integration | not-required | not-required | No | M1-M4 48 项、V101、报告和边界逐项反查 |
| PROJECT-PLATFORM-S11-M5-T02 | core-user | e2e-real-isolated | real | isolated | No | owner 配置角色、subject、动作、限定与 data scope |
| PROJECT-PLATFORM-S11-M5-T03 | core-user | e2e-real-isolated | real | isolated | No | member/guest capability、安全解释和无权态 |
| PROJECT-PLATFORM-S11-M5-T04 | core-user | e2e-real-isolated | real | isolated | No | 空间/事项角色与服务端收权投影 |
| PROJECT-PLATFORM-S11-M5-T05 | core-user | e2e-real-isolated | real | isolated | No | owner/admin 治理预览且 enterprise 无内容旁路 |
| PROJECT-PLATFORM-S11-M5-T06 | core-user | e2e-real-isolated | real | isolated | No | 长名称、键盘、焦点、1366/820 与无障碍 |
| PROJECT-PLATFORM-S11-M5-T07 | core-user | e2e-real-isolated | real | isolated | No | 对象/字段/关系与 data-scope 正向真实流 |
| PROJECT-PLATFORM-S11-M5-T08 | core-user | e2e-real-isolated | real | isolated | No | 六身份和自定义策略负向最小披露 |
| PROJECT-PLATFORM-S11-M5-T09 | core-user | e2e-real-isolated | real | isolated | No | 重校准、离线输入保留、到期/收权合同 |
| PROJECT-PLATFORM-S11-M5-T10 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16、V001-V101 与完整工程门禁 |
| PROJECT-PLATFORM-S11-M5-T11 | non-core | integration | not-required | not-required | No | 架构、Program、模块/对象/事件与 S12 准入 |
| PROJECT-PLATFORM-S11-M5-T12 | non-core | integration | not-required | not-required | No | S11 Go、Stage none、60 Task/五报告一致 |

## Completed Items
- 逐项反查 M1-M4 的 48 个 Task、四份报告、snapshot v5、V101、统一 decision 与治理边界，无未关闭阻断。
- 空间配置草稿新增权限策略编辑器，呈现空间/事项角色、allow/deny、subject、动作、字段/节点/关系限定与六类 data scope；保存保持 schema v5，不建立 live policy 双写。
- 修复关系/节点旧编辑器把 v5 草稿降级为 v4/v3 的缺陷；所有编辑器只提升最低 schema，不覆盖未来能力。
- WorkItem 详情新增服务端 capability、安全 explanation 与字段访问投影面板；成员 UI 不从角色、按钮或策略正文补算权限。
- 运行 snapshot 明确移除 `permissionModel`，普通成员不能从详情响应读取角色继承、selector、deny 或 legacy 映射正文。
- 用户 explanation 只在对象已可见后返回最小安全来源；治理 API 继续要求 owner/admin 空间 manager，enterprise admin 不能借企业身份取得私有内容。
- 最终 full gate 发现关系投影误用 relation-definition hash 作为端点配置绑定；现已投影端点自身 config hash、字段值和创建者，并把 relation key 与完整 data-scope context 交给统一 decision。
- 最终 full gate 发现并发 reparent 的命令回执 FK 锁与 relation `FOR UPDATE` 锁升级可能死锁；withdraw/restore 改为先锁 relation 再开始回执，真实 PostgreSQL 层级/关系集成测试 7/7 PASS。
- 独立浏览器 runner 创建随机 PostgreSQL 数据库，从空库执行 V001-V101，启动独立后端/前端并动态创建六身份夹具。
- 关键视口覆盖 1440/1366/820、长名称、选择器键盘交互和页面横向溢出；真实流验证 capability、safe source 与 hidden 零泄露。
- S11 五个 Milestone、60 个 Task 完成，Go；只开放 S12 准入，不提前实现个人工作台、保存视图、自动化或跨空间同步。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M5-T01 | 48 项可追溯且无阻断 | M1-M4 reports、V101、architecture 27.4-27.7 | planning/document audit | 不需要 | Done |
| PROJECT-PLATFORM-S11-M5-T02 | 权限定义进入唯一草稿且即时诊断 | `ProjectWorkItemPermissionPolicyEditor` | lint/build + definition tests | real isolated owner 配置页 | Done |
| PROJECT-PLATFORM-S11-M5-T03 | capability/explanation 只来自服务端 | `WorkItemPermissionsPanel` + explanation endpoint | decision/governance tests | real isolated member/guest | Done |
| PROJECT-PLATFORM-S11-M5-T04 | 角色/收权投影稳定 | schema v5 roles + participant/member UI | service integration | real isolated owner/admin/member/guest | Done |
| PROJECT-PLATFORM-S11-M5-T05 | 治理交集不旁路内容 | manager-gated governance API | governance negative tests | real isolated：admin preview 200；enterprise 403/404 | Done |
| PROJECT-PLATFORM-S11-M5-T06 | 响应式、键盘和长名称可用 | responsive CSS/semantic controls | lint/build | real isolated：1440/1366/820 无横向溢出 | Done |
| PROJECT-PLATFORM-S11-M5-T07 | 正向动作与细粒度投影一致 | unified decision + endpoint-bound relation projection | PostgreSQL hierarchy/relation 7/7 + service regression | real isolated：member action/field explanation | Done |
| PROJECT-PLATFORM-S11-M5-T08 | 六身份最小披露 | permission model removed from runtime | six-identity integration | real isolated：outsider/enterprise 404 且无标题 | Done |
| PROJECT-PLATFORM-S11-M5-T09 | 重校准、并发和输入保留明确 | explanation query key + relation-first receipt lock order | hierarchy concurrent reparent one-winner | real isolated：refresh/offline contract | Done |
| PROJECT-PLATFORM-S11-M5-T10 | full gate 与真实 DB 通过 | V001-V101 + isolated runner | route-final + system evidence | real isolated PostgreSQL/browser | Done |
| PROJECT-PLATFORM-S11-M5-T11 | 文档只声明已实现事实 | Program rev30 + architecture 27.8 | planning/architecture gate | 不需要 | Done |
| PROJECT-PLATFORM-S11-M5-T12 | S11 Go 且 Stage none | roadmap/report/Program consistency | route-final finish | route-final PASS | Done |

## Code Changes
- `web/src/modules/projectSpaces/components/ProjectWorkItemPermissionPolicyEditor.tsx`
- `web/src/modules/projectSpaces/components/WorkItemPermissionsPanel.tsx`
- `web/e2e/project-platform-s11-m5-route-final.spec.ts`
- `server/src/main/java/com/colla/platform/modules/project/{api,application}/**`
- `tools/workbench/src/{browser/smoke.ts,commands/browser.ts}`
- `docs/{00-product,01-architecture,02-roadmap,05-runbooks,90-reports}/**`

## Validation
- Backend tests: permission decision/governance targeted tests 8/8 PASS；WorkItem PostgreSQL integration 与 final full suite。
- Frontend build: `pnpm lint` PASS；`pnpm build` PASS，3318 modules transformed。
- Local quality gate: `.local-reports/quality-gate-20260727T065254.md` 已通过全部可执行门禁并发现/修正逐 Task 的 real-evidence 表达；最终 `work:finish --validation-profile route-final` 重验，未伪造中间 checkpoint。
- Browser smoke: `pnpm smoke:s11-m5-isolated` 使用随机数据库、V001-V101、独立服务和动态六身份，独立预检 1/1 PASS，最终 finish 复用同一隔离 runner，不使用 route mock。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- S11 完成路线由独立 archive-only 工作循环归档；随后把 S12 激活为唯一当前 Stage。
- S12 聚合、搜索、收藏和动态必须继续复用 S11 data scope、hidden 零披露和统一 decision，不能用全局查询旁路权限。
