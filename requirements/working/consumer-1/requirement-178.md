# REQ-178：Sprint "还剩 X 天"倒计时在开始日期未到达时显示误导信息

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-178 |
| 标题 | Sprint "还剩 X 天"倒计时在开始日期未到达时显示误导信息 |
| 类型 | 设计不合理 |
| 严重程度 | P2 |
| 发现方式 | 技术负责人工作流走查 |
| 发现日期 | 2026-08-04 |
| 关联模块 | sprint/迭代管理 |
| 状态 | 待开发 |

## 1. 问题描述

Sprint 28 的日期范围是 2026-09-09 至 2026-09-22（开始日期在未来 36 天后），但因为被提前激活为"进行中"状态，页面上显示"⏳ 还剩 49 天"。

这个"还剩 49 天"是从今天（8/4）到结束日期（9/22）的天数差，但对于一个开始日期都还没到的 Sprint 来说，这个信息具有严重误导性：
- 用户会以为 Sprint 已经跑了一段时间、还剩 49 天
- 实际上 Sprint 还没开始，剩余天数没有实际意义

## 2. 复现步骤

1. 登录系统，进入"迭代"页面
2. 查看 Sprint 28（状态"进行中"，日期 2026-09-09 — 2026-09-22）
3. 观察倒计时显示"⏳ 还剩 49 天"

## 3. 期望行为

对于开始日期尚未到达但已被标记为"进行中"的 Sprint：
- 倒计时应明确区分两个概念：
  - 距离开始日期：X 天后开始
  - 距离结束日期：X 天后结束
- 或者当 Sprint 有异常状态警告时（如已显示"开始日期尚未到达"），不应该同时显示"还剩 X 天"以避免混淆
- 正常的"还剩 X 天"仅在 Sprint 已实际进入工作期后（today >= startDate）才有意义

## 4. 实际行为

- 显示"⏳ 还剩 49 天"（计算方式 = endDate - today = 2026-09-22 - 2026-08-04 = 49 天）
- 同一卡片同时显示 ⚠️ "开始日期尚未到达，Sprint 不应处于进行中状态"
- 这两条信息同时出现造成矛盾：一边说"开始日期没到"，一边说"还剩 49 天"——用户到底该如何理解？

## 5. 验收标准

- [ ] Sprint 开始日期未到达时，倒计时改为显示"X 天后开始"或不显示倒计时
- [ ] 仅当 today >= startDate 时才显示"还剩 X 天"（正常含义 = endDate - today）
- [ ] 已过期的 Sprint（today > endDate）显示"已超期 X 天"

## 6. 参考截图

**TrackFlow 当前截图**：`![Sprint倒计时和状态警告同时显示](file:///D:/project/YT/test/tf-sprint-overview-1.png)`

---

## 审核记录

**审核日期**：2026-08-04
**审核结论**：✅ 通过

### YouTrack 对标验证

| 维度 | 结果 |
|------|------|
| 文档确认 | ⚠️ YouTrack 没有独立的 Sprint 管理页面倒计时显示（Sprint 管理集成在看板内），因此无直接对标 |
| 设计合理性 | ✅ 通用 UX 原则：倒计时应反映实际剩余工作时间。当 Sprint 尚未进入工作期（today < startDate），显示"还剩 X 天"具有明确的误导性 |

### TrackFlow 验证

| 维度 | 结果 |
|------|------|
| 问题存在 | ✅ 代码确认：`SprintView.vue` 第 1043 行 `getRemainingDays()` 函数仅计算 `endDate - today`，不检查 `today >= startDate` 条件。当 Sprint 被提前激活时，页面同时显示"⏳ 还剩 49 天"和"⚠️ 开始日期尚未到达"两条互相矛盾的信息 |
| 代码确认 | ✅ 模板第 86-95 行：`v-if="getRemainingDays(sprint) !== null"` 无条件显示倒计时，未判断 Sprint 是否已实际进入工作期 |

### 重复性检查

REQ-24（Sprint 开始日期未到但状态显示为"进行中"）和 REQ-27 关注的是"是否应阻止提前激活 Sprint"——属于后端校验问题，均已被拒绝。REQ-178 关注的是**前端倒计时文案的逻辑错误**——即使允许提前激活，倒计时显示也不应误导用户。这是完全不同的问题维度。

**通过理由**：前端倒计时逻辑缺少 `today >= startDate` 判断，导致信息矛盾和误导。修复成本极低（仅前端一个函数），收益明确。P2 合理。
**前置依赖**：无

## 自动化状态

fix_status: DONE
fix_commit: 8f6df816
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-04 22:05

### 本次改动摘要
- 改动1：`trackflow-web/src/views/sprint/SprintView.vue` — 将 `getRemainingDays()` 函数替换为 `getSprintTimeInfo()`，新函数返回包含 `type`（'not-started' | 'remaining' | 'today' | 'overdue'）和 `days` 的结构体。当 `today < startDate` 时返回 `not-started` 类型，显示"X 天后开始"而非误导性的"还剩 X 天"。
- 改动2：同文件模板区域 — 更新倒计时显示逻辑，根据 4 种状态显示不同文案和图标
- 改动3：同文件样式区域 — 新增 `.remaining-icon.not-started` CSS 类，使用三级文字色

### 本次变更文件清单
- `trackflow-web/src/views/sprint/SprintView.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入"迭代"页面 → 找到一个"进行中"且开始日期在未来的 Sprint → 验证显示"📅 X 天后开始"而非"⏳ 还剩 X 天"
  2. 以 testuser 登录 → 进入"迭代"页面 → 找到一个"进行中"且开始日期已过的 Sprint → 验证正常显示"⏳ 还剩 X 天"
  3. 以 testuser 登录 → 进入"迭代"页面 → 找到一个已超期的 Sprint → 验证显示"🚨 已超期 X 天"
- **边界场景**：
  - Sprint 没有 endDate 时，不显示倒计时信息
  - Sprint 没有 startDate 但有 endDate 时，正常显示"还剩 X 天"
- **建议测试账号**：testuser（超级管理员，可看到所有 Sprint）
- **注意事项**：Sprint 28 的日期是 2026-09-09 至 2026-09-22，属于"进行中但未到开始日期"的情况，应显示"X 天后开始"

### 审核重点（给 code-review 会话）
- **重点关注文件**：`trackflow-web/src/views/sprint/SprintView.vue`
- **潜在风险点**：模板中多次调用 `getSprintTimeInfo(sprint)` 可能有性能隐患（Vue 模板中重复调用函数），但因为 Sprint 列表通常 < 10 项，性能影响可忽略
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-04
**修复人**：AI Agent（auto 模式）

### 根因分析
`getRemainingDays()` 函数仅计算 `endDate - today`，不检查 `today >= startDate` 条件。当 Sprint 被提前激活（开始日期在未来）时，页面同时显示"⏳ 还剩 49 天"和"⚠️ 开始日期尚未到达"两条互相矛盾的信息。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/sprint/SprintView.vue` | 用 `getSprintTimeInfo()` 替换 `getRemainingDays()`，新函数先判断 `today < startDate` 返回 'not-started' 状态显示"X 天后开始"，否则走原有的剩余/超期逻辑。模板和样式同步更新。 |

### 影响范围
- Sprint 迭代管理页面的"进行中" Sprint 卡片倒计时显示
