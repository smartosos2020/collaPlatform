# PROJECT-PLATFORM-S21-M5 简洁 Shell 实现审计

## 1. 审计范围

本审计把 M4 已冻结的信息架构合同转换成 M5 可执行和可自动验证的实现检查表，范围为：

- 五个一级入口及唯一导航注册表；
- route-context、旧配置深链和 query 保留；
- 项目管理聚合与设置/高级配置分组；
- 用户、工作区、空间三维隔离的模式偏好；
- owner、admin、member、guest、outsider、enterprise governance 六身份；
- active、disabled、archived、offline、focus、realtime 和多标签恢复；
- 1440、1366、820 三视口及键盘操作。

本文件不改变 M4 合同，不创建第二权限权威，也不把浏览器显隐视为授权。

## 2. 实现不变量

1. 一级导航的顺序、路径、模式、capability 和空间状态元数据只有一个注册表。
2. 当前导航上下文只能由一个 route-context resolver 解析；兼容深链不经往返重定向。
3. 五入口是否挂载由服务端 `availableActions` 和空间状态决定，CSS 隐藏不算权限控制。
4. 项目管理页只组合计划、风险、交付、资源和指标 owner 的当前公共响应，不保存第二份业务事实。
5. 设置中心只组合既有配置组件；旧 `/types/**` 路径和 owner API 保持有效。
6. mode preference 只保存 `simple | advanced`、schema/version 和更新时间，不保存角色、capability 或授权快照。
7. preference 读取、迁移或离线失败时使用 `simple`，不得从本地陈旧值恢复隐藏入口。
8. realtime、online、focus 和跨标签只使 REST query 失效；收到事件本身不产生成功状态。
9. 未授权入口既不渲染也不预取。直接 URL 仍由 API 的当前成员和 capability 校准失败关闭。
10. query、未提交草稿、浏览器前进后退和兼容路径的返回上下文不能在模式切换时被静默清空。

## 3. 六身份预期

| 身份 | 默认落点 | 可见一级入口 | 模式切换 | 直达受限路径 |
| --- | --- | --- | --- | --- |
| owner | 概览 | 五入口 | 可见 | 当前 owner API 决定 |
| admin | 概览 | 五入口 | 可见 | 不能获得 owner-only 动作 |
| member | 工作项 | 概览、工作项、项目管理 | 不可见 | 成员/设置不可挂载或预取 |
| guest | 概览 | 概览、工作项 | 不可见 | mutation 失败关闭 |
| outsider | 无内容 Shell | 无 | 不可见 | 统一 404/最小错误外形 |
| enterprise governance 非成员 | 企业治理 Shell | 无项目空间内容入口 | 不可见 | 不能以治理身份旁路成员边界 |

服务端响应若尚未提供 M4 定义的 `view_overview`、`view_work_items`、
`view_project_management`、`view_members`、`view_settings`，M5 必须在服务端 DTO
投影这些 capability；浏览器不得仅由 `currentUserRole` 合成。

## 4. 深链与上下文矩阵

| 输入 | 预期上下文 | 必须保留 |
| --- | --- | --- |
| `/project-spaces/:spaceId` | 概览 | 任意非授权 query 不得改变 capability |
| `/work-items?typeId&create&savedViewId&panel` | 工作项 | 全部受支持 query |
| `/management?panel` | 项目管理 | 当前聚合 panel |
| `/members` | 成员 | 返回来源 |
| `/settings?group` | 设置/高级配置 | 当前分组 |
| `/types/:typeId` | 设置/高级配置/工作模型 | `typeId` 和原 query |
| `/types/:typeId/fields/:fieldId` | 设置/高级配置/工作模型 | `typeId`、`fieldId`、原 query |
| `/types/:typeId/layouts` | 设置/高级配置/工作模型 | `typeId`、原 query |
| `/types/:typeId/sample` | 设置/高级配置/工作模型 | `typeId`、原 query |

自动化必须检查 URL 不发生循环、source/savedViewId/panel 不丢失，并确认旧深链
仍渲染原 owner 组件而不是复制页面。

## 5. Mode preference 契约检查

API 固定为：

- `GET /api/project-spaces/{spaceId}/experience-preference`
- `PUT /api/project-spaces/{spaceId}/experience-preference`
- `DELETE /api/project-spaces/{spaceId}/experience-preference?expectedVersion=...`

最小响应应具备：

- `schemaVersion`
- `mode`
- `version`
- `updatedAt`
- `availableModes`

保存命令必须具备：

- 可追踪的 request ID；
- `expectedVersion`；
- `mode` 固定枚举；
- workspace/space/user 从认证和 path 取得；
- CAS 冲突响应；
- 不返回或保存 capability/role。

自动化需覆盖：

- owner 保存 advanced 后刷新仍为 advanced；
- 同一用户另一个空间仍为 simple；
- admin/owner 各自偏好隔离；
- member/guest 不能通过调用 preference mutation 获得高级入口；
- 两标签以相同 expected version 写入时，一次成功、一次 409；
- 离线或读取失败时回到 simple，恢复网络后以服务端 REST 为准。

## 6. 浏览器验证约束

- 测试必须使用隔离创建的空间与身份，并在 finally 归档空间、下线身份。
- API 断言先验证服务端 403/404，再验证 UI 没有挂载受限内容。
- 网络观察应证明 member/guest 不请求成员、设置或高级配置私有接口。
- 模式切换不得清除当前 URL query。
- 三视口均不得出现 document 级横向溢出。
- 一级导航支持键盘焦点；当前入口具备 `aria-current` 或等价语义。
- offline 时 mutation disabled 或明确失败，不得出现陈旧成功提示。
- 多标签冲突后必须 REST 重读，不允许客户端覆盖服务端版本。

## 7. 自动化规格

`web/e2e/project-platform-s21-m5.spec.ts` 负责以下真实 isolated flow：

1. 六身份 API 边界与五入口集合；
2. owner 模式偏好保存、跨用户/跨空间隔离和版本冲突；
3. 旧类型/字段/布局深链及 query 保留；
4. 项目管理和设置高级分组定位；
5. disabled/archived 只读落点；
6. online/focus、offline 和 CAS 冲突后的 REST 校准；
7. 1440/1366/820、键盘、长名称和无横向溢出。

测试规格假定 M5 核心实现会提供稳定的 preference API 和下列语义定位点；
若最终名称不同，应只对齐定位点，不弱化断言：

- `project-space-primary-navigation`
- `project-space-mode-switch`
- `project-space-management`
- `project-space-advanced-settings`

disabled/archived 的只读状态使用带有“空间已停用”或“空间已归档”文本的
语义 `alert` 定位，不额外制造测试专用 DOM。

## 8. 准入判断

只有核心实现、后端 preference 集成测试、Web build/lint、上述 isolated E2E 和
真实浏览器截图一致通过，M5 才可写 Go。测试规格本身不能替代运行证据，也不能据此
提前把 M5 Task 标为 Done。
