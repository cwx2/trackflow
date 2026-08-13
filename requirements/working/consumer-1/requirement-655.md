# REQ-655：项目详情页「Issue 列表」Widget 显示跨项目工单，未按当前项目过滤

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-655 |
| 标题 | 项目详情页「Issue 列表」Widget 显示跨项目工单，未按当前项目过滤 |
| 类型 | 逻辑差异 |
| 严重程度 | P1 |
| 发现方式 | 管理员工作流走查 |
| 发现日期 | 2026-08-14 |
| 关联模块 | Projects / 项目管理 |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/2026.2/Managing-Projects.html |
| 状态 | 已修复 |

## 1. YouTrack 标准行为

YouTrack 中项目概览页（Project Overview）展示的 Issue 列表/最近活动等数据均严格限定在当前项目范围内，不会显示其他项目的工单。
- UI 布局：项目详情页内的工单列表仅显示属于该项目的工单
- 交互方式：用户通过项目详情页查看该项目的工单概况
- 功能点：Issue 列表组件自动按当前项目 ID 过滤，确保数据隔离

## 2. TrackFlow 当前实现

管理员进入 DE4 项目详情页（/projects/DE4）后，「Issue 列表」Widget 中出现了 FE1 项目的工单（如 FE1-40、FE1-50、FE1-31、FE1-30 等），与 DE4 项目的工单混合显示。

**参考截图**：
- ![TrackFlow 项目详情页截图](file:///D:/project/YT/test/tf-projects-1.png) — 在 DE4 项目详情页中可以看到 FE1 项目的工单出现在 Issue 列表 Widget 中

## 3. 差异分析

| 维度 | YouTrack | TrackFlow | 差异 |
|------|----------|-----------|------|
| Issue 列表数据范围 | 严格限定当前项目 | 跨项目混合显示 | Widget 未按项目 ID 过滤，管理员看到其他项目工单 |
| 数据隔离 | 项目详情页的所有数据组件均按项目过滤 | Issue 列表 Widget 未正确应用项目过滤 | 管理员无法通过项目详情页准确了解单个项目的工单状况 |

## 4. 期望结果

TrackFlow 项目详情页（如 /projects/DE4）中的「Issue 列表」Widget 应仅显示属于当前项目（DE4）的工单，不应出现其他项目（如 FE1）的工单。

## 5. 验收标准

- [ ] 项目详情页「Issue 列表」Widget 仅显示属于当前项目的工单
- [ ] 其他项目的工单不会出现在当前项目详情页的任何数据组件中
- [ ] 切换到不同项目详情页时，Issue 列表 Widget 正确刷新为对应项目的工单
- [ ] 改动后截图与 YouTrack 对标截图数据隔离逻辑一致

## 6. 备注

此问题影响管理员对单个项目工单状态的判断。项目详情页是管理员检查项目健康度的重要入口，如果数据来源混杂，会导致误判项目进度和工作量分配。

---

## 审核记录

**审核日期**：2026-08-14
**审核结论**：✅ 通过

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ✅ `configuring-a-project.html.md` 中明确描述项目概览页为"dashboard-like interface for the selected project"，数据应限定于当前项目 |
| 截图确认 | ✅ `![YouTrack截图](file:///D:/project/YT/test/review-yt-655-1.png)` 中可见项目概览页展示的是当前项目的信息（团队成员、设置等），所有数据均按项目隔离 |
| 行为一致 | ✅ 需求描述的期望行为（项目详情页数据按项目隔离）与 YouTrack 文档一致 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 代码分析确认 `ProjectWidgetPanel.vue` 通过 `projectApi.getOverviewDashboard(projectId)` 加载数据，后端 API 在处理 Issue 列表 Widget 时可能未正确按项目 ID 过滤。需求中描述的截图（`test/tf-projects-1.png`）也证实了跨项目工单出现的问题 |
| 代码确认 | ✅ `ProjectWidgetPanel.vue` 中使用 `projectId` 属性调用后端 Dashboard API，但 Issue 列表 Widget 的数据获取逻辑可能未正确传递项目过滤参数 |

**通过理由**：YouTrack 项目概览页严格按项目隔离数据是标准行为，TrackFlow 项目详情页的 Issue 列表 Widget 显示跨项目工单属于明确的逻辑差异，影响管理员对单项目工单状态的判断。
**前置依赖**：无


## 自动化状态

fix_status: DONE
fix_commit: 530613b6
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-14 01:10

### 本次改动摘要
项目详情页的 Widget 组件（Issue 列表、数字卡片、活动流等）在没有显式配置 projectId 时不会过滤数据，导致跨项目数据混显。

修复方式：通过 `dashboardProjectId` prop 将项目概览仪表盘的所属项目 ID 透传给所有 Widget 子组件，作为隐式过滤条件。

- 改动1：`customDashboard.ts` — 补充 `dashboardType` 和 `projectId` 字段到前端 `DashboardDetailVO` 类型
- 改动2：`WidgetCard.vue` — 新增 `dashboardProjectId` optional prop，在 `widgetProps` 中透传给子组件
- 改动3：`ProjectWidgetPanel.vue` — 从后端返回的 `overviewDashboard.projectId`（数字型 ID）传入 WidgetCard
- 改动4：`IssueListWidget.vue` — `loadData` 中 `config.projectId` 为空时使用 `dashboardProjectId` 作为 fallback
- 改动5：`NumberCardWidget.vue` — 同上逻辑
- 改动6：`ActivityFeedWidget.vue` — 同上（使用 `[dashboardProjectId]` 数组格式）
- 改动7：`AgileChartWidget.vue` — `config.projectId || props.dashboardProjectId`
- 改动8：`CalendarWidget.vue` — 新增 `effectiveProjectId` computed 替代 `config.projectId` 直用
- 改动9：`ProjectTeamWidget.vue` — 同上逻辑
- 改动10-12：`BoardStatusWidget/NoteWidget/ReportChartWidget` — 仅添加 prop 声明（避免 Vue fallthrough warnings）

### 本次变更文件清单
- `trackflow-web/src/api/customDashboard.ts`
- `trackflow-web/src/components/base/WidgetCard.vue`
- `trackflow-web/src/views/project/components/ProjectWidgetPanel.vue`
- `trackflow-web/src/views/report/dashboard/widgets/ActivityFeedWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/AgileChartWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/BoardStatusWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/CalendarWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/IssueListWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/NoteWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/NumberCardWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/ProjectTeamWidget.vue`
- `trackflow-web/src/views/report/dashboard/widgets/ReportChartWidget.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入项目 DE4 详情页 `/projects/DE4` → 查看「监控视图」→ 如果没有 Issue 列表 Widget 则添加一个 → 确认 Widget 中显示的工单全部属于 DE4（issueKey 以 DE4- 开头）
  2. 以 testuser 登录 → 进入另一个项目（如 FE1）详情页 → 查看 Issue 列表 Widget → 确认只显示该项目的工单
  3. 个人仪表盘（/dashboard）中的 Issue 列表 Widget 如果没有配置 projectId，应该显示所有项目的工单（不受 dashboardProjectId 影响）
- **边界场景**：
  - 项目详情页新添加的 NumberCard Widget（统计类型：工单总数）应只统计当前项目的工单
  - 新添加的活动流 Widget 应只显示当前项目的活动
- **建议测试账号**：超级管理员 testuser（有权访问所有项目）
- **注意事项**：dashboardProjectId 来自后端返回的 overviewDashboard.projectId（数字型字符串），不是路由中的 project key

### 审核重点（给 code-review 会话）
- **重点关注文件**：WidgetCard.vue（prop 透传逻辑）、IssueListWidget.vue（fallback 逻辑）
- **潜在风险点**：CalendarWidget 中 effectiveProjectId computed 是否在 watch 中也正确使用
- **已知遗留项**：CustomDashboardView 中的 WidgetCard 未传 dashboardProjectId，这是 by design（个人仪表盘不应自动限定项目）

======================

## 修复记录

**修复日期**：2026-08-14
**修复人**：AI Agent（auto 模式）

### 根因分析
项目概览仪表盘（project_overview）的 Widget 组件通过 `config.projectId` 字段过滤数据，但新添加的 Widget 默认 config 为 `{}`（空 JSON），不包含 projectId。因此 IssueListWidget 等组件在 loadData 时未传递 projectId 参数到后端 API，导致返回全部项目的工单。

仪表盘实体（Dashboard）本身携带 `projectId` 字段，后端 VO 也正确返回，但前端之前未将此信息传递给子 Widget。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `customDashboard.ts` | 前端 DashboardDetailVO 类型补充 dashboardType 和 projectId 字段 |
| `WidgetCard.vue` | 新增 dashboardProjectId 可选 prop，通过 widgetProps 透传给所有子组件 |
| `ProjectWidgetPanel.vue` | 传递 overviewDashboard.projectId 作为 dashboardProjectId |
| `IssueListWidget.vue` | config.projectId 为空时 fallback 使用 dashboardProjectId |
| `NumberCardWidget.vue` | 同上 |
| `ActivityFeedWidget.vue` | 同上（数组格式 [dashboardProjectId]） |
| `AgileChartWidget.vue` | 同上 |
| `CalendarWidget.vue` | 新增 effectiveProjectId computed，替代所有直接使用 config.projectId 的位置 |
| `ProjectTeamWidget.vue` | loadData 和 goToMemberIssues 中使用 fallback |
| 其余3个 Widget | 仅添加 prop 声明保持一致性 |

### 影响范围
- 项目详情页的所有 Widget 数据查询
- 不影响个人仪表盘（CustomDashboardView 不传 dashboardProjectId）
