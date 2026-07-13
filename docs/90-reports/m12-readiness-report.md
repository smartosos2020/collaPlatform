# M12 内测准入执行记录

执行日期：2026-06-14

## 1. 范围

本轮按 AI 工作循环推进 M12-T01 到 M12-T08，目标是建立干净内测基线、校准测试用例、确认质量门禁和账号可用性。

## 2. 执行结果

| 任务 | 状态 | 证据 |
| --- | --- | --- |
| M12-T01 数据清理与重置流程 | 已完成 | `scripts/reset-m12-test-data.ps1` 成功执行；最终只保留三个账号 |
| M12-T02 测试用例校准 | 已完成 | `docs/m12-user-acceptance-test-cases.md` 和 `docs/m12-ai-verification-test-cases.md` 已更新 |
| M12-T03 IM 核心交互稳定化 | 已完成 | `pnpm verify` 中 `ImControllerIntegrationTests` 通过；前序浏览器已验证 IM 右键菜单 |
| M12-T04 跨模块端到端回归 | 已完成 | `pnpm verify` 覆盖 IM、项目、文档、表格、审批、搜索、通知集成测试 |
| M12-T05 阻塞问题处理 | 已完成 | 当前质量门禁无 P0/P1 阻塞失败 |
| M12-T06 高频脚本沉淀 | 已完成 | `scripts/README.md` 已记录质量门禁、审计快照、M12 重置使用约束 |
| M12-T07 内测账号确认 | 已完成 | `admin`、`m12_alice`、`m12_bob` 登录接口验证通过 |
| M12-T08 内测准入清单 | 已完成 | 本文档和 M12 测试用例文档形成准入依据 |

## 3. 内测账号

| 用户名 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| `admin` | `admin123456` | 管理员 | 管理、授权、创建基础数据 |
| `m12_alice` | `member123456` | 成员 | 普通成员验收 |
| `m12_bob` | `member123456` | 成员 | 多用户协作验收 |

## 4. 自动验证

已执行：

```powershell
pnpm verify
```

结果：

- 后端测试：通过，28 个测试全部通过。
- 前端 lint：通过。
- 前端 build：通过。
- 前端 chunk budget：通过。
- 前端路由懒加载检查：通过。
- Mockito javaagent 配置检查：通过。
- 敏感信息扫描：通过。
- Flyway migration order：通过。
- 生成物扫描：通过。
- 实现标记库存扫描：通过。

质量门禁报告：

- `.local-reports/quality-gate-20260614-211005.md`

## 5. 数据基线

已执行 M12 重置脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-m12-test-data.ps1
```

脚本先生成备份：

- `.local-reports/pre-m12-cleanup-20260614-211051.sql`

随后为清理登录核验产生的 session/device 记录，执行了无备份重置：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset-m12-test-data.ps1 -NoBackup
```

最终账号查询结果只包含：

- `admin`
- `m12_alice`
- `m12_bob`

## 6. 人工验收入口

正式手工验收从以下入口开始：

1. 启动本地依赖、后端、前端。
2. 打开 `http://127.0.0.1:5173/login`。
3. 按 `docs/m12-user-acceptance-test-cases.md` 执行 T01 到 T08。
4. 发现问题时记录账号、页面、步骤、期望、实际结果和截图。

## 7. 剩余风险

- 当前 M12 自动验证以接口和构建为主，复杂 UI 体验仍需要手工验收。
- 文档模块目前仍是 Markdown 整文保存，不是实时协同编辑。
- 表格和文档的 Lark 形态增强应放到 M13 之后的平台化路线中推进。
- 若后续新增数据表，必须同步检查 `scripts/reset-m12-test-data.ps1` 的清理范围。
