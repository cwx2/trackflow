# 前端权限控制规范

## 概述

TrackFlow 前端权限分两级：
- **全局权限**：由 `useAuthStore().hasGlobalPermission(perm)` 检查，如 `system:admin`、`project:create`
- **项目级权限**：由 `usePermission(projectIdRef)` composable 检查，如 `issue:edit`、`sprint:create`

## 权限检查方式选择

| 方式 | 适用场景 | 效果 |
|------|----------|------|
| `v-if="canXxx"` + computed | 核心操作按钮、需要完全不渲染的场景 | DOM 不存在 |
| `v-permission="'perm'"` 指令 | 简单全局权限控制、不依赖动态 projectId | display: none |

**项目标准：优先使用 `v-if` + computed**，因为它不渲染 DOM（安全性更好）且支持项目级权限。
`v-permission` 指令仅用于简单的全局权限场景（如隐藏管理操作按钮）。

## 统一 Composable：`usePermission`

路径：`src/composables/usePermission.ts`

```typescript
import { usePermission } from '@/composables/usePermission'

// 传入响应式的 projectId getter
const { 
  canCreateIssue, canEditIssue, canDeleteIssue,
  canAssignIssue, canChangeStatus, canComment,
  canCreateSprint, canEditSprint, canDeleteSprint,
  canManageWorkflow, canManageMembers, canEditProject,
  hasPermission, loading 
} = usePermission(() => selectedProjectId.value)
```

### 特性

- 自动监听 `projectId` 变化并重新加载权限
- 5 分钟客户端缓存（与后端 Redis TTL 对齐）
- 请求去重：同一 projectId 只发一次请求
- 乐观策略：权限加载中返回 `true`（后端兜底），避免 UI 闪烁
- `system:admin` 自动拥有所有权限

## 路由权限守卫

在 `router/index.ts` 中，路由 meta 声明权限需求：

```typescript
{
  path: 'admin',
  name: 'Admin',
  component: () => import('@/views/admin/AdminView.vue'),
  meta: { requiresAdmin: true }  // 需要 system:admin 全局权限
}
```

无权限时自动导航到 `/403` 页面。

## 新增页面权限检查清单

每个新增页面必须检查以下项：

1. **路由 meta**：如果页面仅限特定角色，在路由定义中添加 `meta: { requiresAdmin: true }`
2. **操作按钮**：所有创建/编辑/删除按钮必须受权限控制
3. **表单提交**：编辑操作通过 `readonly` prop 控制
4. **拖拽操作**：看板拖拽等交互必须检查 `canChangeStatus`

## 权限代码速查表

| 权限代码 | 含义 | 类型 |
|----------|------|------|
| `system:admin` | 系统管理员（全能） | 全局 |
| `project:create` | 创建项目 | 全局 |
| `issue:create` | 创建工单 | 项目级 |
| `issue:edit` | 编辑工单 | 项目级 |
| `issue:delete` | 删除工单 | 项目级 |
| `issue:assign` | 分配工单 | 项目级 |
| `issue:change_status` | 变更状态 | 项目级 |
| `issue:comment` | 添加评论 | 项目级 |
| `sprint:create` | 创建迭代 | 项目级 |
| `sprint:edit` | 编辑迭代 | 项目级 |
| `sprint:delete` | 删除迭代 | 项目级 |
| `sprint:view` | 查看迭代 | 项目级 |
| `project:edit` | 编辑项目 | 项目级 |
| `project:manage_members` | 管理成员 | 项目级 |
| `project:manage_workflow` | 管理工作流 | 项目级 |

## 反模式（禁止）

- ❌ 在组件中直接 `authStore.globalPermissions.has(...)` — 应使用 `authStore.hasGlobalPermission(...)`
- ❌ 不做任何权限检查就显示操作按钮
- ❌ 仅在 API 调用时才检查权限（用户点了才报错，体验差）
- ❌ 使用 `v-permission` 做项目级权限检查（指令不支持 projectId）
- ❌ 权限加载完毕前隐藏按钮（造成 UI 闪烁，用乐观策略 + 后端兜底）
