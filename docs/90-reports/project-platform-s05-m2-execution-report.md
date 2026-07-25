# PROJECT-PLATFORM-S05-M2 Execution Report

## Scope
PROJECT-PLATFORM-S05-M2-T01 至 PROJECT-PLATFORM-S05-M2-T12

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M2-T01 | system-real-isolated | not-required | isolated | No | 真实 PostgreSQL create/detail 独立保存读取 |
| PROJECT-PLATFORM-S05-M2-T02 | unit | not-required | not-required | No | 五类节点图命令与 schema 约束 |
| PROJECT-PLATFORM-S05-M2-T03 | system-real-isolated | not-required | isolated | No | 原子命令、幂等重放、旧版本冲突与确认删除 |
| PROJECT-PLATFORM-S05-M2-T04 | unit | not-required | not-required | No | DSL 规范化、组合、安全拒绝与无副作用求值 |
| PROJECT-PLATFORM-S05-M2-T05 | system-real-isolated | not-required | isolated | No | 隐藏依赖、循环、失效引用、类型和值拒绝 |
| PROJECT-PLATFORM-S05-M2-T06 | static | not-required | not-required | No | API DTO、query key 和错误映射审计 |
| PROJECT-PLATFORM-S05-M2-T07 | e2e-real-isolated | real | isolated | No | owner 配置入口与 member 隐藏入口 |
| PROJECT-PLATFORM-S05-M2-T08 | e2e-real-isolated | real | isolated | No | 1366px 三面板编辑和无页面横向溢出 |
| PROJECT-PLATFORM-S05-M2-T09 | e2e-real-isolated | real | isolated | No | UI 节点命令、条件更新与冲突意图合同 |
| PROJECT-PLATFORM-S05-M2-T10 | e2e-real-isolated | real | isolated | No | 字段条件、类型操作符和错误分支 |
| PROJECT-PLATFORM-S05-M2-T11 | e2e-real-isolated | real | isolated | No | 字段目录与访问投影驱动的共享渲染预览 |
| PROJECT-PLATFORM-S05-M2-T12 | e2e-real-isolated | real | isolated | No | 动态 fixture 的完整管理员配置与 member 拒绝流程 |

## Completed Items
- 完成 create/detail 独立布局聚合和原子节点命令 API，保留永久节点身份、幂等回放与乐观版本冲突。
- 完成 schema v1 条件 DSL、`all/any/not`、安全求值、类型化操作符、引用闭包、隐藏依赖和循环检测。
- 完成布局 API client、隔离 query keys、管理员路由、三面板编辑器、拖放/键盘命令和冲突意图保留。
- 完成组合条件属性编辑器和即时分支诊断，前端只展示字段类型目录允许的操作符。
- 完成共享渲染器及控件注册边界，显式消费布局、字段目录和访问投影，未知字段/控件安全降级。
- 完成真实 PostgreSQL 单元/集成回归及动态隔离 Playwright 管理员/member 闭环。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M2-T01 | create/detail 独立且不互相覆盖 | configuration service/repository；kind 复合唯一键 | API integration 4/4 中 create/detail 隔离 | E2E 分别初始化并比较 id/hash | Done |
| PROJECT-PLATFORM-S05-M2-T02 | 五类节点受 schema 保护且身份稳定 | `WorkItemLayoutGraphCommandHandler` + canonicalizer | graph/canonicalizer unit 5/5 | 编辑器呈现五类控件 | Done |
| PROJECT-PLATFORM-S05-M2-T03 | 局部命令原子、确认删除、并发不覆盖 | `nodes:command`、command receipt、aggregate version | graph unit 2/2；API 重放/409 | 管理员执行真实节点 add/update | Done |
| PROJECT-PLATFORM-S05-M2-T04 | 版本化组合 DSL 无外部副作用 | `WorkItemLayoutConditionDsl` | DSL unit 2/2 | 组合条件 UI 生成 schema v1 | Done |
| PROJECT-PLATFORM-S05-M2-T05 | 引用闭包、循环与失效引用可拒绝 | field reference validator | API 真实库覆盖 hidden/cycle/operator | 错误映射保留到属性面板 | Done |
| PROJECT-PLATFORM-S05-M2-T06 | 客户端类型和缓存隔离 | `workItemLayoutsApi.ts` | TypeScript build PASS | create/detail 切换读取独立缓存 | Done |
| PROJECT-PLATFORM-S05-M2-T07 | 管理入口真实授权 | route + `ProjectSpacesPage` canManage | API owner 200/member 403 | real：owner 可见；member 无按钮/面板 | Done |
| PROJECT-PLATFORM-S05-M2-T08 | 紧凑编辑器在 1366px 可用 | panel + responsive CSS | lint/build PASS | real：1366px page overflow <= 1px | Done |
| PROJECT-PLATFORM-S05-M2-T09 | UI 命令与后端原子命令一致 | runCommand/pendingCommand/retry | API command 版本与幂等通过 | real：add/update 请求通过 | Done |
| PROJECT-PLATFORM-S05-M2-T10 | 只提供合法字段/操作符并即时定位错误 | `NodeProperties` condition drafts | lint/build + API invalid value/operator | real：字段条件保存通过 | Done |
| PROJECT-PLATFORM-S05-M2-T11 | 渲染器消费三类输入并安全降级 | `WorkItemLayoutRenderer` + control registry | TypeScript build PASS | real：预览显示字段目录控件 | Done |
| PROJECT-PLATFORM-S05-M2-T12 | 专项和真实隔离闭环无阻断 Gap | 本报告与路线图 | backend 11/11；lint/build/diff PASS | real：Playwright 1/1 PASS | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayoutConditionDsl.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayoutGraphCommandHandler.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemLayout{Canonicalizer,ConfigurationService,FieldReferenceValidator}.java`
- `server/src/main/java/com/colla/platform/modules/project/api/WorkItemLayoutConfigurationController.java`
- `server/src/test/java/com/colla/platform/modules/project/{application,api}/WorkItemLayout*Tests.java`
- `web/src/modules/projectSpaces/api/workItemLayoutsApi.ts`
- `web/src/modules/projectSpaces/components/{ProjectWorkItemLayoutsPanel,WorkItemLayoutRenderer}.tsx`
- `web/src/modules/projectSpaces/pages/ProjectSpacesPage.tsx`
- `web/src/app/router.tsx`
- `web/src/index.css`
- `web/e2e/project-platform-s05-m2-layout-editor.spec.ts`

## Validation
- Backend tests: `mvn -Dtest=WorkItemLayoutCanonicalizerTests,WorkItemLayoutConditionDslTests,WorkItemLayoutGraphCommandHandlerTests,WorkItemLayoutConfigurationControllerIntegrationTests test` passed 11/11.
- Frontend build: `pnpm web:build` passed; `pnpm web:lint` passed and the project-space route remains lazy-loaded.
- Local quality gate: checkpoint PASS recorded in `quality-gate-20260725T151912.md`; final finish reruns the same targeted gate set.
- Browser smoke: real isolated Playwright `e2e/project-platform-s05-m2-layout-editor.spec.ts` passed 1/1 with dynamic cleanup.

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S05-M3 | 正式服务端字段访问求值和脱敏投影尚未实现 | 不影响 M2 配置图与渲染边界；M2 不从客户端策略推导授权 | Current roadmap M3 |
| N/A | No blocking gap | non-blocking | Closed |

## Next Steps
- 等待并行工作台重构合并验证后，从新的 v3 work context 启动 `PROJECT-PLATFORM-S05-M3-T01`。
