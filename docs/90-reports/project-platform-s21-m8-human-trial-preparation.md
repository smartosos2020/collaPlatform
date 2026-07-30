# PROJECT-PLATFORM-S21-M8 真人试用准备交接包

## 1. 文档状态与不可越界声明

- 文档状态：`Preparation / Awaiting Human Freeze`。
- 适用范围：为 `PROJECT-PLATFORM-S21-M8-T01..T12` 提供真人主持前的交接模板。
- 当前事实：本文件不代表 M8 已启动；尚未登记参与者、取得 consent、创建试用运行、冻结任务脚本、执行真人任务或形成任何人工结论。
- 进入前置：只有
  [`project-platform-s21-m7-execution-report.md`](./project-platform-s21-m7-execution-report.md)
  最终明确记录 M8 Engineering Go，且其 fresh 工程证据、P0/P1 清零与环境可恢复条件均可追溯后，
  真人主持人才可开启 M8。
- 权威边界：若 M7 报告仍为草稿、证据未写入、结论为 Reopen 或前置已失效，M8 保持
  Pending。本文件不得被解释为补签 Engineering Go。
- 人工边界：AI、维护者代演、模拟账号和自动化浏览器可以协助准备或复验环境，但不得充当参与者、
  提供 consent、代填原始反馈或签署人工验收结论。
- 历史边界：revision 49 的
  [`project-platform-s21-m4-human-trial-preparation.md`](./project-platform-s21-m4-human-trial-preparation.md)
  仅作素材；M8 主持人必须针对改造后的界面重新冻结协议和任务脚本。

## 2. 启动门禁

真人主持人在创建 `trialRunId` 前逐项填写；任一项不是“已确认”即不得开始。

| 门禁 | 必须提供的证据 | 状态 | 责任人/时间 |
| --- | --- | --- | --- |
| M7 Engineering Go | M7 最终报告中的明确结论及 fresh evidence index | 待确认 | `[待真人填写]` |
| P0/P1 为零 | 当前开放 finding 查询或受控缺陷清单 | 待确认 | `[待真人填写]` |
| 隔离环境可恢复 | 基线标识、备份/快照、恢复演练和校验结果 | 待确认 | `[待真人填写]` |
| 试用版本固定 | commit、构建版本、配置版本、rollout policy version | 待确认 | `[待真人填写]` |
| 五名真实参与者可用 | 研发、市场、HR、交付、管理者各一名且为不同真人 | 待确认 | `[待真人填写]` |
| 主持与安全角色就绪 | 主持人、数据保管人、安全停止联系人、环境操作人 | 待确认 | `[待真人填写]` |
| consent 材料已审核 | 协议版本、保存位置、撤回方式、保留期 | 待确认 | `[待真人填写]` |
| 合成数据已审核 | 四场景 manifest、无真实隐私声明、校验和 | 待确认 | `[待真人填写]` |
| 外部副作用已隔离 | 邮件、通知、Webhook、文件扫描和外部集成使用沙箱或 sink | 待确认 | `[待真人填写]` |
| 清理/撤权可执行 | 账号、membership、session、数据、对象存储与缓存恢复清单 | 待确认 | `[待真人填写]` |

门禁失败后的唯一动作是等待补齐，或按 M7 结论回到 Reopen；不得通过降低角色隔离、借用生产数据、
共享账号或把自动化结果改写为人工结果来继续。

## 3. 真人参与者与支持角色

### 3.1 必需参与者槽位

实际姓名和联系方式只进入受控参与者名册，不写入代码仓库。仓库证据只使用随机生成且不能反推身份的
participant key。五个槽位必须由五名不同的真人承担。

| 槽位 | 真人资格与主要场景 | 初始最小角色 | 明确禁止 | Participant key | Consent ref | 登记状态 |
| --- | --- | --- | --- | --- | --- | --- |
| `P-DEV-01` | 当前从事研发需求、任务或缺陷协作的真实参与者 | 仅研发试用空间 `member` | workspace admin、其他场景访问、共享会话 | `[待真人登记]` | `[待真人登记]` | 未登记 |
| `P-MKT-01` | 当前从事活动、内容、素材或渠道协作的真实参与者 | 仅市场试用空间 `member` | 真实渠道凭据、其他场景访问、共享会话 | `[待真人登记]` | `[待真人登记]` | 未登记 |
| `P-HR-01` | 当前从事招聘或入职协作且理解敏感信息边界的真实参与者 | 仅 HR 试用空间 `member` | 真实候选人/员工数据、跨空间访问、共享会话 | `[待真人登记]` | `[待真人登记]` | 未登记 |
| `P-DEL-01` | 当前从事交付、风险、交付物或验收协作的真实参与者 | 仅交付试用空间 `member` | 真实客户文件/合同、其他场景访问、共享会话 | `[待真人登记]` | `[待真人登记]` | 未登记 |
| `P-MGR-01` | 当前真实承担团队管理或项目空间管理职责的参与者 | 四个试用空间 `owner`；无平台/企业管理员权限 | 生产运维凭据、无关 workspace、以 enterprise-admin 绕过 membership | `[待真人登记]` | `[待真人登记]` | 未登记 |

若角色脚本需要验证 `guest`、非成员或撤权状态，应由环境操作人准备额外的隔离测试身份；
不得临时扩大五位参与者的长期权限。任何角色变更必须记录变更前后、原因、批准人和撤销时间。

### 3.2 支持角色

| 支持角色 | 允许动作 | 不允许动作 | 登记 |
| --- | --- | --- | --- |
| 真人主持人 | 说明协议、读出冻结任务、记录观察、接受退出、触发停止条件 | 替参与者点击、暗示路径、改写原话、补做失败任务 | `[待指定]` |
| 数据保管人 | 管理名册、consent、原始记录、访问控制、保留与销毁 | 把真实身份或无关隐私写入仓库 | `[待指定]` |
| 安全停止联系人 | 判断 P0/P1、隔离账号/环境、组织升级 | 为提高完成率压低严重度或允许继续暴露 | `[待指定]` |
| 环境操作人 | 建立/恢复隔离环境、账号、基线、外部 sink 和最终撤权 | 代演真人任务或查看无必要的原始反馈 | `[待指定]` |

支持角色可以由同一名合格人员兼任，但不得同时作为其主持场景的参与者，也不得签署参与者原始反馈。

## 4. Consent 与隐私合同

### 4.1 开始前必须逐人说明

1. 试用目的为验证产品任务可用性，不用于个人绩效、能力排名、招聘、晋升或纪律决定。
2. 记录范围仅包括任务时间区间、完成/失败、帮助请求、操作路径、错误外形、参与者主动反馈和必要截图引用。
3. 默认不录制摄像头、麦克风或完整桌面；如确有必要，必须另行逐项同意并限定窗口、时长和保存位置。
4. 不采集真实候选人、员工、客户、渠道、合同、凭据、文件正文或其他无关隐私。
5. 参与者可跳过问题、暂停或随时退出，不需说明理由且不受不利影响。
6. 说明原始记录的访问者、加密位置、保留期限、匿名化方式、撤回窗口和销毁联系渠道。
7. 说明严重安全/隐私问题会立即停止任务，主持人可能保留最小化的复现证据。
8. 说明 AI/自动化可整理结构但不能代写参与者原话或代签结论。

### 4.2 Consent 登记模板

```text
consentVersion: [待真人冻结]
participantKey: [待登记]
scenario: [development | marketing | human-resources | delivery | manager]
purposeExplained: [yes/no]
recordingScopeExplained: [yes/no]
privacyBoundaryExplained: [yes/no]
retentionAndAccessExplained: [yes/no]
withdrawalRightExplained: [yes/no]
optionalAudioVideoConsent: [not-requested | declined | explicitly-approved]
participantDecision: [consent | decline]
consentedAt: [待登记，含时区]
capturedBy: [真人主持人 key]
consentEvidenceRef: [受控存储引用，不放真实签名/联系方式]
withdrawnAt: [如发生]
```

`participantDecision` 不是 `consent`、证据不可访问、协议版本不一致或参与者已撤回时，禁止收集或继续使用
该参与者数据。

## 5. 隔离环境与合成数据基线

### 5.1 环境要求

- 使用专用 trial workspace，不连接生产 workspace、生产身份目录或生产对象存储前缀。
- 固定并记录应用 commit、数据库 schema、Web 构建、rollout policy、浏览器版本、时区和视口。
- 数据库、对象存储、Redis 可恢复状态和配置均有本次运行前基线；记录基线引用与 SHA-256 校验值。
- 邮件、Webhook、通知、附件扫描和第三方集成指向隔离 sink/sandbox；不允许真实外发。
- 每人使用独立账号、独立浏览器 profile 和独立会话；禁止共享密码、token、浏览器 profile 或设备会话。
- 默认简洁模式。仅管理者冻结脚本明确要求时短时切换高级模式，且切换不改变服务端权限。

### 5.2 四类合成场景

| 场景键 | 必须使用的合成内容 | 禁止内容 | 基线/校验 |
| --- | --- | --- | --- |
| `development` | 虚构产品、需求、任务、缺陷、版本、迭代和代码引用 | 真实仓库秘密、客户数据、漏洞细节、token | `[待环境操作人填写]` |
| `marketing` | 虚构活动、内容、素材、渠道、预算区间和复盘数据 | 真实渠道账号、投放凭据、客户名单、未公开素材 | `[待环境操作人填写]` |
| `human-resources` | 明确标注虚构的职位、候选人、面试和入职资料 | 真实姓名、简历、联系方式、薪酬、健康或身份信息 | `[待环境操作人填写]` |
| `delivery` | 虚构客户、项目、任务、风险、交付物、评审和验收 | 真实合同、客户文件、生产地址、密钥或个人信息 | `[待环境操作人填写]` |

所有附件使用无恶意、无隐私、可公开重建的合成文件，并记录文件清单和校验和。合成 fixture 必须可重复创建，
但重置动作只能由环境操作人在确认目标隔离环境后执行。

### 5.3 最小权限预检

- 四位业务参与者只能看到自己的场景空间；直接访问其他空间返回与当前权限合同一致的最小披露结果。
- 管理者仅在四个 trial space 内拥有 `owner`，不获得平台管理员、企业管理员或生产运维权限。
- 非成员、guest、member、owner 的预期入口和 mutation 边界由主持人冻结前逐项核对。
- rollout、模式偏好、onboarding、客户端缓存和遥测都不是授权事实；任何读取或写入仍由服务端逐次校准。
- 高级配置、成员变更、模板安装、交接和收权只在管理者脚本要求的时间窗开放，并保留审计/回执。

## 6. Trial run manifest

以下 manifest 由真人主持人在 M8-T01/T02 完成前填写并冻结；本模板没有预设通过值。

```text
trialRunId: [待生成]
protocolVersion: [待冻结]
taskScriptVersion: [待冻结]
taskScriptDigest: [待冻结]
environmentId: [待登记]
baselineRef: [待登记]
baselineDigest: [待登记]
applicationCommit: [待登记]
webBuildVersion: [待登记]
databaseSchemaVersion: [待登记]
rolloutPolicyVersion: [待登记]
startedAt: [不得预填]
endedAt: [不得预填]
facilitatorKey: [待指定]
dataCustodianKey: [待指定]
safetyContactKey: [待指定]
participantKeys:
  development: [待登记]
  marketing: [待登记]
  humanResources: [待登记]
  delivery: [待登记]
  manager: [待登记]
entryDecision: [awaiting-review | go | no-go]
entryDecisionBy: [真人批准人，待登记]
entryDecisionAt: [待登记]
```

## 7. 任务脚本占位

本节只定义主持人必须冻结的槽位和路线规定的业务结果，不构成已冻结任务脚本。真人主持人须在 M8-T02
针对届时的新界面，以中性、不诱导的语言补齐步骤，并生成版本与 digest；补齐前不得执行。

### 7.1 通用脚本模板

```text
scriptId: [待填写]
scenario: [待填写]
participantRole: [待填写]
neutralTaskPrompt: [待真人主持冻结；不得暴露菜单名或标准答案]
startingState: [待填写]
allowedSyntheticInputs: [待填写]
requiredOutcome: [待填写]
completionEvidence: [待填写]
permissionBoundaryToObserve: [待填写]
simpleModeExpectation: [待填写]
advancedModeCondition: [none | 待管理者脚本明确]
deepLinkOrRecoveryCondition: [待填写]
helpPolicy: [待填写]
timebox: [待填写；仅用于节奏，不作个人绩效]
stopConditionExtension: [待填写]
cleanupAfterTask: [待填写]
```

### 7.2 五类脚本槽位

| Script slot | 路线规定的结果范围 | 中性任务提示 | 完成证据 | 状态 |
| --- | --- | --- | --- | --- |
| `SCRIPT-DEV` | 需求、任务、缺陷、版本、迭代闭环 | `[待真人主持冻结]` | `[待冻结]` | 未冻结/未执行 |
| `SCRIPT-MKT` | 活动、内容、素材、渠道、投放、复盘闭环 | `[待真人主持冻结]` | `[待冻结]` | 未冻结/未执行 |
| `SCRIPT-HR` | 招聘计划、职位、合成候选人、面试、入职闭环及敏感字段最小披露 | `[待真人主持冻结]` | `[待冻结]` | 未冻结/未执行 |
| `SCRIPT-DEL` | 项目、任务、风险、交付物、评审、验收闭环 | `[待真人主持冻结]` | `[待冻结]` | 未冻结/未执行 |
| `SCRIPT-MGR` | 模板安装、成员配置、必要高级设置、交接、收权、深链、失败恢复 | `[待真人主持冻结]` | `[待冻结]` | 未冻结/未执行 |

主持人不得用“点击某菜单”“这里应该看到成功”等措辞引导答案。若参与者请求帮助，应先原样记录请求、
时间点和此前路径，再按冻结 help policy 提供同等级提示；主持人不得静默替参与者完成操作。

## 8. P0/P1 立即停止与升级

### 8.1 严重度和停止条件

| 等级 | 触发示例 | 立即动作 |
| --- | --- | --- |
| P0 | 跨 workspace/space 越权、真实隐私或凭据泄漏、不可恢复数据破坏、生产外发、认证全面失效 | 立即停止全部试用，隔离账号和环境，联系安全停止联系人 |
| P1 | 核心场景无可用绕行、角色收权后仍可访问、隐藏对象/成员/标题泄漏、旧写入口恢复、恢复基线失效 | 立即停止受影响场景；影响边界不明时停止全部试用 |

以下情况即使尚未定级，也先按停止处理：

- 任一参与者撤回 consent、要求退出或无法确认记录范围；
- 账号/会话被共享，真实数据或运维凭据进入环境或证据；
- 试用 commit、脚本 digest、权限或基线与 manifest 不一致；
- rollout/kill、缓存或离线恢复导致陈旧成功、权限旁路或业务事实丢失；
- 原始记录无法与实际 participant、时间或任务对应；
- 主持人需要代操作才能继续，且冻结 help policy 未覆盖。

### 8.2 停止后的固定顺序

1. 停止相关操作与录制，不要求参与者继续复现。
2. 终止受影响会话并撤销临时权限；必要时隔离整个 trial workspace。
3. 保存最小必要的错误外形、时间、角色、路径和受控对象引用，不复制敏感正文。
4. 由安全停止联系人确认 P0/P1、影响范围、owner 和后续处置；不得由主持人单独降级。
5. 把原始记录与 finding 分开保存，不把未完成任务计为“成功”或通过扩大样本稀释。
6. M8 不得签署通过；按治理决定等待修复/真人复验，或回到相应工程阶段 Reopen。

## 9. 原始记录模板

每位参与者一份原始记录。原话使用逐字摘录并与主持人解释分栏；不把摘要反写成原话。

```text
trialRunId: [待登记]
participantKey: [待登记]
scenario: [待登记]
applicationRole: [待登记]
consentRef: [待登记]
protocolVersion: [待登记]
taskScriptVersion: [待登记]
environmentId: [待登记]
sessionStartedAt: [待真人记录]
sessionEndedAt: [待真人记录]

tasks:
  - scriptId: [待登记]
    startedAt: [待记录]
    endedAt: [待记录]
    result: [independent-complete | complete-with-help | failed | stopped | withdrawn]
    helpRequests:
      - at: [待记录]
        participantWords: [逐字记录]
        facilitatorResponse: [逐字记录]
    observedPath: [只记录产品入口/动作，不复制正文或身份]
    modeTransitions: [simple/advanced 及原因]
    confusionPoints: [事实观察]
    errorShape: [脱敏错误码/外形]
    participantQuotes: [逐字记录；不得由 AI 生成]
    participantSuggestion: [逐字记录]
    opaqueEvidenceRefs: [受控截图/日志/对象引用]
    facilitatorNotes: [与原话分离]
    stopConditionTriggered: [none | P0 | P1 | consent | environment | other]

participantClosingFeedback:
  easiestPart: [原话]
  hardestPart: [原话]
  expectedButMissing: [原话]
  modeUnderstanding: [原话]
  recoveryUnderstanding: [原话]
  additionalComments: [原话]

recordCapturedBy: [真人主持人 key]
participantAccuracyCheck: [confirmed | corrections-attached | declined]
```

禁止记录个人“熟练度分数”、参与者排名、键鼠生物特征、无关窗口、真实业务正文或跨参与者比较。
完成率、时间、帮助请求和迷失点只能在 M8-T09 从原始记录派生，必须按区间和小样本限制表达，不能用于个人绩效。

## 10. Finding 登记模板

```text
findingId: [待生成]
trialRunId: [待登记]
sourceRecordRef: [受控引用]
reportedAt: [待登记]
severity: [P0 | P1 | P2 | P3 | pending-triage]
affectedScenario: [待登记]
affectedRole: [待登记]
summary: [脱敏事实]
expected: [待登记]
observed: [待登记]
minimalReproduction: [待登记；不得含隐私正文]
evidenceRefs: [待登记]
reproducibility: [confirmed | intermittent | not-yet-reproduced]
owner: [待登记]
containment: [待登记]
participantImpact: [待登记]
decision: [stop | reopen | fix-and-retest | track]
decisionBy: [真人责任人]
```

原始记录、finding、汇总报告三者分开保存并保持引用关系。AI 可协助去重候选，但真人必须核对原话、
严重度、复现、owner 和决定。

## 11. 结束、清理、撤权和环境恢复

每个 session 结束即做 A；整个 trial run 结束或停止时按 B-D 完成。全部项目需要操作人和复核人双人确认。

### A. 会话终止

- [ ] 停止屏幕/音频录制并确认没有捕获无关窗口。
- [ ] 从所有标签页、设备和浏览器 profile 登出参与者账号。
- [ ] 终止 access/refresh session，验证旧 token 不再可用。
- [ ] 关闭临时分享链接、下载链接和浏览器保存的凭据。
- [ ] 记录 session 终止时间、操作人和复核人。

### B. 权限撤销

- [ ] 移除四位业务参与者的 trial space membership。
- [ ] 移除管理者的四个 owner/admin 角色和任何临时高级设置权限。
- [ ] 禁用或删除专用试用账号；账号保留时必须锁定且无 workspace membership。
- [ ] 用直接 URL/API 复核撤权后遵循 401/403/404 最小披露合同。
- [ ] 检查邀请、审批委托、通知订阅、共享文件和跨空间引用没有残留访问。
- [ ] 记录撤权前后权限快照的受控引用，不保存 token 或密码。

### C. 数据与证据处理

- [ ] 停止并移除外部 sink/sandbox 的临时连接、Webhook、邮件目标和凭据。
- [ ] 删除未获保留批准的合成业务数据、临时附件、导出和浏览器下载。
- [ ] 保留不可变审计、批准证据和经 consent 允许的原始记录；不得为了“清理”删除审计/回执。
- [ ] 对保留证据应用访问控制、加密、保留期限和销毁日期。
- [ ] 将 participant 身份名册与产品证据分库存放；仓库仅保留 participant key。
- [ ] 对撤回 consent 的数据按协议执行删除/去标识，并记录处置结果。

### D. 环境恢复

- [ ] 由环境操作人确认目标确为隔离 trial workspace/namespace 后执行恢复。
- [ ] 从已登记基线恢复 PostgreSQL、对象存储、Redis 可重建状态和运行配置。
- [ ] 对账 schema 版本、规范业务事实、对象校验和、审计/回执保留和 rollout 配置。
- [ ] 验证无临时账号、membership、session、文件、通知、Webhook 或缓存身份残留。
- [ ] 执行恢复后的最小健康、安全和深链失败关闭检查。
- [ ] 记录恢复证据引用、结果、偏差、操作人、复核人和完成时间。

清理或恢复任一项失败都阻断 M8 checkpoint；不得只在报告中标为“后续清理”。

## 12. 人工签署占位

下列字段只能由真人在实际完成对应工作后填写，本文件当前全部保持未签署。

| 决定 | 必需签署者 | 当前状态 | 签署/证据 |
| --- | --- | --- | --- |
| 协议与任务脚本冻结 | 真人主持人、数据保管人、安全停止联系人 | 未签署 | `[待真人填写]` |
| 试用启动 Go/No-Go | M8 责任人、环境操作人 | 未签署 | `[待真人填写]` |
| 研发原始任务记录 | `P-DEV-01` 与真人主持人 | 未执行 | `[待真人填写]` |
| 市场原始任务记录 | `P-MKT-01` 与真人主持人 | 未执行 | `[待真人填写]` |
| HR 原始任务记录 | `P-HR-01` 与真人主持人 | 未执行 | `[待真人填写]` |
| 交付原始任务记录 | `P-DEL-01` 与真人主持人 | 未执行 | `[待真人填写]` |
| 管理者原始任务记录 | `P-MGR-01` 与真人主持人 | 未执行 | `[待真人填写]` |
| 清理、撤权和恢复完成 | 环境操作人、独立复核人 | 未执行 | `[待真人填写]` |
| M8 人工结论 | 四类参与者、真实管理者、M8 责任人 | 未执行/不得预签 | `[待真人填写]` |

## 13. 交接时的 M8 Task 对照

此表只说明准备包提供的入口，不改变路线中的 Pending 状态。

| M8 Task | 准备入口 | 当前边界 |
| --- | --- | --- |
| T01 | 第 2、3、5、6 节 | M7 Engineering Go 已提供；仍待真人复核其证据与其余启动门禁 |
| T02 | 第 4、7 节 | 协议/脚本仍待真人主持冻结 |
| T03 | 第 5、11 节 | 环境/数据仍待真人建立并验证 |
| T04-T08 | 第 3、7、9 节 | 五类真人任务未执行 |
| T09 | 第 9 节 | 仅有空白原始记录合同，无汇总数据 |
| T10 | 第 8、10 节 | 仅有停止和 finding 模板，无 finding 结论 |
| T11 | 第 11 节 | 清理、撤权、恢复未执行 |
| T12 | 第 12 节 | 人工结论未签署；AI/自动化不得代签 |

M7 Engineering Go 已提供；本准备包交付后的正确停点仍是等待真人主持人、五名真实参与者、
逐人 consent 与隔离环境门禁全部就绪，再由用户明确启动独立的 M8 工作循环。
