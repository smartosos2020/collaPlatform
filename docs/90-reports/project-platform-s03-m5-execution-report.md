# PROJECT-PLATFORM-S03-M5 Execution Report

## Scope
PROJECT-PLATFORM-S03-M5-T01 to PROJECT-PLATFORM-S03-M5-T09

## Verification Contract
| Task | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M5-T01 | e2e-real-isolated | real | isolated | No | 复核 M1-M4 后以 owner/admin/member/guest/nonmember/enterprise-admin 隔离链路重放关键合同 |
| PROJECT-PLATFORM-S03-M5-T02 | static | not-required | not-required | No | 文档事实与代码、schema 和已执行证据逐项对照，不需要浏览器动作 |
| PROJECT-PLATFORM-S03-M5-T03 | e2e-real-isolated | real | isolated | No | 真实 API 和数据库覆盖类型动作、最小披露、系统保护及空间角色边界 |
| PROJECT-PLATFORM-S03-M5-T04 | integration | not-required | not-required | No | 通过不可变版本、引用守卫和不存在实例表/API 的集成及静态证据复核扩展边界 |
| PROJECT-PLATFORM-S03-M5-T05 | integration | not-required | not-required | No | PostgreSQL 16 执行 V001-V063 空库、V060 升级和逐空间预置补齐 rehearsal |
| PROJECT-PLATFORM-S03-M5-T06 | e2e-real-isolated | real | isolated | No | 独立夹具重放 owner/admin/member/guest/nonmember/enterprise-admin 配置、摘要、深链与最小披露 |
| PROJECT-PLATFORM-S03-M5-T07 | e2e-real-isolated | real | isolated | No | 使用本轮 fresh 浏览器日志并执行 route-final 全后端、构建、协作与静态门禁 |
| PROJECT-PLATFORM-S03-M5-T08 | static | not-required | not-required | No | 基于实现和验收事实形成 Go S04 决策及可拆分 schema/API/index 输入 |
| PROJECT-PLATFORM-S03-M5-T09 | static | not-required | not-required | No | 同步 Program、索引、目标架构和唯一当前路线的 revision/status 合同 |

## Completed Items
- M1-M4 的 41 个任务逐项复核，类型定义、不可变版本、API/授权、双视角 UI、预置补齐和 legacy 零切流均有代码与可重复测试证据。
- 新增普通非成员真实浏览器负例，确认私有空间不出现在目录，配置/摘要 API 返回 404，直接深链只显示最小披露状态；企业管理员同样不能越过空间成员边界。
- 新增启动补齐逐空间隔离测试，确认自定义预置 key 冲突进入失败报告，而健康空间仍安装完整六类预置。
- 加强 V060 升级 rehearsal，确认 V061-V063、命令回执表和两个不可变/系统保护触发器存在，同时 `project_work_items` 仍不存在。
- 更新当前事实文档并冻结目标架构第 20 节 S04 准入包；S03 结论为 Go S04，S04 保持 Planned 直到本路线归档。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S03-M5-T01 | M1-M4 的 schema、API、权限与用户闭环均有本轮可重复证据 | M1-M4 执行报告、V061-V063、类型 service/controller、配置 UI 与两份 S03 E2E spec | checkpoint `quality-gate-20260722T131723.md` PASS；M5 定向后端 9/9 PASS | real: 隔离 M3 链路 1/1 PASS，覆盖六类身份和最小披露 | Done |
| PROJECT-PLATFORM-S03-M5-T02 | 当前事实与 S04-S07 目标边界分离且可定位 | `current-product-scope.md`、`current-architecture.md`、`platform-object-model.md`、`technology-selection.md` | planning/documentation checkpoint PASS | not-required: 纯事实文档同步由静态合同检查验证 | Done |
| PROJECT-PLATFORM-S03-M5-T03 | 类型生命周期、版本不可变、权限、安全、并发和隔离无阻断缺口 | 领域守卫、V061/V063 触发器、统一 action policy、复合 workspace/space 约束 | 类型领域/service/controller/schema/preset 集成测试及 route-final 全量测试 | real: owner/admin 正向动作与 member/guest/nonmember/enterprise-admin 负向链路 PASS | Done |
| PROJECT-PLATFORM-S03-M5-T04 | S04 字段、S06 发布和 S07 type_version 连接点清晰且没有提前实现 | 目标架构第 20.3 节、可扩展 config 根、`WorkItemTypeReferenceGuard`、published v1 保护 | schema rehearsal 断言 `project_work_items` 不存在；legacy 边界测试断言无第二套实例 API | not-required: 架构扩展合同由代码/schema 负例和集成测试验证 | Done |
| PROJECT-PLATFORM-S03-M5-T05 | V001-V063 空库与 V060 升级可重复，逐空间补齐失败隔离且数据边界不变 | Flyway V061-V063、preset reconciliation/backfill service 与运行手册 | PostgreSQL 16 定向测试 9/9 PASS；空库、V060 升级、冲突隔离和 legacy 零写入均覆盖 | not-required: 迁移正确性由真实 PostgreSQL schema/数据断言判定 | Done |
| PROJECT-PLATFORM-S03-M5-T06 | 六类身份的配置、摘要、系统保护、生命周期、深链和最小披露符合合同 | M3/M4 Playwright specs 使用独立 workspace、用户、空间和清理路径 | Playwright 隔离链路在本轮 fresh 执行 | real: M3 1/1 PASS；route-final 重放 M3+M4 两份 spec | Done |
| PROJECT-PLATFORM-S03-M5-T07 | 路线级完整测试、迁移、构建、协作与静态门禁全部通过 | workbench `route-final` profile 和本轮两份真实浏览器 spec | `quality-gate-20260722T132203.md` full PASS；后端 189/189、协作 15/15 | real: isolated Playwright 2/2 PASS，日志 `work-cycle-browser-20260722T132203.log` | Done |
| PROJECT-PLATFORM-S03-M5-T08 | Go S04 决策、风险与字段 schema/API/索引输入可以直接拆 Task | 目标架构第 20 节固定字段类型、配置、授权、索引、S06/S07 扩展和禁止项 | planning contract 与文档结构门禁 PASS | not-required: Stage 决策基于实现、数据库和真实浏览器证据汇总 | Done |
| PROJECT-PLATFORM-S03-M5-T09 | S03 完成态、revision 11 和下一 Stage 状态在活动规划文档同步 | Program revision 11、S03 Completed/current_stage none、路线 completed、initiative index none | `pnpm work:plan-check` 与 stage-final planning contract | not-required: 规划状态由机器可校验 front matter 和表格验证 | Done |

## Code Changes
- `web/e2e/project-platform-s03-m3-work-item-types.spec.ts`：增加普通非成员 API、空间目录和直接深链最小披露验证，并保留企业管理员负例。
- `server/src/test/java/com/colla/platform/modules/project/application/WorkItemTypePresetReconciliationServiceIntegrationTests.java`：增加单空间冲突不阻断健康空间补齐测试。
- `server/src/test/java/com/colla/platform/modules/project/infrastructure/ProjectWorkItemTypeSchemaIntegrationTests.java`：增强 V060 升级后的命令回执、触发器和无实例表断言。
- 当前产品、当前架构、平台对象、技术选型、Program、专项索引、目标架构和当前路线同步 S03 完成事实与 S04 准入边界。

## Validation
- Backend tests: M5 定向 PostgreSQL 测试 9/9 PASS；route-final Maven 189/189 PASS，package PASS
- Frontend build: lint、TypeScript/Vite build、chunk budget 和 lazy-route 检查全部 PASS
- Local quality gate: `quality-gate-20260722T132203.md` full PASS；Flyway、安全、敏感数据、活动脚本、文档和 diff 门禁均 PASS
- Browser smoke: `work-cycle-browser-20260722T132203.log` fresh isolated M3+M4 2/2 PASS；协作 Node 测试 15/15 PASS

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | 自定义类型占用研发预置 key 时采用显式冲突报告与人工治理，不自动改名或覆盖 | non-blocking | `docs/05-runbooks/project-work-item-type-presets.md` 冲突处置流程 |
| N/A | S04 通过准入但在 S03 路线归档前保持 Planned，避免同时存在两条活动 Stage 路线 | non-blocking | 归档 S03 后依据目标架构第 20 节激活 S04 |

## Next Steps
- 归档 completed 的 S03 当前路线。
- 依据目标架构第 20 节生成 S04 动态字段路线，完成规划检查后再激活执行。
