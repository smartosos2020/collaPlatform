---
title: KB-UX-M11 执行报告
status: archived
milestone: KB-UX-M11
updated_at: 2026-07-04
---

# KB-UX-M11 执行报告

> 命名说明：本文件是历史 KB-UX-M11 报告。当前 UI 拆分路线的 UI-SPLIT-M11 报告见 `docs/90-reports/ui-split-m11-execution-report.md`；保留本文件是为了不破坏历史报告链接和 AI 工作循环的旧文件名检查。

## 本轮范围

KB-UX-M11-T01 到 KB-UX-M11-T08：版本、导入导出、治理和迁移工具。

目标是让结构化 blocks 不只停留在编辑器体验，而是进入版本历史、导入导出、治理健康度和迁移试运行闭环。

## 完成项

| 任务 | 状态 | 代码/文档依据 |
| --- | --- | --- |
| KB-UX-M11-T01 | Done | `DocumentService.saveBlocks` 对比保存前后 block，生成新增、删除、修改、移动和类型转换摘要。 |
| KB-UX-M11-T02 | Done | 新增 `/api/docs/{documentId}/import/html`；基础 HTML 转 blocks，复杂 HTML 进入 `legacy_html`，危险 HTML 写安全占位。 |
| KB-UX-M11-T03 | Done | Markdown/HTML 导出优先读取 active blocks，嵌入对象输出安全 directive 或占位。 |
| KB-UX-M11-T04 | Done | 知识库治理健康度补充 block 覆盖缺口、空内容块、失效嵌入对象和覆盖率。 |
| KB-UX-M11-T05 | Done | 迁移检查脚本补旧富文本回滚覆盖和失效嵌入对象检查。 |
| KB-UX-M11-T06 | Done | 新增 block v2 试运行脚本和 `pnpm kb:block-v2-trial`。 |
| KB-UX-M11-T07 | Done | 产品范围、架构、平台对象模型、runbook 和 AI 工程治理已同步 blocks 边界。 |
| KB-UX-M11-T08 | Done | 集成测试覆盖版本摘要、导入导出、治理指标；脚本验证已生成报告。 |

## 代码变更

- `server/src/main/java/com/colla/platform/modules/doc/application/DocumentService.java`
  - block 保存版本摘要从固定文案改为 block 级变更统计。
  - Markdown/HTML 导出从 active blocks 生成，嵌入对象安全降级。
  - 新增 HTML 导入，基础标签转 blocks，复杂/危险 HTML 降级。
- `server/src/main/java/com/colla/platform/modules/doc/api/DocumentController.java`
  - 新增 `/api/docs/{documentId}/import/html`。
- `server/src/main/java/com/colla/platform/modules/doc/domain/DocumentModels.java`
  - `KnowledgeBaseHealthMetrics` 增加 block 治理指标。
- `server/src/main/java/com/colla/platform/modules/doc/application/KnowledgeBaseSpaceService.java`
  - 治理 dashboard 和 CSV 增加 block 覆盖、空块、失效嵌入对象指标和风险。
- `scripts/knowledge-base-migration-check.ps1`
  - 增加 invalid embedded object blocks 和 old rich text rollback coverage 检查。
- `scripts/knowledge-base-block-v2-trial.ps1`
  - 新增知识库 block v2 试运行报告脚本。
- `package.json`
  - 新增 `pnpm kb:block-v2-trial`。
- `server/src/test/java/com/colla/platform/modules/doc/api/DocumentControllerIntegrationTests.java`
  - 新增 M11 目标集成测试。

## 文档变更

| 文档 | 动作 |
| --- | --- |
| `docs/02-roadmap/current-roadmap.md` | KB-UX-M11-T01 到 T08 标记完成并记录实现依据。 |
| `docs/00-product/current-product-scope.md` | 固定 blocks 为知识内容正文 v2 主事实来源。 |
| `docs/01-architecture/current-architecture.md` | 同步 V043、导入导出、治理指标和脚本边界。 |
| `docs/01-architecture/platform-object-model.md` | 补充嵌入对象导出安全降级和治理检查规则。 |
| `docs/05-runbooks/admin-operations.md` | 增加知识库 v2 迁移检查和 block 试运行命令。 |
| `docs/03-engineering/ai-engineering-governance.md` | 固定 blocks 优先和迁移证据要求。 |
| `docs/90-reports/m11-execution-report.md` | 新增本报告。 |

## 验证

- `mvn -f server/pom.xml -Dtest=DocumentControllerIntegrationTests#knowledgeBaseBlockV2VersionImportExportAndGovernanceFlow test`：通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-base-migration-check.ps1`：通过执行，报告 `.local-reports/kb-migration-check-20260704-180853.md`，Decision 为 GO-WITH-REVIEW。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/knowledge-base-block-v2-trial.ps1 -SkipApi`：通过执行，报告 `.local-reports/kb-block-v2-trial-20260704-180853.md`。
- AI 工作循环 checkpoint：通过，质量门报告 `.local-reports/quality-gate-20260704-181400.md`。
- AI 工作循环 finish：通过，质量门报告 `.local-reports/quality-gate-20260704-181438.md`。

## 遗留 Gap

- 本地迁移检查的 GO-WITH-REVIEW 来自 2 个知识库根节点仍带 deprecated metadata shadow 字段；不是脚本失败，M12 收口需要清理或写入兼容保留决策。
- block v2 试运行脚本本轮以 `-SkipApi` 验证报告生成和覆盖清单；完整 API 试运行留到 M12，在后端重启并进入最终收口时执行。
- HTML 导入是轻量解析，不覆盖复杂 DOM 到原生块的完整转换；复杂结构保守进入 `legacy_html`。

## 下一步

进入 KB-UX-M12：体验收口、全量验证和 v2 冻结。
