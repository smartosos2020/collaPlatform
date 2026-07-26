# PROJECT-PLATFORM-S07-M2 Execution Report

## Scope
PROJECT-PLATFORM-S07-M2-T01 至 PROJECT-PLATFORM-S07-M2-T12

## Verification Contract
| Task | Closure class | Verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M2-T01 | non-core | unit | not-required | not-required | No | canonical JSON、unset/null、集合、引用与 unsupported 类型语义 |
| PROJECT-PLATFORM-S07-M2-T02 | core-system | system-real-isolated | not-required | isolated | No | V087 投影、参与者、不可变活动与复合隔离 |
| PROJECT-PLATFORM-S07-M2-T03 | core-system | system-real-isolated | not-required | isolated | No | 11 类 snapshot codec、校验和同语义 hash |
| PROJECT-PLATFORM-S07-M2-T04 | core-system | system-real-isolated | not-required | isolated | No | 权威 JSONB、投影、实例版本、活动、回执、审计和 outbox 原子提交 |
| PROJECT-PLATFORM-S07-M2-T05 | core-system | system-real-isolated | not-required | isolated | No | capability 查询投影、config hash 漂移隔离和重建 |
| PROJECT-PLATFORM-S07-M2-T06 | core-system | system-real-isolated | not-required | isolated | No | read/write/required 和 hidden 零披露 |
| PROJECT-PLATFORM-S07-M2-T07 | core-system | system-real-isolated | not-required | isolated | No | 参与者角色、用户状态、幂等、乐观版本和最后责任人 |
| PROJECT-PLATFORM-S07-M2-T08 | core-system | system-real-isolated | not-required | isolated | No | 实例单调序号、actor、不可变触发器和安全 payload |
| PROJECT-PLATFORM-S07-M2-T09 | core-system | system-real-isolated | not-required | isolated | No | capability 白名单、受控 SQL 模板和类型索引 |
| PROJECT-PLATFORM-S07-M2-T10 | non-core | integration | not-required | not-required | No | project 公共事件合同、稳定去重键和无隐藏值载荷 |
| PROJECT-PLATFORM-S07-M2-T11 | core-system | system-real-isolated | not-required | isolated | No | codec、投影重建、参与者、活动、权限、回滚和跨空间负例 |
| PROJECT-PLATFORM-S07-M2-T12 | core-system | system-real-isolated | not-required | isolated | No | 251 实例 text eq 查询 plan、limit 50 和限制声明 |

## Completed Items
- V087 建立类型化字段投影、参与者和不可变活动账本；三张表均带 workspace/space/item 复合边界并归 project owner。
- `WorkItemFieldValueCodec` 只解释实例绑定 snapshot 中的 11 类注册字段。显式 null 与 unset 均删除值，集合去重排序，number/date/datetime/URL/reference 使用稳定规范编码；interval/computed 保持未注册并稳定拒绝。
- `project_work_items.field_values` 保持唯一权威，创建/更新在同一事务替换派生投影；投影保存 config hash 和 canonical hash，可检测语义漂移并从权威 JSONB 重建。
- 用户 API 增加类型化字段查询、参与者和活动读取/变更。查询只接受 published capability、`eq` 模板、受控 sort direction 和服务端选择的类型列。
- 创建者自动成为 owner；owner/assignee/collaborator/watcher 角色、active 用户、expected version、幂等回执和最后责任人约束已落地。
- 活动按实例单调序号追加，数据库拒绝常规更新/删除；字段命令活动只记录版本/状态，不记录字段 key 或值，避免 hidden 值进入历史。
- `project.contract.WorkItemChangedEvent` 冻结搜索/通知/协作可消费的最小公共事件，不提前创建对应产品投影或 realtime handler。

## Acceptance Evidence
| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M2-T01 | 每类值和 unsupported 行为稳定 | `WorkItemFieldValueCodec` 类注释与类型分支 | `WorkItemFieldValueCodecTests` | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T02 | 投影/参与者/活动隔离和约束完整 | V087 | Flyway V001-V087 + schema integration | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T03 | 11 类 codec、校验、canonical hash | codec + registry descriptor | 11 类型同语义 hash/非法类型测试 | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T04 | authority/projection/activity/side effects 同事务 | service + repository replace/touch/append | stale/last-owner 失败不留 receipt；M1/M2 integration PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T05 | capability 投影可重建且按配置隔离 | projection config hash、rebuild API boundary | 删除派生行后重建并保持 authority 测试 | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T06 | hidden 不进入响应、错误、历史或事件 | runtime projection + minimal activity/event payload | guest query hidden；event payload 无字段值 | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T07 | 参与者权限、角色、用户状态、并发和幂等 | participant repository/service/API | upsert replay、remove、last responsible、version tests | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T08 | 活动不可变、序号/actor 稳定 | activity table trigger/repository/API | created/participant sequence + forged update rejection | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T09 | 仅 capability 白名单和受控模板 | service whitelist + repository controlled column map | typed eq query + unsupported/hidden rejection | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T10 | 公共事件可 replay 且不泄漏 | `WorkItemChangedEvent` + request-id outbox key | event row/payload assertions | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T11 | 正反例、重建、回滚、隐藏和隔离 | codec/unit + service integration + ArchUnit | focused suites PASS | Not required | Done |
| PROJECT-PLATFORM-S07-M2-T12 | 代表性 plan 和限制可追溯 | 251 实例、text eq、limit 50 夹具 | `EXPLAIN (ANALYZE, BUFFERS)` 命中 typed index | Not required | Done |

## Query Budget
- Environment: Testcontainers PostgreSQL 16.14，Flyway V001-V087 空库升级。
- Data shape: 单 workspace、单 space、单 type、251 个 work item、每项一个 text 投影；过滤 `priority eq "normal"`，按该字段降序并 limit 50。
- Plan assertion: `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)` 必须包含 `idx_project_work_item_field_projection_text`；专项测试已通过。
- Scope limit: 该证据不是 10 万实例、组合过滤、group、复杂视图、并发或生产容量结论；本阶段只承诺受控单字段 `eq` 与有界返回。

## Code Changes
- `server/src/main/resources/db/migration/V087__create_work_item_runtime_projections.sql`
- `server/src/main/java/com/colla/platform/modules/project/{api,application,contract,domain,infrastructure}/**`
- `server/src/test/java/com/colla/platform/{architecture,modules/project/application}/**`
- `tools/workbench/config/platform-table-owners.json`
- `docs/01-architecture/{current-architecture,platform-object-model,platform-module-contracts,event-side-effect-matrix}.md`

## Validation
- Backend compile: PASS.
- Backend tests: `mvn -Dtest=WorkItemFieldValueCodecTests,WorkItemServiceIntegrationTests,ModuleArchitectureTests test`，focused suites PASS。
- Frontend build: Not required；M2 未修改 `web/**`，用户侧规范工作项 UI 属于 M5。
- Migration: isolated PostgreSQL 16.14，Flyway V001-V087 87/87 PASS。
- Browser smoke: Not required；M2 交付后端动态值、参与者、活动和查询合同，用户浏览器竖切属于 M5。
- Local quality gate: stage checkpoint PASS（`.local-reports/quality-gate-20260726T065021.md`）；focused backend suites PASS；architecture boundaries PASS（backendPrivate=140、crossOwnerReads=93）；architecture contracts PASS（15 modules、109 active tables、23 contract files）；planning contract 与 `git diff --check` PASS。最终 finish 由工作台独立复验。

## Remaining Gaps
| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| PROJECT-PLATFORM-S07-M3 | legacy resolver、显式 ID map、shadow read 与受控切流尚未实现 | legacy 继续保持旧业务事实源 | M3 |
| PROJECT-PLATFORM-S07-M4 | 分批迁移、独立 verify、resume 与 rollback 尚未实现 | 禁止生产迁移或切流 | M4 |
| PROJECT-PLATFORM-S07-M5 | 用户列表/详情、评论、附件和浏览器身份矩阵尚未实现 | M2 不声明完整用户产品 | M5 |
| N/A | 组合 filter/sort/group、复杂视图和大规模查询；M2 只提供单字段受控 `eq` 底座 | non-blocking | PROJECT-PLATFORM-S13 |

## Next Steps
- 进入 PROJECT-PLATFORM-S07-M3，冻结 legacy/canonical identity map、统一 resolver、兼容读取与切流开关。
- 保持 legacy project/issue 写入口不变；M3 仍不得建立静默双写。
