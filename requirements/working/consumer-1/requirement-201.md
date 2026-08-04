# REQ-201：工单活动流中评论与字段变更应合并展示

## 需求描述

在工单详情页的活动流中，当用户在**1 分钟内**既提交了评论又修改了字段，这两个操作应被视为"关联操作"，在 UI 上**合并展示为一条活动记录**：上方显示评论正文，下方用灰色背景块显示字段变更（格式：`字段名: 旧值 → 新值`）。

当前 TrackFlow 将评论和字段变更分别渲染为独立条目，导致活动流碎片化，用户无法直观理解"评论时顺手改了什么字段"。

## YouTrack 文档依据

YouTrack 官方文档 [Add Comments](add-a-comment.html) 中明确说明：

> **"All the changes that are applied to an issue within one minute from adding a comment are considered to be related to the comment. When the issue history is enabled in the activity stream, these changes are displayed immediately following the comment."**

**参考截图**：

![YouTrack 评论关联字段变更](file:///D:/project/YT/youtrack-docs/images/1f4385be1cc8.png)

截图说明：Anita Freeman 提交评论"I'm going to need to wait for Charles to get back from vacation to finish this task. Marking it as blocked."，同时将 Kanban State 从 Ready to pull 改为 Blocked，两者合并展示：评论文本 + 灰色块 `Kanban State: Ready to pull → Blocked`。

## 当前行为（问题）

- 评论提交 → 生成一条活动记录（仅含评论文本）
- 字段修改 → 生成另一条独立活动记录（仅含字段变更）
- 两条记录在活动流中**分开显示**，视觉上无关联

## 期望行为（修复后）

### 场景 1：提交评论时同步修改字段（1分钟内）

活动流渲染效果：

```
┌─────────────────────────────────────────┐
│ 👤 张伟  · 3分钟前                      │
│                                         │
│ 代码已合并，正在部署到测试环境。          │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ 状态: 代码审查 → 测试中           │  │  ← 灰色背景块
│  │ 负责人: 张伟 → 赵静               │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 场景 2：仅提交评论（无字段变更）

正常渲染评论，无灰色块。

### 场景 3：仅修改字段（无评论）

正常渲染字段变更条目，无评论文本。

### 场景 4：评论与字段变更间隔超过 1 分钟

两者分开显示，不合并。

## 技术实现要点

### 后端

**方案 A（推荐）：在活动记录层关联**

修改 `issue_activity` 表或查询逻辑，记录"关联评论 ID"字段：

```sql
-- issue_activity 表新增字段（或在查询时关联）
ALTER TABLE issue_activity ADD COLUMN related_comment_id BIGINT REFERENCES issue_comment(id);
```

当字段变更发生在某条评论创建后 1 分钟内，将该变更记录关联到该评论。

前端 API 返回时，将评论及其关联的字段变更合并为一个对象：

```json
{
  "type": "comment",
  "id": 123,
  "author": { "id": "...", "name": "张伟" },
  "createdAt": "2026-08-05T10:00:00Z",
  "content": "代码已合并...",
  "relatedChanges": [
    { "field": "状态", "from": "代码审查", "to": "测试中" },
    { "field": "负责人", "from": "张伟", "to": "赵静" }
  ]
}
```

**方案 B（纯前端聚合）**：

活动列表 API 按时间排序返回所有记录，前端渲染时检测"评论之后 1 分钟内的字段变更"，将其附加到该评论条目下方渲染。不修改后端结构。

### 前端

活动流组件渲染逻辑（无论哪个方案）：

1. 遍历活动列表
2. 若某条活动类型为 `comment`，且其 `relatedChanges` 非空（或前端聚合后非空）：
   - 渲染评论正文
   - 在评论正文下方渲染灰色背景块
   - 灰色块内每行格式：`字段名: 旧值 → 新值`
3. 若 `relatedChanges` 为空，只渲染评论文本

### 灰色块 UI 规范

参考截图中的样式：
- 背景色：`var(--tf-bg-surface)` 或比评论区域略深的灰色
- 圆角：`6px`
- 内边距：`8px 12px`
- 每行文字：`字段名` 用普通色，`旧值 → 新值` 用次要色
- 箭头 `→` 使用系统字符或图标

## 验收标准

1. 用户提交评论后 1 分钟内修改字段，活动流中两者合并为一条记录展示
2. 字段变更块显示在评论文本正下方，有灰色背景区分
3. 多个字段同时变更时，每个字段单独一行
4. 评论与字段变更间隔超过 1 分钟时，分开显示
5. 仅评论无字段变更：正常显示评论，无灰色块
6. 仅字段变更无评论：正常显示字段变更，不显示评论区
7. 活动流筛选器（评论/变更历史）关闭时，对应部分隐藏，合并块中只显示另一部分

## 优先级

**P2** — 体验提升，YouTrack 标准行为

## 模块

`Issue 详情 / 活动流`

## 自动化状态

fix_status: DONE
fix_commit: 64644eb0
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 06:30

### 本次改动摘要
- 改动1：`trackflow-web/src/views/issue/components/ActivityStream.vue` — 新增 `RelatedChange` 接口和 `relatedChanges` 字段到 `ActivityItem`；在模板中评论文本下方新增灰色背景的字段变更块；更新 filter 逻辑让"变更"筛选器也展示合并到评论的字段变更（仅显示变更块不显示评论文本）；新增 `hideCommentText()` 辅助函数控制评论文本可见性；新增相关 CSS 样式。
- 改动2：`trackflow-web/src/views/issue/IssueDetailView.vue` — 重写 `activityItems` computed 属性，实现前端聚合逻辑：遍历所有有 `fieldName` 的活动记录，检测是否在某条评论创建后 1 分钟内且同一用户，将其合并为 `relatedChanges` 数组附加到评论条目，合并后的活动不再独立渲染。选择最近的符合条件的评论进行合并。

### 本次变更文件清单
- `trackflow-web/src/views/issue/IssueDetailView.vue`
- `trackflow-web/src/views/issue/components/ActivityStream.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开任一有活动记录的工单详情（如 DE4 项目任一工单） → 查看活动流是否正常渲染（评论和变更都正常显示）
  2. 找到一个有评论且评论后 1 分钟内有字段变更的工单（或手动操作制造：先提交评论，然后立即修改状态/负责人等字段），验证活动流中该评论下方出现灰色背景的字段变更块
  3. 切换活动流筛选器到"变更"模式，验证合并的字段变更仍然展示（仅灰色块，无评论文本）
  4. 切换到"评论"模式，验证评论正常显示且关联变更块附在下方
  5. 间隔超过 1 分钟的评论和字段变更应分开展示（不合并）
- **边界场景**：
  - 无评论仅有字段变更的工单：活动流正常显示独立变更条目
  - 仅有评论无字段变更：活动流正常显示评论，无灰色块
  - 已删除的评论不应合并字段变更
- **建议测试账号**：超级管理员 testuser
- **注意事项**：这是纯前端变更，无需重启后端。需要有真实的活动数据才能验证合并效果。可以通过"提交评论后立即修改字段"来制造测试数据。

### 审核重点（给 code-review 会话）
- **重点关注文件**：ActivityStream.vue（模板结构和 CSS）、IssueDetailView.vue（合并逻辑算法）
- **潜在风险点**：合并逻辑的时间窗口判断是否有边界问题；`v-if`/`v-else-if` 分支在模板中是否正确嵌套；filter 函数的类型安全性
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
前端活动流将评论（comments）和字段变更（activities）作为独立条目平铺渲染，缺乏 YouTrack 风格的时间窗口内关联合并逻辑，导致用户无法直观看到"评论时顺手改了什么字段"的上下文。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/issue/components/ActivityStream.vue` | 新增 RelatedChange 接口、relatedChanges 字段、灰色变更块模板和 CSS、hideCommentText 辅助函数、filter 逻辑更新 |
| `trackflow-web/src/views/issue/IssueDetailView.vue` | 重写 activityItems computed，实现 1 分钟窗口内同用户评论+变更的前端聚合 |

### 影响范围
- Issue 详情页活动流渲染逻辑
- 活动流筛选器（全部/评论/变更）的过滤行为
