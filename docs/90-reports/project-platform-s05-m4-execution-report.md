# PROJECT-PLATFORM-S05-M4 Execution Report

## Scope
PROJECT-PLATFORM-S05-M4-T01 至 PROJECT-PLATFORM-S05-M4-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M4-T01 | core-system | system-real-isolated | not-required | isolated | No | 真实 PostgreSQL 一次读取组合字段、双布局与一致访问投影 |
| PROJECT-PLATFORM-S05-M4-T02 | core-user | e2e-real-isolated | real | isolated | No | owner 在真实浏览器切换布局与 synthetic 角色上下文 |
| PROJECT-PLATFORM-S05-M4-T03 | core-user | e2e-real-isolated | real | isolated | No | member/guest 进入无保存动作的当前身份只读样本 |
| PROJECT-PLATFORM-S05-M4-T04 | core-user | e2e-real-isolated | real | isolated | No | 共享渲染器按 S04 类型目录呈现编辑、只读或安全不支持状态 |
| PROJECT-PLATFORM-S05-M4-T05 | core-user | e2e-real-isolated | real | isolated | No | 条件重算不持久化，隐藏值保留风险显式提示 |
| PROJECT-PLATFORM-S05-M4-T06 | core-user | e2e-real-isolated | real | isolated | No | 管理诊断可操作，用户响应不返回管理诊断或隐藏身份 |
| PROJECT-PLATFORM-S05-M4-T07 | core-user | e2e-real-isolated | real | isolated | No | 标签、键盘焦点、状态 live region 和无焦点陷阱 |
| PROJECT-PLATFORM-S05-M4-T08 | core-user | e2e-real-isolated | real | isolated | No | 1440/1366/820 视口无文档级横向溢出，编辑区内部重排 |
| PROJECT-PLATFORM-S05-M4-T09 | core-user | e2e-real-isolated | real | isolated | No | 网络失败、重试、脏草稿刷新确认和并发重新应用 |
| PROJECT-PLATFORM-S05-M4-T10 | core-system | system-real-isolated | not-required | isolated | No | 120 字段、2400 选项集合读取和最大合法布局预算 |
| PROJECT-PLATFORM-S05-M4-T11 | core-user | e2e-real-isolated | real | isolated | No | 六身份 API/浏览器矩阵与动态夹具回收 |
| PROJECT-PLATFORM-S05-M4-T12 | core-user | e2e-real-isolated | real | isolated | No | 目标后端、前端、架构、安全和真实浏览器门禁 |

## Completed Items
- 建立 repeatable-read 配置集合读模型，批量装载选项并校验配置与投影 version/hash 一致性。
- 建立用户侧当前身份 synthetic 样本入口及 S07 可复用的共享渲染器输入边界。
- 完成 S04 注册字段类型的编辑/只读映射、最小披露诊断、条件清值提示和冲突恢复。
- 完成键盘、标签、状态朗读、1440/1366/820 响应式和局部滚动收口。
- 完成 120 字段、2400 选项配置预算与六身份真实隔离浏览器闭环。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M4-T01 | 单次一致读取，无字段选项 N+1 | `WorkItemLayoutWorkbenchController`、bulk `listByType`、repeatable-read hash guard | 集成测试 8/8 PASS，120 字段目录一次装载 | Not required：真实 API/数据库系统证据覆盖 | Done |
| PROJECT-PLATFORM-S05-M4-T02 | create/detail 与角色/状态切换复用正式投影 | `ProjectWorkItemLayoutsPanel` + `WorkItemLayoutRenderer` | frontend lint/build PASS | Real isolated：owner workbench、guest synthetic preview PASS | Done |
| PROJECT-PLATFORM-S05-M4-T03 | 用户只见当前身份只读样本，无伪保存 | `ProjectWorkItemLayoutSample`、`POST .../sample` | API 断言 member/guest 200，配置读 403 | Real isolated：member/guest 页面无保存或创建动作 | Done |
| PROJECT-PLATFORM-S05-M4-T04 | 每个 S04 已注册类型有明确行为，未知类型失败关闭 | renderer 11 类映射、rich-text presentation 多行控件、unsupported fallback | TypeScript build PASS | Real isolated：text/select 编辑与只读呈现；附件/引用不产生事实 | Done |
| PROJECT-PLATFORM-S05-M4-T05 | 条件变化不写事实，隐藏值风险显式 | projection-only preview、hidden value warning | preview 零持久化合同沿用 M3；M4 API 响应断言 PASS | Real isolated：角色切换后合成结果和字段状态更新 | Done |
| PROJECT-PLATFORM-S05-M4-T06 | 管理诊断可修复，用户最小提示 | diagnostics panel、user diagnostics suppression、safe option DTO | 用户响应断言无 workspaceId/createdBy/管理诊断 | Real isolated：用户样本只见安全投影 | Done |
| PROJECT-PLATFORM-S05-M4-T07 | 核心路径键盘可达且标签稳定 | field label/id、aria-live、editable-target shortcut guard | lint/build PASS | Real isolated：textarea Delete 不触发删除；12 步 Tab 覆盖多控件且仅在序列结束经过一次 body | Done |
| PROJECT-PLATFORM-S05-M4-T08 | 桌面与窄屏不发生文档级横向崩坏 | responsive grid、layout editor local overflow | CSS/build PASS | Real isolated：1440/1366/820 三视口 scrollWidth 断言 PASS | Done |
| PROJECT-PLATFORM-S05-M4-T09 | 错误可恢复，冲突重放不丢草稿 | query retry、offline command guard、conflict confirmation、dirty refresh confirmation | mutation/hash/version 合同测试 PASS | Real isolated：离线命令明确拒绝且不排队；恢复联网后主动命令和刷新 PASS | Done |
| PROJECT-PLATFORM-S05-M4-T10 | 120 字段/2400 选项在 3 秒预算内 | bulk option query、配置集合 DTO、120 节点冻结限制 | 真实 PostgreSQL 测试 8/8 PASS；120/2400 请求 <3s | Not required：系统证据直接计时和断言完整目录 | Done |
| PROJECT-PLATFORM-S05-M4-T11 | 六身份权限与最小披露闭环 | API matrix + dynamic identity fixture cleanup | owner/admin 200，member/guest 配置 403，outside/enterprise sample 404 | Real isolated：单 spec 覆盖 owner、admin、member、guest、non-member、enterprise admin | Done |
| PROJECT-PLATFORM-S05-M4-T12 | fresh 门禁和证据可追溯 | workbench checkpoint/finish、执行报告 | 目标后端、lint/build、diff/architecture/security gates PASS | Real isolated：`project-platform-s05-m4-layout-samples.spec.ts` 1/1 PASS（13.8s） | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemLayoutWorkbenchController.java`
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemLayoutAccessController.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayoutAccessProjectionService.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemFieldConfigurationService.java`
- `server/src/main/java/com/colla/platform/modules/project/infrastructure/{WorkItemFieldOptionRepository,JdbcWorkItemFieldOptionRepository}.java`
- `server/src/test/java/com/colla/platform/modules/project/api/WorkItemLayoutConfigurationControllerIntegrationTests.java`
- `web/src/modules/projectSpaces/components/{ProjectWorkItemLayoutsPanel,ProjectWorkItemLayoutSample,WorkItemLayoutRenderer}.tsx`
- `web/src/modules/projectSpaces/api/{workItemLayoutsApi,workItemTypesApi}.ts`
- `web/src/{app/router.tsx,index.css}`
- `web/e2e/project-platform-s05-m4-layout-samples.spec.ts`
- `docs/01-architecture/{current-architecture,project-platform-target-architecture}.md`

## Validation
- Backend tests: `WorkItemLayoutConfigurationControllerIntegrationTests` 8/8 and `WorkItemFieldAccessPolicyEvaluatorTests` 4/4 passed against real PostgreSQL/Testcontainers.
- Frontend build: `pnpm web:lint` and `pnpm web:build` passed; 3299 modules transformed and production chunks emitted successfully.
- Local quality gate: checkpoint `quality-gate-20260725T193557.md` passed; final finish report is recorded under `.local-reports`.
- Browser smoke: corrected real-isolated Playwright passed 1/1 in 17.2s; evidence covers six identities, keyboard traversal, contrast, offline rejection/recovery and responsive viewports.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Scope Clarifications
- M5 审计确认原路线 T04 误把目标架构中的 interval/computed 写入 S04 已注册类型清单。S04 实际注册 11 类字段，rich-text 是 text 的 presentation；路线已纠正为覆盖全部 S04 注册类型并对未知类型失败关闭。interval/computed 必须在后续 Stage 先增加字段 schema、存储和服务端校验合同，不能由 S05 单独伪造控件。
- The 120-node graph invariant includes containers. A full 120-field catalog is returned, while the largest legal rendered graph is one root section plus 119 field nodes; no graph limit was weakened.

## Next Steps
- 执行 PROJECT-PLATFORM-S05-M5 route-final：全量迁移、后端/前端/协作/工作台/安全门禁、Stage 文档同步和 S06 准入决策。
