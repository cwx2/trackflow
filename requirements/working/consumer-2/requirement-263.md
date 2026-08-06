# REQ-263：前端 53 处内联 SVG 图标应替换为 Arco Design 框架图标组件

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-263 |
| 标题 | 前端 53 处内联 SVG 图标应替换为 Arco Design 框架图标组件 |
| 类型 | 编码规范违规 |
| 严重程度 | P3 |
| 发现方式 | 前端代码审查 |
| 发现日期 | 2026-08-05 |
| 关联模块 | layout / notification / admin / automation / board / issue / settings |
| 影响层级 | 前端 |
| 状态 | 已修复 |

## 1. 问题概述

**一句话**：项目已引入 Arco Design Vue，但 14 个 Vue 文件中仍有 53 处硬编码的内联 `<svg>` 图标，应使用 Arco 的 `<icon-*>` 组件替换，以减少冗余代码、统一视觉风格、保持主题一致性。

**技术根因**：功能开发时直接粘贴了 GitHub Octicons / Feather 的 SVG path，未查阅 Arco Design 图标库。

## 2. 问题证据

通过代码搜索，受影响文件及 SVG 数量：

```
NotificationPanel.vue           10 处
NotificationView.vue             8 处
UserManagement.vue              10 处
BottomToolbar.vue (automation)   5 处
UserDetailView.vue               3 处
NodeCard.vue (automation)        3 处
DetailSidebar.vue                2 处（issue）
KanbanBoardViewImpl.vue          2 处
NotificationSubscriptions.vue    2 处
ProjectNotificationPreferences.vue 2 处
BoardSelector.vue                1 处
DraggableColumnHeader.vue        1 处
ProjectSettingsCustomFields.vue  1 处
TimeProgressIndicator.vue        1 处
```

## 3. 替换对照表

### 可直接替换（约 49 处）

| 当前 SVG 用途 | viewBox | 所在文件 | Arco 替代组件 |
|---|---|---|---|
| 关闭 / × | 16×16 | NotificationPanel、NotificationView、NotificationSubscriptions | `<icon-close>` |
| 仅未读（圆圈√） | 16×16 | NotificationPanel、NotificationView | `<icon-check-circle>` |
| 清除已读（垃圾桶） | 16×16 | NotificationPanel、NotificationView | `<icon-delete>` |
| 展开为全页面（外链箭头） | 16×16 | NotificationPanel | `<icon-expand-alt>` |
| 新建（＋） | 16×16 | UserManagement、ProjectNotificationPreferences、NotificationSubscriptions | `<icon-plus>` |
| 排序箭头（△） | 10×10 | UserManagement | `<icon-caret-up>` |
| info 圆圈（ⓘ） | 16×16 | UserManagement、UserDetailView | `<icon-info-circle>` |
| 禁用状态（×圆圈） | 16×16 | UserDetailView | `<icon-close-circle>` |
| 外部链接 ↗ | 16×16 | UserDetailView | `<icon-export>` |
| 注释气泡 | 24×24 | BottomToolbar | `<icon-message>` |
| 布局优化（四方格） | 24×24 | BottomToolbar | `<icon-apps>` |
| 导出图片（图片＋山） | 24×24 | BottomToolbar | `<icon-image>` |
| 缩略图（小地图框） | 24×24 | BottomToolbar | `<icon-mind-mapping>` |
| 运行（▶ 三角） | 24×24 | NodeCard | `<icon-play-arrow>` |
| 更多操作（···） | 24×24 | NodeCard | `<icon-more>` |
| 展开箭头（stroke ∨） | 24×24 | NodeCard | `<icon-down>` |
| 锁（权限不足只读） | 16×16 | DetailSidebar | `<icon-lock>` |
| 在列表中打开（外链） | 16×16 | KanbanBoardViewImpl（×2） | `<icon-export>` |
| 下拉箭头（∨） | 12×12 | BoardSelector | `<icon-down>` |
| 删除列（垃圾桶） | 16×16 | DraggableColumnHeader | `<icon-delete>` |
| 拖拽手柄（6个点） | 12×16 | ProjectSettingsCustomFields | `<icon-drag-dot-vertical>` |

### 建议保留的 SVG（4 处）

| 文件 | SVG | 原因 |
|---|---|---|
| NotificationPanel、NotificationView | 全部已读（双勾图标，git-branch 造型） | Arco 无等效的 `check-double` 图标 |
| UserManagement | 空状态人物插图（48×48） | 装饰性插图，非功能图标，建议改用 `<a-empty>` 组件 |
| DetailSidebar | 计算器图标（只读计算字段） | Arco 无 `icon-calculator`，保留 SVG |

## 4. 改进方案

### 替换写法示例

```vue
<!-- ❌ 当前：内联 SVG -->
<button class="panel-action-btn" title="关闭" @click="closePanel">
  <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
    <path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94..."/>
  </svg>
</button>

<!-- ✅ 替换后：Arco 图标 -->
<button class="panel-action-btn" title="关闭" @click="closePanel">
  <icon-close :size="14" />
</button>
```

```vue
<!-- ❌ 当前：内联 SVG 垃圾桶 -->
<svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
  <path d="M11 1.75V3h2.25..."/>
</svg>

<!-- ✅ 替换后 -->
<icon-delete :size="14" />
```

### 空状态插图建议

`UserManagement.vue` 的空状态图形建议改用 Arco 的空状态组件：

```vue
<!-- ❌ 当前：手写 SVG 插图 -->
<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
  <path d="M16 21v-2a4 4 0 00-4-4H6..."/>
</svg>
<h3>暂无用户</h3>
<p>点击"新建用户"按钮添加第一个用户</p>

<!-- ✅ 替换后：使用 Arco Empty 组件 -->
<a-empty description="暂无用户">
  <template #extra>
    <a-button type="primary" @click="showCreateDialog = true">新建用户</a-button>
  </template>
</a-empty>
```

## 5. 影响分析

### 业务影响

- **视觉一致性**：Arco 图标会跟随主题色（亮色/暗色/护眼），硬编码 SVG 的 `fill="currentColor"` 在某些上下文可能表现不一致
- **代码可读性**：`<icon-delete :size="14" />` 比 10 行 SVG path 直观
- **包体积**：Arco 图标按需引入，实际影响可忽略

### 风险评估

| 风险维度 | 当前状态 | 说明 |
|----------|----------|------|
| 视觉回归 | ⚠️ | 替换后图标造型略有差异，需对照视觉验收 |
| 功能影响 | ✅ | 纯 UI 替换，无业务逻辑 |

## 6. 实现复杂度

| 维度 | 评估 |
|------|------|
| 改动文件数 | 14 个 Vue 文件 |
| 是否需要数据迁移 | 否 |
| 是否需要前后端联动 | 否 |
| 预计工作量 | 小（<2h，机械性替换） |

## 7. 验收标准

- [ ] 14 个文件中可替换的内联 `<svg>` 均已改为 `<icon-*>` 组件
- [ ] 保留的 4 处 SVG（双勾、计算器、人物插图）已注释说明保留原因
- [ ] UserManagement 空状态改用 `<a-empty>` 组件
- [ ] 所有替换后图标在亮色/暗色/护眼三个主题下视觉正常
- [ ] 控制台无 Vue 报错

## 自动化状态

fix_status: DONE
fix_commit: fedbd4c8
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-06 15:29

### 本次改动摘要
将 13 个 Vue 文件中约 49 处内联 SVG 图标替换为 Arco Design Vue 的 `<icon-*>` 组件，以减少代码冗余、统一视觉风格、保持主题一致性。

- UserManagement.vue — 替换 plus(×3)、sort-arrow(×2)、info-circle(×1)、close(×2)、circle(×1) 共 10 个 SVG 为 Arco 组件；空状态插图改用 `<a-empty>`
- NotificationPanel.vue — 替换 expand、check-circle、delete、close、system-globe、check、notification/mute 共 9 个 SVG（保留 1 个 mark-all-read 双勾，无 Arco 等效）
- NotificationView.vue — 替换 system-globe、check、record(dot)、reply、close 共 8 个 SVG
- UserDetailView.vue — 替换 close-circle(ban)、info-circle、external-link 共 3 个 SVG
- BottomToolbar.vue — 替换 comment、layout、image、minimap、debug 共 5 个 SVG
- NodeCard.vue — 替换 play、more-dots、expand-chevron 共 3 个 SVG
- DetailSidebar.vue — 替换 toggle-chevron、lock 共 2 个 SVG（保留 calculator SVG，无 Arco 等效）
- KanbanBoardViewImpl.vue — 替换 external-link(×2) 为 icon-launch
- NotificationSubscriptions.vue — 替换 plus、close 共 2 个 SVG
- ProjectNotificationPreferences.vue — 替换 plus、close、chevron-down 共 3 个 SVG（注：chevron-down 改用 icon-down 组件加 class 控制旋转）
- BoardSelector.vue — 替换 dropdown-arrow 1 个 SVG
- DraggableColumnHeader.vue — 替换 trash/delete 1 个 SVG
- ProjectSettingsCustomFields.vue — 替换 drag-handle(6-dot) 1 个 SVG

### 本次变更文件清单
- `trackflow-web/src/views/admin/UserDetailView.vue`
- `trackflow-web/src/views/admin/UserManagement.vue`
- `trackflow-web/src/views/automation/components/BottomToolbar.vue`
- `trackflow-web/src/views/automation/graph/nodes/base/NodeCard.vue`
- `trackflow-web/src/views/board/BoardSelector.vue`
- `trackflow-web/src/views/board/KanbanBoardViewImpl.vue`
- `trackflow-web/src/views/issue/components/DetailSidebar.vue`
- `trackflow-web/src/views/issue/components/DraggableColumnHeader.vue`
- `trackflow-web/src/views/layout/NotificationPanel.vue`
- `trackflow-web/src/views/notification/NotificationView.vue`
- `trackflow-web/src/views/project/settings/ProjectSettingsCustomFields.vue`
- `trackflow-web/src/views/settings/NotificationSubscriptions.vue`
- `trackflow-web/src/views/settings/ProjectNotificationPreferences.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 进入用户管理（/admin/users）→ 确认"新建用户"按钮有 + 图标、表头排序箭头正常显示、空状态显示 Arco Empty 组件
  2. 点击通知面板 → 确认面板头部按钮（展开、仅未读、标记已读、清除、关闭）图标正常显示
  3. 进入通知全页面（/notifications）→ 确认操作按钮（标记已读/未读、回复、删除）图标正常
  4. 进入看板视图 → 确认列头的"在列表中打开"外链图标正常
  5. 进入设置 → 通知订阅/项目偏好 → 确认"新建订阅"、"添加项目"按钮的 + 图标、删除 × 图标正常
- **边界场景**：
  - 暗色/亮色/护眼主题下图标颜色跟随主题变化
- **建议测试账号**：testuser（超级管理员，可访问所有管理页面）
- **注意事项**：这是纯 UI 替换，不影响任何业务逻辑，重点验证图标是否正常渲染

### 审核重点（给 code-review 会话）
- **重点关注文件**：所有 13 个 Vue 文件
- **潜在风险点**：
  - 部分 Arco 图标组件名是否正确（已通过 node_modules d.ts 验证）
  - BoardSelector.vue 中 `icon-down` 替换后 CSS class 绑定是否仍有效
  - UserManagement.vue 中 empty state 改用 `<a-empty>` 后样式是否需要调整
- **已知遗留项**：
  - NotificationPanel 保留 1 个 mark-all-read 双勾 SVG（Arco 无等效图标）
  - DetailSidebar 保留 1 个 calculator SVG（Arco 无等效图标）
  - TimeProgressIndicator.vue 的 SVG 是自定义进度环，非图标，不在此需求范围

======================

## 修复记录

**修复日期**：2026-08-06
**修复人**：AI Agent（auto 模式）

### 根因分析
开发时直接粘贴了 GitHub Octicons/Feather 的 SVG path，未查阅 Arco Design 图标库。项目已全局注册 Arco 图标组件，可直接使用 `<icon-*>` 标签。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `UserManagement.vue` | 替换 10 处 SVG → Arco 图标；空状态改用 `<a-empty>` |
| `NotificationPanel.vue` | 替换 9 处 SVG → Arco 图标；保留 1 处双勾（有注释） |
| `NotificationView.vue` | 替换 8 处 SVG → Arco 图标 |
| `BottomToolbar.vue` | 替换 5 处 SVG → Arco 图标 |
| `UserDetailView.vue` | 替换 3 处 SVG → Arco 图标 |
| `NodeCard.vue` | 替换 3 处 SVG → Arco 图标 |
| `DetailSidebar.vue` | 替换 2 处 SVG → Arco 图标；保留 1 处 calculator（有注释） |
| `KanbanBoardViewImpl.vue` | 替换 2 处外链 SVG → `icon-launch` |
| `NotificationSubscriptions.vue` | 替换 2 处 SVG → Arco 图标 |
| `ProjectNotificationPreferences.vue` | 替换 3 处 SVG → Arco 图标 |
| `BoardSelector.vue` | 替换 1 处下拉箭头 SVG → `icon-down` |
| `DraggableColumnHeader.vue` | 替换 1 处垃圾桶 SVG → `icon-delete` |
| `ProjectSettingsCustomFields.vue` | 替换 1 处拖拽手柄 SVG → `icon-drag-dot-vertical` |

### 影响范围
- 纯前端 UI 替换，无后端改动
- 涉及 layout / notification / admin / automation / board / issue / settings 模块
- 不影响任何业务逻辑或 API 调用
