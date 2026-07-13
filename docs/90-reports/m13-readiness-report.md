# M13 平台对象模型执行记录

执行日期：2026-06-14

## 1. 范围

本轮按 AI 工作循环推进 M13-T01 到 M13-T08，目标是补强平台对象模型，让核心对象统一具备引用、预览、跳转、权限状态和前端卡片展示能力。

## 2. 执行结果

| 任务 | 状态 | 证据 |
| --- | --- | --- |
| M13-T01 盘点现有 `object_links` 和 resolver | 已完成 | 已确认 issue、document、base、base_table、base_record、approval 已有 resolver，message 缺失 |
| M13-T02 定义统一对象摘要结构 | 已完成 | `PlatformObjectSummary` 继续作为统一摘要；新增 `PlatformObjectTypeRule` |
| M13-T03 补齐核心对象 resolver | 已完成 | 新增 `MessagePlatformObjectResolver` |
| M13-T04 统一内部链接解析规则 | 已完成 | 支持完整 URL query、`/im?...messageId=...` 和 `colla://message/{id}` |
| M13-T05 统一对象卡片组件 | 已完成 | `InternalLinkCard`、`ObjectSummaryCard` 统一类型中文和访问状态 |
| M13-T06 对象生命周期状态统一 | 已完成 | 保持 `available/forbidden/deleted/not_found/invalid`，消息撤回映射为 `deleted` |
| M13-T07 增加集成测试 | 已完成 | `PlatformObjectControllerIntegrationTests` 覆盖消息对象类型、摘要、链接解析 |
| M13-T08 输出对象模型开发规范 | 已完成 | `docs/m13-platform-object-model.md` |

## 3. 本轮新增能力

- 新增 `message` 平台对象类型迁移：`V019__add_message_object_type.sql`。
- 发送 IM 消息时登记 `object_links`：
  - Web path：`/im?conversationId={conversationId}&messageId={messageId}`
  - Deep link：`colla://message/{messageId}`
- 新增消息对象摘要 resolver：
  - 校验用户是否仍是会话成员。
  - 撤回消息返回 `deleted`。
  - 可访问消息返回标题、副标题、状态、路径和 metadata。
- 新增对象类型列表接口：
  - `GET /api/platform/object-types`
- 前端对象卡片统一显示中文对象类型：
  - 事项、文档、表格空间、数据表、表格记录、消息、审批、文件。

## 4. 验证

已执行目标验证：

```powershell
mvn '-Dtest=PlatformObjectControllerIntegrationTests,ImControllerIntegrationTests' test
pnpm web:lint
pnpm web:build
```

结果：

- `PlatformObjectControllerIntegrationTests`：3 个测试通过。
- `ImControllerIntegrationTests`：5 个测试通过。
- 前端 lint：通过。
- 前端 build：通过。

## 5. 剩余风险

- `file` 当前主要依赖 object link fallback，还没有专用 file resolver；下载权限已有独立校验。
- 搜索仍使用模块 SQL 汇总，不是事件驱动索引；该项进入 M17。
- 权限决策仍分散在各模块 resolver 内；该项进入 M14。
- 消息对象定位前端已有 URL 参数，但消息高亮/滚动定位仍可在后续 IM 体验迭代中增强。
