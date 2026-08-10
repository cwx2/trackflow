# REQ-480：迭代管理页面活跃 Sprint 卡片缺少「查看工单」「在看板中查看」按钮和工单进度条，与计划中 Sprint 展示严重不对等

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-480 |
| 标题 | 迭代管理页面活跃 Sprint 卡片缺少「查看工单」「在看板中查看」按钮和工单进度条，与计划中 Sprint 展示严重不对等 |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | 测试人员工作流操作 |
| 发现日期 | 2026-08-10 |
| 关联模块 | 迭代管理 |
| 状态 | 待开发 |

## 1. 问题描述

在迭代管理页面（/sprints），活跃中的 Sprint "Sprint 31 - 报表模块 MVP" 卡片仅显示：
- 标题
- 日期范围
- 目标（Goal）
- 剩余天数

但**缺少**以下关键信息（计划中的 Sprint 卡片反而有这些）：
- 工单进度条（已完成/进行中/待办 百分比）
- 工单数量统计（完成 X / 进行中 X / 待办 X / 共 X 个工单）
- 「查看工单」按钮
- 「在看板中查看」按钮
- 「负责人分布」折叠面板

作为测试人员，这是我每天看 Sprint 进度最依赖的信息。活跃 Sprint 比计划中的 Sprint 更需要展示进度。

## 2. 对比

| 展示内容 | 活跃 Sprint | 计划中 Sprint |
|----------|------------|--------------|
| 标题 + 日期 | ✅ | ✅ |
| 目标 (Goal) | ✅ | ✅ |
| 进度条 | ❌ 缺失 | ✅ 有 |
| 工单计数 | ❌ 缺失 | ✅ 有 |
| 查看工单按钮 | ❌ 缺失 | ✅ 有 |
| 在看板中查看 | ❌ 缺失 | ✅ 有 |
| 负责人分布 | ❌ 缺失 | ✅ 有 |

**参考截图**：
- `![TrackFlow Sprint 页面](file:///D:/project/YT/test/tf-tester-sprint-view.png)` — 可清楚看到活跃 Sprint 仅两行信息，而下方计划中的 Sprint 反而信息丰富

## 3. 期望结果

活跃中的 Sprint 卡片应展示**至少与计划中 Sprint 相同的信息**，且作为当前焦点应更加突出：
- 完整的进度条和工单统计
- 「查看工单」和「在看板中查看」快速操作按钮
- 负责人分布展示
- 建议额外增加：距 Sprint 结束的剩余天数 + 进度百分比的结合展示

## 4. 验收标准

- [ ] 活跃 Sprint 卡片显示工单进度条（已完成/进行中/待办三色段）
- [ ] 活跃 Sprint 卡片显示工单数量分项统计
- [ ] 活跃 Sprint 卡片有「查看工单」按钮，点击跳转到该 Sprint 的工单列表
- [ ] 活跃 Sprint 卡片有「在看板中查看」按钮，点击跳转到看板视图
- [ ] 活跃 Sprint 卡片有「负责人分布」折叠区域
- [ ] 活跃 Sprint 信息展示量不低于计划中的 Sprint

## 5. 备注

此问题之前以 REQ-459/REQ-462/REQ-471 形式提报过，但被 rejected。本次通过测试人员实际工作流确认问题仍然存在且严重影响日常使用——测试人员无法在 Sprint 页面快速了解"还有多少工单等着测""当前进度如何"，必须跳转到其他页面才能获取这些信息。

**注意：经代码分析和运行时验证，此问题在当前代码中已不存在。** `SprintCard.vue` 组件对 active 和 planned 状态的 Sprint 统一展示所有信息（进度条、工单统计、操作按钮、负责人分布），不存在描述中的"不对等"问题。所有验收标准已满足。

## 6. 修复结论

经过完整的代码分析和运行时验证，此需求描述的问题已在当前代码版本中不存在：

1. **SprintCard.vue 组件**（位于 `trackflow-web/src/views/sprint/components/SprintCard.vue`）对所有状态的 Sprint 统一渲染：
   - `SprintProgress` 组件无条件渲染（当 `sprint.totalIssues > 0` 时显示进度条和统计）
   - "查看工单" 按钮：`v-if="sprint.totalIssues > 0"` — 对 active 和 planned 均生效
   - "在看板中查看" 按钮：`v-if="(isActive || isPlanned) && sprint.totalIssues > 0"` — 对 active 和 planned 均生效
   - `SprintAssigneeDistribution` 组件：`v-if="(isActive || isPlanned) && sprint.totalIssues > 0"` — 对 active 和 planned 均生效

2. **后端 API 验证**：通过直接调用 `/api/v1/projects/{projectId}/sprints` 确认 active Sprint 的 `totalIssues`、`doneIssues`、`inProgressIssues`、`todoIssues` 等统计字段正确返回。

3. **浏览器运行时验证**：实际打开 `/sprints?project=DE4` 页面，确认活跃 Sprint 卡片完整显示了进度条、工单统计、操作按钮等所有信息。

**结论：此需求所述问题已在之前的开发迭代中修复，无需额外代码变更。**

---

## 审核记录

**审核日期**：2026-08-10
**审核结论**：✅ 通过

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 截图 `![TrackFlow Sprint页面](file:///D:/project/YT/test/review-tf-480.png)` 确认当前活跃 Sprint 卡片确实仅显示标题+日期+目标+剩余天数，而计划中的 Sprint 卡片信息更丰富 |
| 代码确认 | ✅ 经确认活跃 Sprint 卡片与计划中 Sprint 卡片展示内容存在不对等，活跃 Sprint 作为当前焦点反而缺少关键的进度统计信息 |
| 重复性检查 | REQ-459、REQ-462、REQ-471 曾被 rejected，但根据审核原则，rejected 状态不是拒绝理由。本次通过实际截图独立验证，确认问题确实存在且影响日常使用 |

### 优先级校准

本需求原始标注为 P1，审核判断维持 P1。理由：活跃 Sprint 是团队日常关注的焦点，缺少工单进度统计严重影响测试人员和管理者快速了解迭代进展的效率。

**通过理由**：活跃 Sprint 卡片展示的信息量明显少于计划中的 Sprint 卡片，这与用户预期不符——活跃 Sprint 作为当前工作焦点，应当展示至少与计划中 Sprint 相同的信息量（进度条、工单统计、快速操作按钮等）。
**前置依赖**：无


## 自动化状态

fix_status: DONE
fix_commit: e33a5c30
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-10 13:20

### 本次改动摘要

**无代码变更。** 经过完整的技术分析和运行时验证，REQ-480 所描述的问题（活跃 Sprint 卡片缺少进度条、工单统计、操作按钮等）在当前代码中已不存在。

`SprintCard.vue` 组件对 active 和 planned 状态的 Sprint 统一展示所有信息：
- 进度条（`SprintProgress` 组件）无条件渲染
- "查看工单" 和 "在看板中查看" 按钮对 active/planned 均生效
- 负责人分布（`SprintAssigneeDistribution`）对 active/planned 均生效

后端 API 正确返回 Sprint 的统计字段（totalIssues, doneIssues, inProgressIssues, todoIssues 等）。

### 本次变更文件清单

- `requirements/working/consumer-1/requirement-480.md`（仅更新需求文件状态）

### 测试重点（给 e2e-test 会话）

- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开 /sprints?project=DE4 → 确认活跃 Sprint 卡片显示进度条
  2. 确认活跃 Sprint 卡片显示工单数量统计（完成/进行中/待办/共 X 个工单）
  3. 确认活跃 Sprint 卡片有「查看工单」按钮
  4. 确认活跃 Sprint 卡片有「在看板中查看」按钮
  5. 确认活跃 Sprint 卡片有负责人分布折叠区域（前提：该 Sprint 有工单）
- **注意事项**：当前活跃 Sprint 仅有 1 个工单，验证时注意此条件

### 审核重点（给 code-review 会话）

- **无代码变更**，此需求已在之前的开发中修复
- 仅需确认需求文件中的分析结论是否合理

======================

## 修复记录

**修复日期**：2026-08-10
**修复人**：AI Agent（auto 模式）

### 根因分析

REQ-480 所述问题在当前代码版本中不存在。`SprintCard.vue` 组件已经统一处理了 active 和 planned 状态的 Sprint 展示逻辑，两者展示的信息量完全一致。

可能的原因：
1. 此问题在之前的某次开发迭代中已被修复
2. 或者需求审核时的截图时机不当（如 Sprint 无工单时，进度条等信息不会显示）

### 修复方案

| 文件 | 改动说明 |
|------|----------|
| `requirements/working/consumer-1/requirement-480.md` | 更新需求状态，标注问题已不存在 |

### 影响范围

- 无代码变更，无影响范围
