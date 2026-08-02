# 项目空间概览 Design QA

## 对照对象

- source visual truth 1（我的工作）: 用户会话附件中的“我的工作”参考图。
- source visual truth 2（动态与边界）: 用户会话附件中的“动态与边界”参考图。
- implementation 1: `.local-reports/project-space-overview-qa/member-home-final.png`
- implementation 2: `.local-reports/project-space-overview-qa/activity-boundary-final.png`
- combined comparison 1: `.local-reports/project-space-overview-qa/member-home-comparison.png`
- combined comparison 2: `.local-reports/project-space-overview-qa/activity-boundary-comparison.png`

## 视口、归一化与状态

- Browser CSS viewport: `2560 × 1215`; `devicePixelRatio: 1`。
- 我的工作实现内容区域: `2068 × 925` CSS px / screenshot pixels；参考图: `1672 × 941` pixels。
- 动态与边界实现内容区域: `2053 × 1039` CSS px / screenshot pixels；参考图: `1536 × 1024` pixels。
- 参考图没有 CSS viewport 与 DPR 元数据。两个 comparison 文件都保留各自长宽比，仅将双方缩放到相同显示高度（我的工作 `900px`，动态与边界 `1000px`），没有非等比拉伸。由于实现浏览器内容区更宽，不用合成图中的物理横向字号做像素级结论。
- 状态: 浅色主题、Owner 视角、研发空间启用、高级模式、真实个人工作数据与 12 条当前活动记录。

## Full-view comparison evidence

- 我的工作保持“二级 Tab → Hero → 四个工作分区 → 通栏可用工作项”的参考顺序。四个分区实测为两列：第一行 `250 / 250px` 等高，第二行 `206 / 206px` 等高；分区标题、数字徽标、逐分区空态和事项打开动作均与参考层级一致。
- 可用工作项保持通栏卡片，类型入口不拉满整行；需求和任务入口使用淡紫图标、两行文案与淡蓝圆形动作按钮。
- 动态与边界保持左右双栏，实测 `1360 / 605px`，比例 `2.248:1`；参考约 `2.27:1`。两卡实测等高 `839px`。
- 动态区保持约 `60px` 行高、蓝色实心时间线节点、细分隔线和右侧时间；边界区保持三条事实、状态胶囊和浅蓝说明块。
- 两个页面的 document horizontal overflow 均为 `0`。

## Focused region comparison evidence

- 两个 combined comparison 均以接近原始高度展示，分区标题、事项两行文本、时间线节点、三条边界事实和类型入口都可直接辨认，因此不需要另做会丢失整体比例的局部放大图。
- 重点检查显示：Hero 图标尺寸和标题层级、四卡同排关系、活动/边界列宽、时间线节点、状态语义色、空态与可用类型入口均已对齐。

## 必查保真面

- Fonts and typography: 沿用项目 Ant Design/系统中文字体栈；Hero、卡片标题、事项标题、辅助文案与时间的字重层级一致，无异常断行或裁切。
- Spacing and layout rhythm: 内容区使用约 `22–32px` 的页边距与区块间距，卡片采用 `14px` 圆角、轻阴影；我的工作两列等高，动态区为 `2.25fr / 1fr`。
- Colors and visual tokens: 主蓝、浅蓝图标底、淡紫类型图标、绿色启用状态与浅灰边框均复用现有产品语义色。
- Image quality and asset fidelity: 参考图没有照片或自定义位图；日历、趋势、盾牌、事实和类型图标全部来自项目既有 `@ant-design/icons`，空态使用 Ant Design 自带 Empty 资源，无手绘 SVG、Emoji、CSS 图形或占位图。
- Copy and content: Hero、四个工作分区、可用工作项、空间动态、协作边界和提示文案均使用真实业务数据。活动 API 不返回总记录数；截断页因此显示“已显示 12 条”，没有伪造参考图中的“共 13 条”。
- States and interactions: 浏览器验证两个 Tab 可切换；活动行进入对应工作项详情；“新建需求”进入带 `create=1` 的工作项页面并打开创建对话框，取消后不产生数据；空分区独立显示空态。
- Accessibility: Tab、事项、类型入口继续使用语义控件和可访问名称；活动按钮不再覆盖为 `listitem` 角色；焦点态与最小操作尺寸已保留。
- Responsiveness: 使用 Overview 容器查询；动态双栏在 `820px` 以下堆叠，工作分区在 `760px` 以下单列，活动行与类型入口在 `620px` 以下收敛。参考图仅提供桌面状态，本轮浏览器证据为桌面视口。

## Comparison history

1. Pass 1 — 发现两个 P2：我的工作第一行空卡没有跟随有数据卡等高；Ant Design 6 Timeline 使用新 DOM 后，时间线节点呈空心。另发现本次新增的 Timeline/Alert 弃用提示。
2. Fix — 工作网格改为 stretch；第一、第二行分别统一为 `250px` 与 `206px`。时间线适配 Ant Design 6 的 `.ant-timeline-item-icon` 并使用实心主蓝节点；Timeline 改用 `items.content`，Alert 改用 `title`。
3. Pass 2 — 更新后的两个并排对照未发现待处理 P0/P1/P2；列宽比例、卡片等高、标题层级、时间线和事实卡均达到参考意图。

## 运行检查

- `pnpm web:lint`: passed
- `pnpm web:build`: passed
- 项目空间 Tab/route/surface 契约: `23/23 passed`
- `git diff --check`: passed
- Browser primary interactions: Tab 切换、活动深链、新建对话框打开/取消、页面无横向溢出均已验证。
- Console: 未发现未捕获运行时异常；本次新增的 Timeline/Alert 弃用提示已清除。仅剩项目既有 Drawer `width` 弃用提示。
- 隔离型 S21-M8 E2E 会创建动态身份和业务数据，本轮未在当前非一次性环境执行；新增布局断言已通过 Lint/TypeScript 检查。

## Findings

- 无待处理 P0/P1/P2。
- P3（可选）: 参考图未提供窄屏视觉真值；当前响应式规则已实现，但窄屏像素级对照需要后续补一张 820px 或手机参考图。

final result: passed
