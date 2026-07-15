---
title: KB-PRODUCT-M5 Execution Report
status: completed
milestone: KB-PRODUCT-M5
updated_at: 2026-07-15
---

# KB-PRODUCT-M5 Execution Report

## Scope

- KB-PRODUCT-M5-T01 到 KB-PRODUCT-M5-T11
- 目标：在单协作节点内建立以 Yjs/Hocuspocus 为合并核心、Spring Boot 与 PostgreSQL 为业务和数据 owner 的块级实时协同主路径。
- 边界：Redis 跨节点广播、离线队列和双节点故障切换属于 KB-PRODUCT-M6，不在本里程碑内冒充完成。

## Verification Contract

| Task | Required verification level | Browser evidence kind | Environment | Mock browser allowed | Required real flow |
| --- | --- | --- | --- | --- | --- |
| T01-T03 | protocol + focused integration | automated | local single node | no | CRDT 合并、协议版本、标题与正文同文档 |
| T04 | real browser | real | two isolated login contexts | no | presence、选择区、远端光标 |
| T05-T06 | protocol + backend integration | automated | local single node | no | 校验、去重、快照恢复、冲突矩阵 |
| T07 | backend + real browser | real | two isolated login contexts | no | edit 降级到 view 后立即只读 |
| T08 | backend + real browser | real | local single node | no | 快照落库、版本不按键增长、审计节流 |
| T09 | real browser | real | two isolated login contexts | no | 并发输入、格式、移动和删除 |
| T10 | protocol | automated | deterministic Yjs documents | no | 乱序、重复、延迟和短断恢复 |
| T11 | stage gate + build + browser | real | local single node | no | 单节点准入判定 |

## Acceptance Evidence

| Task | Acceptance criterion | Implementation evidence | Automated/browser evidence | Status |
| --- | --- | --- | --- | --- |
| KB-PRODUCT-M5-T01 | 不依赖整篇最后写覆盖 | Yjs 更新作为事实合并单元，Hocuspocus 承担房间同步 | Node 并发与重复更新收敛测试 | Completed |
| KB-PRODUCT-M5-T02 | 协议有版本、权限、幂等和错误边界 | `colla-yjs-v1`、ticket 鉴权、update hash、sequence、awareness、stateless permission message | 协议校验与边界测试 7/7 | Completed |
| KB-PRODUCT-M5-T03 | 标题、正文块和属性位于同一协作文档 | `title` Y.Text、`default` Y.XmlFragment、稳定 `blockId` | 双端标题和正文同时同步 | Completed |
| KB-PRODUCT-M5-T04 | presence、选择区和光标可解释 | Awareness 用户身份净化，Tiptap CollaborationCaret 映射相对位置 | 两个隔离浏览器可见远端选择区并跟随块变更 | Completed |
| KB-PRODUCT-M5-T05 | 更新受校验并可在重启后恢复 | 大小/schema/权限校验，update log、state vector、snapshot 和 canonical projection 持久化 | snapshot + pending updates 重建测试；越权/超限后端测试 | Completed |
| KB-PRODUCT-M5-T06 | 同块、不同块、结构和删除冲突不静默丢失 | Yjs 类型承载文本、块属性和表格单元格；块操作沿协同事务传播 | 同块/异块、删除/格式、表格单元格、乱序重复测试通过 | Completed |
| KB-PRODUCT-M5-T07 | 权限降级、停用或会话过期立即只读/退出 | 2 秒 token sync 重新鉴权，服务端 readOnly，stateless 权限通知，客户端断开即只读 | edit -> view 真实浏览器用例；失效会话后端测试 | Completed |
| KB-PRODUCT-M5-T08 | 保存、版本和审计无按键噪音 | debounce snapshot、规范块投影、1 分钟审计 checkpoint；协作更新不自动追加业务版本 | 双浏览器断言版本数不增长；审计仅记录一次 checkpoint | Completed |
| KB-PRODUCT-M5-T09 | 两个真实用户覆盖协同主路径 | Playwright 创建两个独立 BrowserContext 与独立账号 | 输入、光标、加粗、插入、移动、删除、落库和降权 1/1 通过 | Completed |
| KB-PRODUCT-M5-T10 | 乱序、重复、延迟和短断后最终一致 | update hash 去重、sequence 水位、snapshot 后增量重放 | 协议测试 7/7，未出现状态分叉 | Completed |
| KB-PRODUCT-M5-T11 | 单节点协同可准入 M6 | 本报告、部署配置、健康端点和反向代理契约完整 | stage gate、前端 production build、compose config、真实浏览器均通过 | Completed |

## Code Changes

- Backend：新增 collaboration ticket、内部鉴权/load/update/snapshot API、二进制更新与规范块投影、权限刷新和审计 checkpoint；修复同一主体重复变更权限时事件键冲突。
- Collaboration：新增 Hocuspocus v4 单节点 sidecar，接入 Yjs、Tiptap transformer、更新校验、快照恢复、awareness 净化与权限状态通知。
- Frontend：块编辑器接入 Yjs/Hocuspocus provider、共享标题、远端光标、在线成员、同步状态和即时只读。
- Database：V052 建立 collaboration tickets、updates、binary snapshot/state vector、序列和索引契约。
- Deployment：补充 collaboration 容器、健康检查、环境变量与 WebSocket 反向代理。

## Validation

- Collaboration protocol：`pnpm --dir collaboration test`，7/7 通过。
- Backend focused integration：`KnowledgeCollaborationGatewayServiceTests` 5/5；`KnowledgeContentControllerIntegrationTests` 通过。
- Frontend lint：通过。
- Frontend production build：通过。
- Deployment config：`docker compose ... config --quiet` 通过。
- Browser smoke：两个隔离账号与浏览器上下文，1/1 通过；并发丢失 0，未解释冲突 0。

## Admission Decision

- KB-PRODUCT-M5：Go。
- 可以进入 KB-PRODUCT-M6，下一阶段必须验证 Redis 跨节点广播、断线补偿、离线队列、房间回收和双节点故障注入。
- M5 不声明多节点高可用；当前准入结论仅适用于单协作节点。
