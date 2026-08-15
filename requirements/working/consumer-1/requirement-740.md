# REQ-740：「FE1 当前迭代」Saved Query 左侧面板 badge 计数显示"0"，但点击后实际返回 5 条工单

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-740 |
| 标题 | 「FE1 当前迭代」Saved Query 左侧面板 badge 计数显示"0"，但点击后实际返回 5 条工单 |
| 类型 | 逻辑差异 |
| 严重程度 | P2 |
| 发现方式 | 开发人员工作流走查 |
| 发现日期 | 2026-08-15 |
| 关联模块 | Saved Query / 工单列表 |
| 状态 | 已修复 |

## 1. 问题描述

开发人员（liuyang）登录系统后，左侧面板"已保存的搜索"区域中「FE1 当前迭代」查询的 badge 计数显示为 **"0"**，但点击该查询后工单列表实际返回 **5 条工单**（FE1-50、FE1-38、FE1-35、FE1-34、FE1-47），底部分页也显示"共 5 条"。

这导致开发人员在不点击查询的情况下误以为当前迭代没有工单，无法通过 badge 数字快速判断工作量。

## 2. 根因分析

根据 REQ-731 的测试备注，已定位根因：

`SavedQueryService.countForQueryWithProjectFilter` 方法在计算 badge 计数时**未调用 `injectProjectContext`**，导致 `extractProjectIds` 返回空列表。后续 `resolveCurrentSprintIds` 在全局范围查找 ACTIVE 状态的 Sprint（找到了其他项目如 TF1 的活跃 Sprint），而非 FE1 项目的已完成 Sprint 中的遗留工单。

而点击查询执行时，`QueryExecutor` 正确调用了 `injectProjectContext`，因此能正确返回 5 条结果。

**两条代码路径不一致**：
- 计数路径（badge）：`countForQueryWithProjectFilter` → 缺少 `injectProjectContext` → 错误计数
- 执行路径（列表）：`QueryExecutor` → 有 `injectProjectContext` → 正确结果

## 3. 复现步骤

1. 以 liuyang 账号登录 TrackFlow
2. 观察左侧面板"已保存的搜索"中「FE1 当前迭代」的 badge 数字 → 显示 "0"
3. 点击「FE1 当前迭代」查询
4. 观察右侧工单列表 → 显示 5 条工单，底部分页显示"共 5 条"

## 4. 期望结果

- 「FE1 当前迭代」badge 计数应显示 **"5"**，与点击后的实际查询结果一致
- 计数路径和执行路径应使用相同的 `injectProjectContext` 逻辑
- 当项目无活跃 Sprint 时，"当前迭代"类查询应自动 fallback 到最近已完成 Sprint 的未关闭工单（与执行路径行为一致）

## 5. 验收标准

- [ ] `SavedQueryService.countForQueryWithProjectFilter` 内部调用 `injectProjectContext`，确保计数逻辑与执行逻辑一致
- [ ] 左侧面板「FE1 当前迭代」badge 数字与点击后列表底部"共 X 条"完全一致
- [ ] 当 FE1 项目无活跃 Sprint、但有已完成 Sprint 中的遗留工单时，badge 正确显示遗留工单数

## 6. 备注

- 此问题在 REQ-731 测试过程中已被定位为"独立 cosmetic 问题，可单独提需求跟踪"
- REQ-634（rejected）描述了类似问题但当时是"badge 显示 2 但返回 0"，与本次相反
- 影响所有包含项目上下文注入逻辑的 Saved Query 的 badge 计数准确性


---

## 审核记录

**审核日期**：2026-08-15
**审核结论**：✅ 通过

### 验证说明

此需求属于"逻辑差异"类型，描述的是 TrackFlow 系统**内部一致性问题**——同一 Saved Query 的 badge 计数路径与查询执行路径产生不同结果。

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ⚠️ YouTrack 文档（saved-search.html.md 和 issues-list.html.md Sidebar 章节）中 Saved Searches 列表未明确展示 badge 计数功能 |
| 截图确认 | ⚠️ `![YouTrack Sidebar截图](file:///D:/project/YT/test/review-yt-740-1.png)` 和 `![YouTrack Issue Sidebar](file:///D:/project/YT/test/review-yt-740-2.png)` 中 Saved Searches 列表项旁未显示数字 badge |
| 评估结论 | badge 计数可能是 TrackFlow 自主设计的增强功能，但其内部一致性问题仍是有效缺陷 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 代码确认 | ✅ `SavedQueryService.countForQueryWithProjectFilter` 方法确实存在，badge 计数功能已实现在代码中 |
| 问题存在 | ✅ 需求中描述的根因分析（计数路径缺少 `injectProjectContext` 调用）由 REQ-731 测试过程中已定位确认 |
| 重复性检查 | ⚠️ REQ-23（已实现）描述了类似的 Saved Query 计数不一致问题，但 REQ-740 定位了具体根因且针对不同场景（Sprint 项目上下文注入），属于更精确的修复需求 |

### 通过理由

虽然 YouTrack 文档中未明确展示 Saved Search 的 badge 计数功能，但 TrackFlow 已自主实现了此功能。此需求描述的是**系统内部一致性缺陷**：badge 计数代码路径（`countForQueryWithProjectFilter`）与查询执行路径（`QueryExecutor`）对同一 Saved Query 产生不同结果，这是一个明确的逻辑 Bug，不依赖于是否对标 YouTrack 的特定功能。根因已明确定位，修复方向清晰。

**前置依赖**：无（独立修复）
**与 REQ-23 的关系**：REQ-23 是更宽泛的计数不一致问题，REQ-740 是对特定场景（项目上下文注入缺失）的精确定位，两者互补而非重复。

## 自动化状态

fix_status: DONE
fix_commit: d039d91
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-15 14:50

### 本次改动摘要
- 改动1：`SavedQueryService.java` — 在 `countForQuery()` 方法中，解析 filters 后添加 `injectProjectContext(filters, query.getProjectId())` 调用，确保不带项目过滤的计数路径也能正确注入项目上下文（用于解析 `${currentSprint}` 等动态变量）
- 改动2：`SavedQueryService.java` — 在 `countForQueryWithProjectFilter()` 方法中同样添加 `injectProjectContext` 调用，与执行路径（line 374）保持一致

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/query/service/SavedQueryService.java`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 liuyang 账号登录 → 观察左侧面板「FE1 当前迭代」Saved Query 的 badge 数字 → 应不为 0（应与点击后列表显示的总数一致）
  2. 点击「FE1 当前迭代」→ 确认右侧工单列表显示工单，底部分页显示"共 N 条"→ 对比 badge 数字一致
  3. 以 testuser 登录 → 检查其他有项目绑定的 Saved Query badge 计数是否正确反映实际结果
- **边界场景**：
  - 项目无活跃 Sprint 但有已完成 Sprint 遗留工单时，badge 应正确统计
  - 没有绑定项目的 Saved Query 行为不受影响
- **建议测试账号**：liuyang（开发人员，复现场景原始账号）
- **注意事项**：修复仅涉及计数路径，查询执行路径无变化，不应产生回归

### 审核重点（给 code-review 会话）
- **重点关注文件**：SavedQueryService.java（countForQuery 和 countForQueryWithProjectFilter 两个方法）
- **潜在风险点**：无——`injectProjectContext` 是已有方法，逻辑是"如果 query 绑定了项目且 filters 中没有 project 字段，则注入隐式 project filter"。对未绑定项目的 query，projectId 为 null 时直接 return，不会有副作用
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-15
**修复人**：AI Agent（auto 模式）

### 根因分析
`SavedQueryService` 中有两条代码路径处理 Saved Query：
1. **执行路径**（点击查询）：调用 `injectProjectContext(filters, query.getProjectId())` 后再执行查询 → 正确
2. **计数路径**（badge 数字）：`countForQuery` 和 `countForQueryWithProjectFilter` 解析 filters 后直接传给 QueryExecutor 计数，**未调用 `injectProjectContext`** → 导致 `${currentSprint}` 等动态变量在解析时缺少项目上下文，`resolveCurrentSprintIds` 在全局范围查找活跃 Sprint，结果错误

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-server/.../query/service/SavedQueryService.java` | 在 `countForQuery()` 和 `countForQueryWithProjectFilter()` 中，`parseFilters` 之后添加 `injectProjectContext(filters, query.getProjectId())` 调用，与执行路径保持一致 |

### 影响范围
- 所有绑定了项目且使用动态变量（如 `${currentSprint}`）的 Saved Query 的 badge 计数
- 未绑定项目的 Saved Query 不受影响（`injectProjectContext` 对 null projectId 直接 return）
