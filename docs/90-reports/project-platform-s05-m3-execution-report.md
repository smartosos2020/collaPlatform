# PROJECT-PLATFORM-S05-M3 Execution Report

## Scope
PROJECT-PLATFORM-S05-M3-T01 至 PROJECT-PLATFORM-S05-M3-T11

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M3-T01 | non-core | unit | not-required | not-required | No | 策略模式、优先级、默认值和 required/write 关系单元验证 |
| PROJECT-PLATFORM-S05-M3-T02 | core-system | system-real-isolated | not-required | isolated | No | 真实 PostgreSQL 保存 schema v1 策略并拒绝非法版本、角色和规则 |
| PROJECT-PLATFORM-S05-M3-T03 | core-system | system-real-isolated | not-required | isolated | No | 服务端确定性求值、解释链和 hidden 最小披露 |
| PROJECT-PLATFORM-S05-M3-T04 | core-system | system-real-isolated | not-required | isolated | No | 六身份、停用成员及空间/类型/字段状态矩阵 |
| PROJECT-PLATFORM-S05-M3-T05 | core-system | system-real-isolated | not-required | isolated | No | 真实布局过滤、空容器裁剪和诊断脱敏 |
| PROJECT-PLATFORM-S05-M3-T06 | core-system | system-real-isolated | not-required | isolated | No | owner 策略写入、member 伪造拒绝、幂等/并发和元数据审计 |
| PROJECT-PLATFORM-S05-M3-T07 | core-system | system-real-isolated | not-required | isolated | No | synthetic preview 请求及业务表、审计、outbox、command receipt 零写入 |
| PROJECT-PLATFORM-S05-M3-T08 | core-user | e2e-real-isolated | real | isolated | No | owner 在真实浏览器编辑字段策略、确认危险收窄并保存 |
| PROJECT-PLATFORM-S05-M3-T09 | core-user | e2e-real-isolated | real | isolated | No | 共享渲染器消费服务端投影并按 hidden/read/write/required 呈现 |
| PROJECT-PLATFORM-S05-M3-T10 | core-system | system-real-isolated | not-required | isolated | No | 六身份、跨边界、停用成员和企业管理员直接 API 负向矩阵 |
| PROJECT-PLATFORM-S05-M3-T11 | core-user | e2e-real-isolated | real | isolated | No | 120 次并发求值预算、规范 hash、幂等、安全门禁和真实权限浏览器闭环 |

## Completed Items
- 冻结 schema v1 字段访问策略，明确 hidden/read/write、required 从属于 write、角色上限和资源状态收窄规则。
- 实现服务端规范化、静态校验、确定性求值器、解释链、布局过滤、空容器裁剪和最小披露响应。
- 实现独立策略写 API、聚合版本/hash/幂等并发合同、拒绝审计和严格非持久化 synthetic preview。
- 通过公开 `AuthenticationQuery` 校验活跃成员，未新增 project 对 identity owner 表的直接读取。
- 实现管理端策略编辑、危险收窄确认、六身份/状态/样本预览，以及只消费服务端投影的失败关闭渲染。
- 完成真实 PostgreSQL 目标测试、前端 lint/build、架构门禁及真实隔离 Playwright 权限闭环。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M3-T01 | hidden 优先、required 仅属于 write，默认与角色上限无冲突 | `WorkItemFieldAccessPolicySchema`、`WorkItemFieldAccessPolicyEvaluator` | schema/evaluator 单元测试 7/7 PASS | Not required：纯策略语义由确定性单元测试闭包 | Done |
| PROJECT-PLATFORM-S05-M3-T02 | 只接受 schema v1、允许角色/上下文/同类型字段和无冲突规则 | schema canonicalize + `WorkItemLayoutFieldReferenceValidator` | canonicalizer 3/3 与真实库 API 6/6 PASS | Not required：真实 API/数据库系统证据覆盖 | Done |
| PROJECT-PLATFORM-S05-M3-T03 | 相同输入结果与解释链稳定，hidden 不返回字段元数据 | evaluator + `WorkItemLayoutAccessProjectionService` projected DTO | 120 次并发确定性测试及 API 最小披露断言 PASS | Not required：系统证据直接断言响应体 | Done |
| PROJECT-PLATFORM-S05-M3-T04 | 六身份与 active/disabled/archived/retired 状态按上限收窄 | evaluator role ceilings + `AuthenticationQuery.findActiveMember` | 集成测试覆盖 owner/admin/member/guest/non-member/enterprise admin 和 disabled user | Not required：真实身份矩阵由系统证据闭包 | Done |
| PROJECT-PLATFORM-S05-M3-T05 | hidden 节点和字段移除，结构合法且诊断不泄露身份 | `filterNodes`、projected fields/decisions/diagnostics | 集成测试断言隐藏 key、标题、规则均不出现在响应体 | Not required：真实投影响应由系统证据闭包 | Done |
| PROJECT-PLATFORM-S05-M3-T06 | manager 可写，伪造写入一致拒绝并仅记录安全元数据 | policy PUT、`savePolicies`、`WorkItemLayoutSecurityAuditService` | 真实库覆盖 owner 200、member 403、stale 409、幂等重放和审计无敏感正文 | Not required：系统证据直接调用授权 API | Done |
| PROJECT-PLATFORM-S05-M3-T07 | 合法合成上下文可预览且不产生任何正式副作用 | preview POST + `SyntheticContext` 验证 | 集成测试比较 preview 前后 policy/audit/outbox/command 表计数不变 | Not required：真实数据库零写入断言 | Done |
| PROJECT-PLATFORM-S05-M3-T08 | 权限启用、关系清楚、危险收窄确认且保留条件规则 | `ProjectWorkItemLayoutsPanel` policy editor | frontend lint/build PASS | Real isolated：owner 选择 member hidden、确认并保存成功 | Done |
| PROJECT-PLATFORM-S05-M3-T09 | UI 不自行扩权，缺失投影失败关闭，状态完全来自服务端 | `WorkItemLayoutRenderer` + projection API client | TypeScript build PASS；缺失 projection 映射 hidden | Real isolated：预览显示 title 且不显示 security_note/secret | Done |
| PROJECT-PLATFORM-S05-M3-T10 | 越权、停用和企业管理员请求均零泄露 | projection 404 boundary + policy 403 boundary | 真实库六身份矩阵与 OpenAPI 路径断言 6/6 PASS | Not required：浏览器 spec 另补 enterprise admin 404 和 member forged PUT 403 | Done |
| PROJECT-PLATFORM-S05-M3-T11 | 求值在预算内且 hash、并发、幂等、安全与 checkpoint 全通过 | evaluator 并发预算、canonical policy、aggregate command contract | 后端 16/16、checkpoint `quality-gate-20260725T172741.md` PASS | Real isolated：`project-platform-s05-m3-field-access.spec.ts` 1/1 PASS，1440px 无横向溢出 | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemFieldAccessPolicySchema.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemFieldAccessPolicyEvaluator.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayoutAccessProjectionService.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayoutSecurityAuditService.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayout{Canonicalizer,ConfigurationService,FieldReferenceValidator}.java`
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemLayout{Access,Configuration}Controller.java`
- `server/src/test/java/com/colla/platform/modules/project/{application,api}/WorkItem*Tests.java`
- `web/src/modules/projectSpaces/api/workItemLayoutsApi.ts`
- `web/src/modules/projectSpaces/components/{ProjectWorkItemLayoutsPanel,WorkItemLayoutRenderer}.tsx`
- `web/src/index.css`
- `web/e2e/project-platform-s05-m3-field-access.spec.ts`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture}.md`
- `tools/workbench/src/security/platformGuard.ts` and `tools/workbench/test/platformGuard.test.ts`（修复收口门禁对规范 prose 的自误报）

## Validation
- Backend tests: targeted Maven tests passed 16/16 against real PostgreSQL/Testcontainers.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed; chunk budget and route lazy-loading passed.
- Local quality gate: checkpoint PASS in `.local-reports/quality-gate-20260725T172741.md`; architecture boundaries remain at 93 approved reads with no new exception.
- Browser smoke: real isolated Playwright `e2e/project-platform-s05-m3-field-access.spec.ts` passed 1/1 with dynamic fixture cleanup and a 1440px screenshot.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S05-M4-T01 开始组合配置集合读模型、完整控件预览、可访问性、响应式和规模验证。
