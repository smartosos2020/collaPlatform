---
title: KB-PRODUCT-M2 Execution Report
status: archived
milestone: KB-PRODUCT-M2
updated_at: 2026-07-15
---

# KB-PRODUCT-M2 Execution Report

## Scope

本轮按 AI 工作循环推进 `KB-PRODUCT-M2-T01` 到 `KB-PRODUCT-M2-T10`。范围限定为规范块模型、确定性转换、迁移预览/批次合同、canonical 投影和模型/数据库测试；没有切换现有编辑器写路径，没有启用 Hocuspocus，也没有删除 Markdown 兼容链路。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M2-T01 | L1 schema/model unit | N/A | isolated JVM | N/A | 无用户界面变更；验证节点和 mark 集合 |
| KB-PRODUCT-M2-T02 | L1 schema/model unit | N/A | isolated JVM | N/A | 无用户界面变更；验证 blockId、父子和顺序 |
| KB-PRODUCT-M2-T03 | L1 projection unit | N/A | isolated JVM | N/A | 无用户界面变更；验证 canonical 到派生投影 |
| KB-PRODUCT-M2-T04 | L1 schema/model unit | N/A | isolated JVM | N/A | 无用户界面变更；验证非法、旧版和未知节点 |
| KB-PRODUCT-M2-T05 | L1 conversion unit | N/A | isolated JVM | N/A | 无用户界面变更；验证 blocks/Markdown 重复转换 |
| KB-PRODUCT-M2-T06 | L1 migration-plan unit | N/A | isolated JVM | N/A | 无用户界面变更；默认 dry-run，不写共享数据 |
| KB-PRODUCT-M2-T07 | L2 migration integration | N/A | Testcontainers PostgreSQL 16 | N/A | 无用户界面变更；空库执行 V001-V051，并验证 V049 存量升级到 V051 |
| KB-PRODUCT-M2-T08 | L1 + L2 snapshot contract | N/A | isolated JVM + Testcontainers | N/A | 无用户界面变更；三类快照使用同一 canonical JSON |
| KB-PRODUCT-M2-T09 | L1 projection unit | N/A | isolated JVM | N/A | 无用户界面变更；验证 plainText、Markdown、对象元数据 |
| KB-PRODUCT-M2-T10 | L1 + L2 model/migration integration | N/A | isolated JVM + Testcontainers | N/A | 无用户界面变更；浏览器 smoke 不适用 |

## Completed Items

| Task | Status | Evidence |
| --- | --- | --- |
| KB-PRODUCT-M2-T01 | Done | `KnowledgeContentCanonicalModels` 固定 schemaVersion 3、Tiptap/ProseMirror 节点和 mark 集合 |
| KB-PRODUCT-M2-T02 | Done | `blockId` 写入 node attrs；父子关系通过嵌套节点与 `parentBlockId` 保留；无 ID 使用确定性 UUID |
| KB-PRODUCT-M2-T03 | Done | canonical document 是唯一结构输入；plainText、Markdown、blocks 和 embed metadata 从其派生 |
| KB-PRODUCT-M2-T04 | Done | schema service 拒绝非法 root/content/future version；旧 alias 升级；未知节点/mark 转可恢复占位 |
| KB-PRODUCT-M2-T05 | Done | legacy blocks 和 Markdown 转 canonical JSON，表格、任务、代码、对象和 divider 有确定性转换 |
| KB-PRODUCT-M2-T06 | Done | dry-run migration plan 和 batch plan 提供源/目标 checksum、失败项和稳定幂等批次 ID |
| KB-PRODUCT-M2-T07 | Done | V050 建立 canonical 文档、迁移批次和迁移项表；V051 增加 canonical snapshot 列和索引/约束；验证空库及 V049 存量升级路径 |
| KB-PRODUCT-M2-T08 | Done | version/template/collaboration 三类记录增加相同 schemaVersion/canonical_snapshot 合同 |
| KB-PRODUCT-M2-T09 | Done | canonical projection 输出 plainText、Markdown 和带 objectType/objectId 的 embed metadata |
| KB-PRODUCT-M2-T10 | Done | 12 个模型/API 测试与 2 个 Flyway 迁移集成测试通过，覆盖空库执行到 v051 及 V049 到 V051 的存量升级 |

## Implementation Details

### Canonical schema

新增 `KnowledgeContentCanonicalModels` 和 `KnowledgeContentSchemaService`。规范文档形状为：

```json
{
  "type": "doc",
  "schemaVersion": 3,
  "content": [
    {
      "type": "paragraph",
      "attrs": { "blockId": "stable-uuid" },
      "content": [{ "type": "text", "text": "正文" }]
    }
  ]
}
```

支持 paragraph、heading、bullet/ordered/task list、blockquote、codeBlock、table、media、embed、callout、horizontalRule、toc，以及 bold、italic、strike、underline、link、code marks。节点字段排序后计算 SHA-256，保证同一输入的 checksum 稳定。

### Migration and projection

- `KnowledgeContentCanonicalService` 从现有 blocks/Markdown 生成 `KnowledgeContentMigrationPlan`，只读返回 canonical document、源/目标 checksum、块数量和 schema issues。
- `KnowledgeContentMigrationBatchService` 按 workspace + idempotency key 生成稳定批次 ID，排序 item 后汇总 checksum；不执行写入，失败项状态为 `failed`，成功项保持 `planned`。
- 未知节点不会静默删除：完整原始 node 写入 `attrs.originalNode`，节点类型降级为 `unsupported`；未知 mark 使用同样的可恢复占位策略。
- 旧 `content`、`blocks`、`richContent` 写路径仍由旧服务维护，只作为 M3/M4 切流前的兼容路径；M2 的 canonical 表和 snapshot 列默认不被业务写入。

新增只读接口：

`GET /api/knowledge-bases/{spaceId}/items/{itemId}/migration/preview`

该接口返回 dry-run 迁移计划，不会覆盖 active blocks、版本、模板或协同状态。

### Database contract

- V050：`knowledge_content_canonical_documents`、`knowledge_content_migration_batches`、`knowledge_content_migration_items`，包含 checksum、幂等键、失败清单、目标文档和状态约束。
- V051：`knowledge_content_versions`、`knowledge_content_templates`、`knowledge_content_collaboration_states` 增加 `canonical_snapshot`，避免修改已执行的 V050 checksum。
- `block_snapshot`、`blocks` 等历史字段没有删除或重写，保证 M2 可以回滚并让 M3/M4 逐步切流。

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated evidence | Browser evidence | Status |
| --- | --- | --- | --- | --- | --- |
| KB-PRODUCT-M2-T01 | 目标节点和 mark 可表达 | `KnowledgeContentCanonicalModels.CURRENT_SCHEMA_VERSION` 与 schema sets | `KnowledgeContentSchemaServiceTests.canonicalDocumentExpressesTheM2BlockAndMarkSet` | N/A：后端模型改动 | Done |
| KB-PRODUCT-M2-T02 | 编辑/保存/刷新不按数组下标重配身份 | attrs.blockId、确定性 UUID、parentBlockId | `repeatedLegacyConversionIsDeterministicAndPreservesExistingIds`、父子测试 | N/A：后端模型改动 | Done |
| KB-PRODUCT-M2-T03 | 结构唯一、投影可重建 | canonical document + `project` | 表格/对象和 Markdown 投影测试 | N/A：后端模型改动 | Done |
| KB-PRODUCT-M2-T04 | 非法拒绝、旧版升级、未知保留 | schema exception、alias normalize、unsupported node/mark | future/malformed/unknown 测试 | N/A：后端模型改动 | Done |
| KB-PRODUCT-M2-T05 | legacy blocks/Markdown 重复转换一致 | `fromLegacy` + Markdown parser + table conversion | 8 个 schema tests 全部通过 | N/A：后端模型改动 | Done |
| KB-PRODUCT-M2-T06 | dry-run、失败清单、checksum、幂等 | `KnowledgeContentMigrationBatchService` | 2 个 batch plan tests 全部通过 | N/A：默认不执行写入 | Done |
| KB-PRODUCT-M2-T07 | 空库/存量升级有 canonical migration contract | V050/V051 Flyway migrations | `KnowledgeSchemaMigrationIntegrationTests`，空库 + V049→V051，2/2 | N/A：数据库迁移改动 | Done |
| KB-PRODUCT-M2-T08 | 三类快照共享 schema 版本和 canonical snapshot | 三张表的 schema_version/canonical_snapshot 列 | 信息架构断言 3/3 列存在 | N/A：数据库迁移改动 | Done |
| KB-PRODUCT-M2-T09 | 搜索和对象投影可定位且不带正文权限泄露 | plainText/Markdown/embed metadata projection | table/embed projection tests | N/A：后端投影改动 | Done |
| KB-PRODUCT-M2-T10 | 覆盖嵌套、表格、媒体、对象、未知和损坏输入 | 8 schema + 2 batch + API + migration tests | 12/12 unit/API tests，Flyway integration 2/2 | N/A：无用户流程改动 | Done |

## Code Changes

- Backend domain: 新增 `KnowledgeContentCanonicalModels`、`KnowledgeContentMigrationModels`。
- Backend application: 新增 `KnowledgeContentSchemaService`、`KnowledgeContentCanonicalService`、`KnowledgeContentMigrationBatchService`；新增 migration preview API。
- Database: 新增 V050、V051 canonical 文档/迁移批次/快照合同。
- Tests: 新增 schema 和 migration batch 模型测试，扩展 API/Flyway 合同测试。
- Existing product path: 未修改旧编辑器保存、块替换、版本恢复和协同 snapshot 写入路径。

## Documentation Changes

| Document | Action | Reason |
| --- | --- | --- |
| `docs/02-roadmap/current-roadmap.md` | M2 T01-T10 标记 Done，执行入口切换到 M3 | 与实际完成度一致 |
| 本报告 | 新建并归档 | 记录实现、验证、迁移边界和后续切流条件 |

## Validation

- `mvn -f server/pom.xml "-Dtest=KnowledgeContentSchemaServiceTests,KnowledgeContentMigrationBatchServiceTests,KnowledgeContentApiContractTests" test`：12 tests，0 failures/errors。
- `mvn -f server/pom.xml -Dtest=KnowledgeSchemaMigrationIntegrationTests test`：2 tests，0 failures/errors；Testcontainers PostgreSQL 16，覆盖空库执行 51 migrations 到 v051，以及独立数据库从 V049 升级到 V051。
- `mvn -f server/pom.xml -DskipTests compile`：BUILD SUCCESS。
- `git diff --check`：通过。
- Browser smoke：不适用。M2 没有前端用户流程改动，finish 采用 `BrowserNotRequiredReason`，不使用 mock 浏览器替代真实证据。

## Remaining Gaps

| Related task | Gap | Acceptance effect | Tracking |
| --- | --- | --- | --- |
| M3/M4 | canonical snapshot 仍未成为编辑器正式写入事实，旧 blocks/Markdown 路径仍保留 | 不阻塞 M2；M2 明确要求不能提前切流 | M3 单一编辑器与 M4 保存状态机 |
| M5/M6 | 尚未接入 Hocuspocus/Yjs binary update log、多用户和双节点协同 | 不阻塞 M2；canonical schema 是其前置合同 | M5-M6 |
| M7/M9 | 有效新 Base resolver、连续输入和完整 UI 交互仍需真实流程闭环 | 不阻塞 M2；本轮只保留对象 metadata | M7、M9 |

以上均为 M2 之后的明确范围，不是 M2 T01-T10 的未完成项。

## Next Steps

- 当前唯一执行入口：`KB-PRODUCT-M3-T01` 到 `KB-PRODUCT-M3-T11`。
- M3 应先建立 canonical snapshot 与编辑器的单一读路径，禁止把 Markdown 往返重新引入正式保存链路。
- M3 通过后，M4 再实现保存状态机、canonical snapshot 写入、失败不覆盖和版本恢复合同。
