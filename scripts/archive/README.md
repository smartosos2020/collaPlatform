# Historical Scripts

本目录保存已完成路线的一次性脚本，不属于当前工具链，也不在根 `package.json` 暴露入口。

| 分类 | 文件 |
| --- | --- |
| M12/M31/M40 数据和试运行 | `reset-m12-test-data.ps1`, `m31-*`, `trial-*`, `team-trial-readiness.ps1` |
| 旧性能基线 | `performance-baseline.ps1` |
| 知识库 v1/v2 迁移验收 | `knowledge-base-*` |
| KB-NAME 清理证据 | `knowledge-naming-inventory.ps1`, `knowledge-compat-observation.ps1` |
| V045 前数据保护 | `knowledge-content-block-snapshot.ps1` |
| 失效浏览器测试 | `e2e/docs-collaboration.spec.ts`, `e2e/m31-collab-simulation.spec.ts` |

这些文件可能调用已删除路由、查询旧 schema 或重置数据库。只允许在隔离副本上复现历史证据，不得复制回活动脚本或测试目录直接运行。
