# 项目空间研发预置类型运行手册

## 1. 适用范围

本手册用于 `PROJECT-PLATFORM-S03-M4` 的六类研发预置工作项类型：

| catalogVersion | typeKey | 默认名称 | 默认排序 |
| --- | --- | --- | --- |
| `development-v1` | `project` | 项目 | 100 |
| `development-v1` | `requirement` | 需求 | 200 |
| `development-v1` | `task` | 任务 | 300 |
| `development-v1` | `bug` | 缺陷 | 400 |
| `development-v1` | `iteration` | 迭代 | 500 |
| `development-v1` | `release` | 版本 | 600 |

预置只包含稳定 key、名称、图标、说明和排序。动态字段、页面布局、流程、角色图、工作项实例和 legacy 数据切流不属于 S03。

## 2. 初始化和补齐

- 常规新空间与 legacy 迁移创建空间都在创建空间的同一业务事务内安装完整目录；任一类型初始化失败会回滚整个空间创建。
- 应用启动后扫描 active 既有空间，逐空间加行锁并执行补齐。一个空间失败不会阻断其他空间或应用启动。
- 已存在的系统 key 保留当前启停状态、顺序和聚合版本，不被目录默认值覆盖。
- 自定义类型占用预置 key 时，该空间报告 `PRESET_KEY_CONFLICT` 和具体 key，不把自定义类型静默改成系统类型。
- 同一空间的重复或并发补齐最终只有六个系统定义和六个首发骨架版本；没有新增内容时不写重复审计或事件。

启动补齐默认开启。仅在故障隔离时临时设置：

```properties
colla.project.work-item-types.preset-backfill-enabled=false
```

恢复配置并重启即可重试非冲突失败。冲突必须先由数据治理人员确认，不得直接改 key、删定义或覆盖系统标记。

## 3. 保护和可用操作

- 系统类型允许：停用、恢复、排序、复制。
- 系统类型禁止：改 key、改系统标记、修改展示定义、retire、物理删除、替换已发布骨架版本。
- 复制系统类型会创建新的 workspace 自定义类型，必须提供新的 `typeKey`。
- 服务端 `availableActions` 是 UI 动作的唯一来源；企业治理接口只提供计数，不提供空间配置写入口。
- 数据库触发器继续拒绝绕过 API 的覆盖、retire 和删除。

legacy 迁移回滚是唯一内部清理例外：仓储在同一回滚事务内设置 transaction-local 清理标志，先删除该迁移空间的类型版本和定义，再删除空间。普通 SQL 会话和业务请求无法使用该路径。

## 4. 审计与排查

首次成功补齐写入：

- audit action：`work_item_type.presets_reconciled`
- domain event：`work_item_type.presets_reconciled`
- metadata：`source=preset_reconciliation`、`catalogVersion`、`installedKeys`、`installedCount`

启停、恢复和排序继续使用类型配置命令的 request id、审计和 outbox 合同。重放相同命令不产生重复事件；legacy 空间回滚后合法重迁属于新的类型生命周期，会生成新的补齐事件。

启动日志出现 `PRESET_KEY_CONFLICT` 时记录 spaceId 和 conflictKeys。可用以下只读 SQL 核对，不要直接修数据：

```sql
select space_id, type_key, status, is_system, sort_order, aggregate_version
from project_work_item_types
where space_id = :space_id
order by sort_order, type_key;
```

```sql
select action, target_id, metadata, created_at
from audit_logs
where target_id = :space_id
  and action = 'work_item_type.presets_reconciled'
order by created_at;
```

## 5. 兼容边界

- 补齐不写 `projects`、`issues`、`project_members`、`conversations` 或 `conversation_members`。
- S03 不创建 `project_work_items`，不新增第二套 Project Controller、resolver 或权限引擎。
- `/projects`、`/issues` 和 legacy deep link 继续使用既有运行时；预置 `project`/`bug` 只是空间配置定义，不代表实例切流或双写。
- 空间配置页展示来源和目录版本；用户执行侧只接收 active 类型的 key、名称、图标和排序。
