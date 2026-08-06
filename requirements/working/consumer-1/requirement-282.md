# REQ-282：审计日志页面内容区域未撑满宽度，有大量留白

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-282 |
| 标题 | 审计日志页面内容区域未撑满宽度，有大量留白 |
| 类型 | UI差异 |
| 严重程度 | P1 |
| 发现方式 | 用户反馈 + 对标审查 |
| 发现日期 | 2026-08-06 |
| 关联模块 | 审计日志页面（AuditLogView.vue） |
| 对标文档 | https://www.jetbrains.com/help/youtrack/server/2025.3/audit-events.html |
| 状态 | 待修复 |

## 1. YouTrack 标准行为

YouTrack 审计事件页面中，表格**完整占满页面宽度**，数据列按比例分配空间：
- 没有明显的两侧空白
- 各列根据内容自适应宽度，最后一列（变更详情）占据剩余所有空间
- 「变更详情」列的内容可以充分展开显示

## 2. TrackFlow 当前实现

`AuditLogView.vue` 中存在以下问题：

1. **`admin-page` 有 `padding: 24px`**，本身没问题，但表格内的列宽是固定 px 值：
   - 时间列：`width: 160px`
   - 操作者列：`width: 120px`
   - 操作列：`width: 160px`
   - 目标类型列：`width: 80px`
   - 目标列：`width: 120px`
   - 变更详情列：`flex: 1`（理论上能撑满剩余空间）

2. **实际视觉问题**：截图中可见页面右侧有大块空白，内容没有撑满整个可用区域。根本原因是 `.data-table` 可能没有 `width: 100%`，或者父容器的宽度没有正确撑满。

## 3. 差异分析

| 维度 | YouTrack | TrackFlow | 差异 |
|------|----------|-----------|------|
| 表格宽度 | 100% 撑满 | 有空白区域 | 内容未占满页面 |
| 变更详情列 | 充分展开 | 有时被截断 | 信息展示不完整 |

## 4. 期望结果

确保审计日志表格完整占满页面可用宽度：

1. 给 `.data-table` 加 `width: 100%` 或 `flex: 1`，确保表格撑满容器
2. 检查 `.admin-page` 的父容器是否有正确的 `width: 100%` 和 `min-width` 限制
3. 确保 `.detail-col` （变更详情列）的 `flex: 1` 能正常生效，内容可完整展示
4. 整体内边距合理：左右 padding 不超过 24px

## 5. 验收标准

- [ ] 审计日志表格占满页面可用宽度，视觉上无多余空白
- [ ] 「变更详情」列内容完整显示，不被截断
- [ ] 在 1440px 和 1280px 宽屏幕上表现一致
- [ ] 在管理后台侧边栏展开/收起两种状态下均正确

## 6. 相关文件

- `trackflow-web/src/views/admin/AuditLogView.vue`（`.data-table` 样式）

## 自动化状态

fix_status: DONE
fix_commit: 4bf74ff3
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 18:13

### 本次改动摘要
- 改动1：`trackflow-web/src/views/admin/AuditLogView.vue` — 给 `.admin-page` 添加 `width: 100%` 和 `min-width: 0`，确保在 flex 布局中正确填满宽度（防止在某些场景下组件不能完全占满父容器宽度）
- 改动2：`trackflow-web/src/views/admin/AuditLogView.vue` — 给 `.data-table` 添加显式 `width: 100%`，保证表格始终占满容器宽度
- 改动3：`trackflow-web/src/views/admin/AuditLogView.vue` — 给 `.detail-col` 添加 `min-width: 0` 和 `flex: 1`，确保变更详情列正确填充剩余空间，文本能正确换行而不被截断

### 本次变更文件清单
- `trackflow-web/src/views/admin/AuditLogView.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 导航到 /admin/audit-logs → 验证表格宽度占满页面可用区域，无多余右侧留白
  2. 验证「变更详情」列内容完整显示，较长内容能自动换行而不被截断
- **边界场景**：
  - 在 1440px 和 1280px 视口宽度下表现一致
  - 侧边栏展开/收起两种状态下表格均正确撑满
- **建议测试账号**：超级管理员 testuser
- **注意事项**：本次改动只涉及 CSS 样式，不涉及逻辑变更，重点验证视觉效果

### 审核重点（给 code-review 会话）
- **重点关注文件**：AuditLogView.vue 的 `<style scoped>` 部分
- **潜在风险点**：添加的 CSS 属性都是标准属性，风险极低。`min-width: 0` 是 flex 布局中防止子项溢出的标准做法
- **已知遗留项**：无

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
审计日志页面的 `.admin-page` 容器没有显式设置 `width: 100%`，在 flex 布局链路中可能导致宽度不能正确传递。同时 `.data-table` 虽然作为块级元素默认应获得 100% 宽度，但缺少显式声明在某些边界场景下可能导致问题。`.detail-col` 虽然有 inline style `flex: 1`，但缺少 `min-width: 0` 可能导致在某些情况下 flex 子项无法正确收缩。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `trackflow-web/src/views/admin/AuditLogView.vue` | `.admin-page` 添加 `width: 100%; min-width: 0;` |
| `trackflow-web/src/views/admin/AuditLogView.vue` | `.data-table` 添加 `width: 100%;` |
| `trackflow-web/src/views/admin/AuditLogView.vue` | `.detail-col` 添加 `min-width: 0; flex: 1;` |

### 影响范围
- 仅影响审计日志页面（AuditLogView.vue）的表格布局样式
