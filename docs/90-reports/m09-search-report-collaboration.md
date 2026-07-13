# M9 搜索、报表与协作增强

## 范围

M9 面向协作效率增强，不引入新的业务主模块。本轮交付：

- PostgreSQL 全文搜索 MVP，覆盖 issue、document、base record、message。
- 搜索结果在 SQL 层按项目成员、文档权限、Base 成员、会话成员过滤。
- Web 全局搜索入口与搜索结果页。
- 项目统计接口与 Web 展示，包含状态、负责人、迭代、延期。
- 文档版本行级 diff。
- 表格看板视图与日历视图。
- IM 消息编辑、撤回、表情回应、置顶。

## 搜索实现

当前使用 PostgreSQL `to_tsvector('simple', ...)` 和 `plainto_tsquery('simple', ...)`。选择 `simple` 的原因是 MVP 需要稳定支持中英文混合文本，不依赖额外数据库扩展。

新增索引位于 `V013__create_search_and_collaboration_enhancements.sql`：

- `issues`: issue key、title、description。
- `documents`: title、content。
- `base_record_values`: value_text。
- `messages`: content。

## 权限边界

搜索不是先查出结果再过滤，而是在各对象 SQL 中直接加入访问条件：

- issue: `project_members`。
- document: 创建者或 `document_permissions`。
- base record: `base_members`。
- message: `conversation_members`。

这样可以避免不可见对象参与分页和排序。

## Meilisearch / Elasticsearch 评估

M9 暂不引入外部搜索引擎。

暂缓原因：

- 当前数据规模和权限模型还处于单体 MVP 阶段，PostgreSQL 能覆盖第一阶段全局搜索。
- 外部搜索需要索引同步、重放、权限字段冗余、删除/撤权一致性处理，会明显增加运维和一致性成本。
- IM、文档、表格记录的权限变更频繁，过早引入搜索中间件容易出现搜索可见性滞后。

后续触发条件：

- 单工作区对象量达到百万级，PostgreSQL 搜索延迟稳定超过 500ms。
- 需要中文分词、拼写纠错、同义词、复杂高亮、多字段权重调优。
- 需要跨工作区大规模搜索分析，且已具备可靠事件投递和索引重建机制。

优先级建议：

1. 先优化 PostgreSQL：生成列 tsvector、GIN 索引、分页游标、对象级搜索统计。
2. 需要轻量搜索体验时优先评估 Meilisearch。
3. 需要复杂查询、审计级索引治理、海量日志/文档搜索时再评估 Elasticsearch/OpenSearch。
