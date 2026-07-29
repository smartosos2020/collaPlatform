# PROJECT-PLATFORM-S21-M2 Execution Report

## Scope
PROJECT-PLATFORM-S21-M2-T01 到 PROJECT-PLATFORM-S21-M2-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M2-T01 | non-core | integration | not-required | not-required | No | 读取 M1 不可变快照、finding 与 removal decision，复核删除清单和保留边界 |
| PROJECT-PLATFORM-S21-M2-T02 | non-core | static | not-required | not-required | No | 复核 V139 顺序、兼容终止语义、恢复锚点和不可逆门禁 |
| PROJECT-PLATFORM-S21-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | 在真实隔离 PostgreSQL/Spring 上证明旧写 Controller/service/repository 不再注册 |
| PROJECT-PLATFORM-S21-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | 在真实隔离 Spring mapping 与 repository 反射中证明旧读 DTO/聚合合同退出 |
| PROJECT-PLATFORM-S21-M2-T05 | core-user | e2e-real-isolated | real | isolated | No | 真实浏览器验证 `/projects` 规范跳转与旧 issue 深链统一终止 |
| PROJECT-PLATFORM-S21-M2-T06 | core-user | e2e-real-isolated | real | isolated | No | 真实服务/浏览器创建 canonical 空间/类型并把 IM 消息转换为 WorkItem |
| PROJECT-PLATFORM-S21-M2-T07 | non-core | static | not-required | not-required | No | 编译与全仓 import 反查证明固定 legacy DTO/枚举不再被产品模块引用 |
| PROJECT-PLATFORM-S21-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | V139、owner manifest、架构 baseline 与 route/event/objectType 禁止项联合校验 |
| PROJECT-PLATFORM-S21-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | 从空库迁移到 V139 并证明源表、map、batch、audit 等恢复证据仍存在 |
| PROJECT-PLATFORM-S21-M2-T10 | core-user | e2e-real-isolated | real | isolated | No | 真实登录身份验证 canonical 列表、旧 API 404、深链、离线与响应式失败关闭 |
| PROJECT-PLATFORM-S21-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | Spring mapping、repository method、迁移注册和全仓反查共同证明产品活动残留为零 |
| PROJECT-PLATFORM-S21-M2-T12 | non-core | integration | not-required | not-required | No | 当前/目标架构、模块合同、对象模型、事件矩阵和路线同步到 V139 |

## Completed Items
- V139 只撤销旧产品注册与派生索引，不删除 legacy 源事实或不可变迁移/审计证据。
- 删除 ProjectController、ProjectService、固定 ProjectModels/UserProjectDtos、旧平台对象 resolver、旧 IM adapter 和 Web ProjectsPage/projectsApi。
- Workspace、Search、Platform Object、Notification filter、IM、Knowledge 和 Base 消费者改用 `project_space`/`work_item`。
- compatibility 缩减为受权 location-only resolver；旧 Web 深链只跳转 canonical WorkItem 或统一失败关闭。
- 架构 baseline 已 ratchet 到 canonical imports/SQL owner，V139 schema owner 范围已登记。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M2-T01 | M1 清单逐项可追溯且无未决定扩大删除 | M1 report、V138 snapshot/decision 与 M2 删除矩阵 | Legacy exit audit integration + 文档交叉检查 | not-required：治理证据复核无用户交互闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T02 | 删除顺序、终止 reason、保留与恢复点冻结 | V139 注释、LegacyIssueRoute、compatibility location 与架构章节 | migration compile + static route scan | not-required：静态删除/恢复合同 | Done |
| PROJECT-PLATFORM-S21-M2-T03 | `/issues` 不再写且无隐藏双写 | 删除 ProjectController/Service/legacy repository 写方法；跨模块改用 WorkItemCreationCommand | real isolated `LegacyIssueProductExitIntegrationTests` mapping scan | not-required：系统服务合同由真实隔离测试闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T04 | 用户读只返回 canonical WorkItem | 删除旧 DTO/聚合读；ProjectRepository 仅剩两个 history 方法 | real isolated repository reflection + backend compile | not-required：系统服务合同由真实隔离测试闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T05 | Web 无活动旧产品页/cache/realtime | router 只保留 LegacyIssueRoute；删除 ProjectsPage/projectsApi；realtime 仅 project_space | frontend lint/build + route/source scan | real isolated `project-platform-s21-m2.spec.ts` 验证重定向、终止与 1440/1366/820 | Done |
| PROJECT-PLATFORM-S21-M2-T06 | 活动消费者统一使用 canonical 公共合同 | PersonalWorkQuery、WorkItemCreationCommand、WorkItem resolver/search provider | backend compile + frontend build + API contract checks | real isolated spec 创建空间/类型、发布配置并把消息转换为 canonical WorkItem | Done |
| PROJECT-PLATFORM-S21-M2-T07 | 无 fixed legacy DTO 跨模块泄漏 | 删除 ProjectModels/UserProjectDtos/ProjectMessaging/legacy resolver | architecture boundaries + Maven test compile | not-required：类型泄漏由编译和架构图确定性校验 | Done |
| PROJECT-PLATFORM-S21-M2-T08 | owner/架构/route/event 门禁阻断恢复 | V139 owner range、canonical boundary baseline、security audit target 更新 | architecture boundaries PASS；V139 integration | not-required：系统门禁由真实隔离迁移和静态门禁闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T09 | 历史证据不丢失 | 保留 projects/issues、migration batch/unit/map/provenance/verification、V138 audit | real isolated V001→V139 migration 与 `to_regclass` assertions | not-required：恢复锚点为数据库系统闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T10 | 深链/转换/离线/视口失败关闭 | location-only compat、canonical conversion、统一不可用文案 | Playwright isolated API + UI assertions | real isolated spec 覆盖旧 API 404、canonical 列表、离线与视口 | Done |
| PROJECT-PLATFORM-S21-M2-T11 | 产品活动 legacy 残留为零，历史符号有登记 | 允许清单仅含 migration/audit/location、历史 `issue_embed` schema、project-register“问题”种类及待 M3 替换的非生产 capacity-v1 fixture | Spring mapping/repository assertions + rg inventory + architecture gate | not-required：系统反查由真实隔离和静态证据闭环 | Done |
| PROJECT-PLATFORM-S21-M2-T12 | 文档与 V139 实现一致 | roadmap、current/target、module/object/event 文档同步 | workbench documentation/quality gate | not-required：文档集成合同 | Done |

## Code Changes
- 新增 `WorkItemCreationCommand`/canonical adapter 与 V139 退出迁移。
- 删除旧 Project/Issue Controller、service、DTO、active repository、平台对象 resolver、Web 页面/client 和旧 realtime 协调。
- 重写 Workspace dashboard、IM/Knowledge conversion、Search/Platform/Notification/Base consumer 为 canonical identity。
- 新增 `LegacyIssueProductExitIntegrationTests` 与真实隔离 `project-platform-s21-m2.spec.ts`。
- ratchet architecture baseline、SQL exception owner 与 V001-V139 table-owner 范围。

## Validation
- Backend tests: Maven compile/test-compile PASS；real isolated `LegacyIssueProductExitIntegrationTests` PASS
- Frontend build: `pnpm web:build` PASS；lint PASS（仅 2 个既有 hook warning）
- Local quality gate: light checkpoint PASS，`quality-gate-20260729T022615.md`
- Browser smoke: real isolated `project-platform-s21-m2.spec.ts` 由 M2 finish 执行

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S21-M3-T03 | capacity-v1 中 legacy project/issue fixture 仅为显式非生产历史负载生成器，不可作为 S21 证据；M3 必须交付 canonical WorkItem 四场景 seed 后停用它 | non-blocking for M2 product-contract exit；blocking for M3 engineering Go | M3-T03 |
| PROJECT-PLATFORM-S21-M4 | 尚无真人研发/市场/HR/交付试用证据 | non-blocking for M2；M4 必须由真人执行 | M4 |

## Next Steps
- 独立启动 PROJECT-PLATFORM-S21-M3，完成 canonical capacity、安全、备份/恢复与完整 route-final。
