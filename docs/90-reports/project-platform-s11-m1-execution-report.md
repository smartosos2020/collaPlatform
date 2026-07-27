# PROJECT-PLATFORM-S11-M1 Execution Report

## Scope
PROJECT-PLATFORM-S11-M1-T01 到 PROJECT-PLATFORM-S11-M1-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M1-T01 | non-core | static | not-required | not-required | No | 本地代码、路线和已提交架构全文审计 |
| PROJECT-PLATFORM-S11-M1-T02 | non-core | unit | not-required | not-required | No | permission definition 领域合同测试 |
| PROJECT-PLATFORM-S11-M1-T03 | core-system | system-real-isolated | not-required | isolated | No | snapshot v1-v5 canonical/validation |
| PROJECT-PLATFORM-S11-M1-T04 | core-system | system-real-isolated | not-required | isolated | No | PostgreSQL 16 V001-V101 fresh/repeat migration |
| PROJECT-PLATFORM-S11-M1-T05 | core-system | system-real-isolated | not-required | isolated | No | 既有 draft/publish/template 真实服务链 |
| PROJECT-PLATFORM-S11-M1-T06 | non-core | unit | not-required | not-required | No | 继承/deny/action/subject diagnostics |
| PROJECT-PLATFORM-S11-M1-T07 | core-system | system-real-isolated | not-required | isolated | No | diff/compatibility/template lineage |
| PROJECT-PLATFORM-S11-M1-T08 | non-core | unit | not-required | not-required | No | 六类系统类型确定性 preset/hash |
| PROJECT-PLATFORM-S11-M1-T09 | non-core | unit | not-required | not-required | No | legacy 来源 manifest 校验 |
| PROJECT-PLATFORM-S11-M1-T10 | non-core | static | not-required | not-required | No | 最小 decision/explanation/request/event 公共合同 |
| PROJECT-PLATFORM-S11-M1-T11 | core-system | system-real-isolated | not-required | isolated | No | targeted Maven + PostgreSQL + architecture gates |
| PROJECT-PLATFORM-S11-M1-T12 | non-core | static | not-required | not-required | No | roadmap/report/target/current/module/object/event 同步 |

## Completed Items
- 审计并冻结 enterprise RBAC、space role、WorkItem role、participant、field/node/relation policy、通用 ACL 的分层来源和禁止依赖。
- configuration snapshot 升级到 v5，在 S06 唯一 draft/published authority 内承载 role、policy、selector、data scope 与 legacy manifest。
- 实现确定性 preset、canonical hash、继承/deny/action/subject/data-scope 校验，以及 diff、compatibility、rollback、template lineage 接入。
- V101 建立空间角色绑定、事项角色分配、权限命令回执和不可变 decision evidence；四表纳入 project owner 与空间清理闭包。
- 冻结最小 permission decision/explanation/request/event 公共合同；M1 没有激活运行授权、授权 API 或 UI。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S11-M1-T01 | 来源、调用方与禁止依赖可定位 | roadmap/target/current + code/schema audit | 全文件读取与 `rg` 审计 | 不需要：事实审计 | Done |
| PROJECT-PLATFORM-S11-M1-T02 | 永久 key、来源、优先级和作用域无歧义 | `WorkItemPermissionModels` | permission definition tests | 不需要：领域合同 | Done |
| PROJECT-PLATFORM-S11-M1-T03 | 定义进入唯一版本化配置链 | snapshot v5、assembler、canonicalizer、validator | configuration unit tests | 不需要：无 UI | Done |
| PROJECT-PLATFORM-S11-M1-T04 | 复合隔离、唯一性、索引和不可变保护完整 | V101 + project-space cleanup | PostgreSQL fresh 101/repeat 0 | 不需要：数据库基座 | Done |
| PROJECT-PLATFORM-S11-M1-T05 | 草稿 hash/乐观版本且无双写 | draft preservation、domain DTO、existing publication path | compile + configuration regression | 不需要：配置服务 | Done |
| PROJECT-PLATFORM-S11-M1-T06 | 非法继承/动作/来源稳定失败关闭 | `WorkItemPermissionDefinitionValidator` | cycle/unknown action/enterprise-owner tests | 不需要：校验器 | Done |
| PROJECT-PLATFORM-S11-M1-T07 | 兼容分级不可绕过 | diff keying、compatibility rules、template preset | permission tightening compatibility tests | 不需要：发布链 | Done |
| PROJECT-PLATFORM-S11-M1-T08 | 系统预置确定且企业管理员不成为内容 owner | `WorkItemPermissionPresetCatalog` | deterministic hash/owner boundary tests | 不需要：预置数据 | Done |
| PROJECT-PLATFORM-S11-M1-T09 | legacy 来源显式承接且未知项失败 | `LegacyPermissionMapping` manifest | preset/validator tests | 不需要：M1 不迁移 | Done |
| PROJECT-PLATFORM-S11-M1-T10 | 最小公共合同且私表不外泄 | contracts/event v1、owner manifest、架构合同 | compile + architecture contracts | 不需要：公共合同 | Done |
| PROJECT-PLATFORM-S11-M1-T11 | schema/config/boundary 回归通过 | unit/integration suites | targeted Maven、PostgreSQL、workbench gates | 不需要：M1 无浏览器流 | Done |
| PROJECT-PLATFORM-S11-M1-T12 | 文档只声明 M1 定义底座 | roadmap、report、target/current/module/object/event | checkpoint + finish | 不需要：文档同步 | Done |

## Code Changes
- `server/src/main/java/com/colla/platform/modules/project/domain/WorkItemPermissionModels.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemPermissionDefinitionValidator.java`
- `server/src/main/java/com/colla/platform/modules/project/application/WorkItemPermissionPresetCatalog.java`
- `server/src/main/java/com/colla/platform/modules/project/contract/WorkItemPermissionContracts.java`
- `server/src/main/java/com/colla/platform/modules/project/contract/WorkItemPermissionChangedEvent.java`
- configuration snapshot/draft/template/diff/compatibility 既有链路扩展。
- `server/src/main/resources/db/migration/V101__create_work_item_permission_foundation.sql`
- permission definition 与 PostgreSQL foundation 自动化测试、table owner 和架构合同。

## Validation
- Backend tests: permission/config targeted suites PASS；PostgreSQL 16 V001-V101 fresh/repeat foundation PASS。
- Frontend build: Not required；M1 未修改 Web。
- Local quality gate: `git diff --check`、architecture contracts/boundaries 与 stage checkpoint；fresh closing report `.local-reports/quality-gate-20260727T053938.md`。
- Browser smoke: Not required；M1 明确没有运行授权 API 或用户 UI。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| N/A | None | non-blocking | Closed |

## Next Steps
- 从 PROJECT-PLATFORM-S11-M2-T01 复核本报告、snapshot v5 与 V101；激活绑定 snapshot 驱动的统一运行决策，不读取 permission/identity/project 私表拼授权。
