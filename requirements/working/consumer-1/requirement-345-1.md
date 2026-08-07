# REQ-345-1：提取 `UserAvatar` 通用用户头像组件，统一全系统头像显示

## 基本信息

| 字段 | 内容 |
|------|------|
| 编号 | REQ-345-1 |
| 父需求 | REQ-345 |
| 标题 | 提取 `UserAvatar` 通用用户头像组件，统一全系统头像显示 |
| 类型 | 组件开发 + 重构 |
| 优先级 | P1 |
| 依赖 | 无 |
| 状态 | 待开发 |

---

## 自动化状态

fix_status: DONE
fix_commit: 1ae5890
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

---

## 问题现状

用户头像（首字母 + 彩色背景）的实现在 **15 个文件** 中各自重复，且互不一致：

| 文件 | 问题 |
|------|------|
| `ActivityStream.vue` | `initial()` + `avatarBg()` 函数，有哈希颜色算法 |
| `UserManagement.vue` | `getInitial()` + `getAvatarColor()`，另一套实现 |
| `RoleManagement.vue` | `charAt(0)`，颜色写死 `var(--accent-blue)` |
| `GroupManagement.vue` | `(displayName \|\| username).charAt(0)`，无颜色哈希 |
| `ProjectTeamWidget.vue` | 又一个独立 `getInitial()`，无颜色 |
| `SprintAssigneeDistribution.vue` | 又一个独立 `getInitial()` |
| `UserHoverCard.vue` | `initial()` + `avatarBg()`，与 ActivityStream 相同但独立维护 |
| `UserDetailView.vue` | 手写 div + CSS |
| `AppLayout.vue` | `userInitial` computed，两种尺寸 `.user-avatar-sm` / `.user-avatar-lg` |
| `UserProfileView.vue` | `avatarBg` computed，单独计算 |
| `TimesheetView.vue` | `.user-avatar-dot` 仅一个小圆点 |
| 其他 4 处 | 各自实现 |

**问题**：同一个用户在不同页面显示的头像颜色不同；有的只取第一个字符、有的取中文名、有的有 tooltip、有的没有。

---

## 组件设计

### 文件位置
`src/components/base/UserAvatar.vue`

### Props

```typescript
interface UserAvatarProps {
  /** 用户显示名（必须，用于生成首字母和颜色） */
  name: string
  /** 头像图片 URL（可选，有时显示图片，无时显示文字头像） */
  avatar?: string
  /** 头像尺寸（px），默认 28 */
  size?: number
  /** 是否在 hover 时显示用户名 tooltip，默认 false */
  showTooltip?: boolean
  /** 自定义颜色（可选，不传则自动哈希计算） */
  color?: string
  /** 形状，circle 圆形（默认）| square 方形 */
  shape?: 'circle' | 'square'
}
```

### 统一算法规范

```typescript
// 首字母取法（统一规则）
function getInitial(name: string): string {
  if (!name) return '?'
  // 中文：取第一个汉字
  const cjk = name.match(/[\u4e00-\u9fff]/)
  if (cjk) return cjk[0]
  // 英文：取第一个大写字母
  return name.charAt(0).toUpperCase()
}

// 颜色哈希（统一规则，保证同名用户全局颜色一致）
const PALETTE = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6',
                 '#06b6d4','#84cc16','#f97316','#ec4899','#6366f1']
function getAvatarColor(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return PALETTE[Math.abs(hash) % PALETTE.length]
}
```

### 使用示例

```vue
<!-- 最简用法 -->
<UserAvatar :name="user.displayName" />

<!-- 带头像图片 + tooltip -->
<UserAvatar
  :name="user.displayName"
  :avatar="user.avatarUrl"
  :show-tooltip="true"
  :size="32"
/>

<!-- 小尺寸（侧边栏用） -->
<UserAvatar :name="userInitial" :size="20" />
```

---

## 迁移范围

以下文件需要用 `UserAvatar` 替换现有头像实现：

1. `ActivityStream.vue` — 评论/活动条目头像
2. `UserManagement.vue` — 用户列表头像
3. `RoleManagement.vue` — 角色成员列表头像
4. `GroupManagement.vue` — 用户组成员头像
5. `ProjectTeamWidget.vue` — 项目团队 Widget 头像
6. `SprintAssigneeDistribution.vue` — Sprint 负责人分布头像
7. `UserHoverCard.vue` — 悬浮卡片头像
8. `UserDetailView.vue` — 用户详情页头像
9. `AppLayout.vue` — 顶部导航用户头像（两种尺寸）
10. `UserProfileView.vue` — 个人资料页头像
11. `AuditLogView.vue`（如有头像）
12. `OrgDetailView.vue`（如有成员头像）

迁移同时删除各文件中的 `getInitial`、`getAvatarColor`、`avatarBg`、`initial`、`.user-avatar`、`.user-avatar-sm`、`.user-avatar-lg` 等重复的函数和 CSS。

---

## 验收标准

1. 组件文件创建于 `src/components/base/UserAvatar.vue`，并在 `src/components/base/index.ts` 中导出
2. **颜色一致性**：同一个用户名在所有页面显示的头像颜色完全相同
3. **首字母规则**：中文名取第一个汉字，英文名取首字母大写
4. **图片降级**：`avatar` prop 传入且图片加载成功时显示图片；图片加载失败或未传时回退到文字头像
5. **Tooltip**：`showTooltip=true` 时 hover 显示用户全名
6. **尺寸**：`size` prop 控制宽高和字体大小，默认 28px
7. 以上 12 个文件完成迁移，TypeScript 编译无错误
8. 各文件中的重复函数（`getInitial` 等）和重复 CSS（`.user-avatar` 等）已删除


======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-07 16:35

### 本次改动摘要
- 新建 `src/components/base/UserAvatar.vue` — 统一用户头像组件，支持 name/avatar/size/showTooltip/color/shape props，内置 CJK 首字符提取和稳定颜色哈希算法
- 新建 `src/components/base/index.ts` — base 组件桶导出（DataContainer、DeleteConfirmButton、UserAvatar）
- 迁移 10 个文件，删除各自重复的 `getInitial`/`getAvatarColor`/`avatarBg`/`initial` 函数和 `.user-avatar`/`.member-avatar`/`.avatar` 等 CSS

### 本次变更文件清单
- `trackflow-web/src/components/base/UserAvatar.vue` (新建)
- `trackflow-web/src/components/base/index.ts` (新建)
- `trackflow-web/src/views/admin/GroupManagement.vue`
- `trackflow-web/src/views/admin/RoleManagement.vue`
- `trackflow-web/src/views/admin/UserDetailView.vue`
- `trackflow-web/src/views/admin/UserManagement.vue`
- `trackflow-web/src/views/issue/components/ActivityStream.vue`
- `trackflow-web/src/views/issue/components/UserHoverCard.vue`
- `trackflow-web/src/views/layout/AppLayout.vue`
- `trackflow-web/src/views/report/dashboard/widgets/ProjectTeamWidget.vue`
- `trackflow-web/src/views/sprint/SprintAssigneeDistribution.vue`
- `trackflow-web/src/views/timesheet/TimesheetView.vue`
- `trackflow-web/src/views/users/UserProfileView.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开侧边栏 → 确认左下角用户头像显示首字母+彩色背景
  2. 打开用户菜单（点击侧边栏用户区域）→ 确认弹出菜单中有头像
  3. 导航到 /admin/users → 确认用户列表每行有彩色头像
  4. 导航到 /admin/roles → 点击"查看用户"→ 确认弹窗中用户有彩色头像
  5. 打开任一 Issue 详情页 → 确认活动流中评论/变更条目有头像
  6. Hover 评论区用户名 → 确认 UserHoverCard 弹出有大尺寸头像
- **颜色一致性验证**：同一个用户名在不同页面（如侧边栏 vs 用户管理列表 vs 活动流）显示相同颜色
- **边界场景**：
  - 无 displayName 的用户应回退到 username 的首字母
  - 中文名用户（如"赵静"）应显示第一个汉字"赵"
- **建议测试账号**：testuser（超级管理员，能访问所有页面）
- **注意事项**：本次未修改 TimesheetView 中的 "user-avatar-dot"（已替换为 UserAvatar 12px），验证工时表页面头像正常

### 审核重点（给 code-review 会话）
- **重点关注文件**：UserAvatar.vue（核心组件，算法正确性）、index.ts（导出完整性）
- **潜在风险点**：颜色哈希算法与原有多种算法不同，旧实现有的用 charCodeAt(0)%7，有的用完整哈希 — 迁移后颜色会统一为新算法，与之前不完全一致（预期行为，这是需求要求的"统一"）
- **已知遗留项**：OrgDetailView 和 AuditLogView 没有头像代码，不需要迁移

======================

## 修复记录

**修复日期**：2026-08-07
**修复人**：AI Agent（auto 模式）

### 根因分析
用户头像（首字母 + 彩色背景）的实现分散在 15 个文件中（实际有头像代码的 10 个），每个文件独立维护不同的首字母提取逻辑和颜色算法，导致同一用户在不同页面显示不同颜色。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `components/base/UserAvatar.vue` | 新建统一头像组件：CJK 首字提取、10 色稳定哈希、img 降级、tooltip、size/shape props |
| `components/base/index.ts` | 新建桶导出文件 |
| `ActivityStream.vue` | 替换 avatar div + avatarBg/initial 函数为 UserAvatar |
| `UserManagement.vue` | 替换 user-avatar span + getInitial/getAvatarColor 为 UserAvatar |
| `RoleManagement.vue` | 替换固定蓝色 user-avatar div 为 UserAvatar（2 处） |
| `GroupManagement.vue` | 替换 member-avatar/user-avatar-sm 为 UserAvatar（2 处） |
| `ProjectTeamWidget.vue` | 替换 member-avatar + getAvatarColor/getInitial 为 UserAvatar |
| `SprintAssigneeDistribution.vue` | 替换 assignee-avatar + getInitial 为 UserAvatar |
| `UserHoverCard.vue` | 替换 card-avatar + avatarBg/initial 为 UserAvatar |
| `UserDetailView.vue` | 替换 user-avatar div 为 UserAvatar size=56 |
| `AppLayout.vue` | 替换 user-avatar-sm/lg + userInitial computed 为 UserAvatar size=24/32 |
| `UserProfileView.vue` | 替换 profile-avatar + avatarBg/userInitial 为 UserAvatar size=56 with avatar prop |
| `TimesheetView.vue` | 替换 user-option-avatar/member-avatar/author-avatar/user-avatar-dot 为 UserAvatar |

### 影响范围
- 所有包含用户头像的页面：侧边栏、用户管理、角色管理、组管理、活动流、HoverCard、用户详情、个人资料、Sprint 分布、项目团队 Widget、工时表
