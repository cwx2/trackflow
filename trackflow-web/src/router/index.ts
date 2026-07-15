import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTabStore } from '@/stores/tabs'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue')
  },
  {
    path: '/auth/callback',
    name: 'AuthCallback',
    component: () => import('@/views/login/AuthCallback.vue')
  },
  {
    path: '/',
    component: () => import('@/views/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '403',
        name: 'Forbidden',
        component: () => import('@/views/error/ForbiddenView.vue')
      },
      {
        path: '',
        name: 'Issues',
        component: () => import('@/views/issue/IssueListView.vue')
      },
      {
        path: 'issues/create',
        name: 'IssueCreate',
        component: () => import('@/views/issue/IssueCreateView.vue'),
        meta: { requiresCreateIssue: true }
      },
      {
        path: 'issues/:id',
        name: 'IssueDetail',
        component: () => import('@/views/issue/IssueDetailView.vue')
      },
      {
        path: 'trash',
        name: 'Trash',
        component: () => import('@/views/issue/IssueTrashView.vue')
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/project/ProjectListView.vue')
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetailView.vue')
      },
      {
        path: 'projects/:id/settings',
        name: 'ProjectSettings',
        component: () => import('@/views/project/settings/ProjectSettingsView.vue'),
        meta: { requiresProjectEdit: true }
      },
      {
        path: 'boards',
        name: 'Boards',
        component: () => import('@/views/board/KanbanBoardView.vue')
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue')
      },
      {
        path: 'sprints',
        name: 'Sprints',
        component: () => import('@/views/sprint/SprintView.vue')
      },
      {
        path: 'workflow',
        name: 'Workflow',
        component: () => import('@/views/admin/WorkflowEditor.vue'),
        meta: { requiresWorkflow: true }
      },
      {
        path: 'timesheets',
        name: 'Timesheets',
        component: () => import('@/views/timesheet/TimesheetView.vue')
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/report/ReportDashboardView.vue'),
        meta: { requiresReport: true }
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/admin/AdminView.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/workflow',
        name: 'WorkflowEditor',
        redirect: '/workflow',
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/users',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/roles',
        name: 'RoleManagement',
        component: () => import('@/views/admin/RoleManagement.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/organizations',
        name: 'OrgManagement',
        component: () => import('@/views/admin/OrgManagement.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/custom-fields',
        name: 'CustomFieldManagement',
        component: () => import('@/views/admin/CustomFieldManage.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'admin/audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/admin/AuditLogView.vue'),
        meta: { requiresAdmin: true }
      },
      {
        path: 'settings/profile',
        name: 'Profile',
        component: () => import('@/views/settings/ProfileView.vue')
      },
      {
        path: 'settings/notifications',
        name: 'NotificationSettings',
        component: () => import('@/views/settings/NotificationSettingsView.vue')
      },
      {
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/error/NotFoundView.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：未认证跳转登录 + 全局权限加载 + 管理路由权限 + 标签管理
router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login' })
    return
  }

  // 已认证用户：确保全局权限已加载
  if (authStore.isAuthenticated && !authStore.permissionsLoaded) {
    await authStore.loadGlobalPermissions()
  }

  // 如果权限加载失败（permissionsLoaded 仍为 false），对于需要权限检查的路由允许通过
  // 依赖后端 API 的 @PreAuthorize 做最终权限校验（前端仅作为 UX 优化）
  const permissionCheckAvailable = authStore.permissionsLoaded

  // 管理路由权限检查
  if (to.meta.requiresAdmin && permissionCheckAvailable) {
    if (!authStore.hasGlobalPermission('system:admin')) {
      next({ name: 'Forbidden' })
      return
    }
  }

  // 工作流路由权限检查：system:admin 或在任意项目中有 project:manage_workflow
  if (to.meta.requiresWorkflow && permissionCheckAvailable) {
    const canAccess = authStore.hasGlobalPermission('system:admin') || authStore.hasGlobalPermission('nav:workflow')
    if (!canAccess) {
      next({ name: 'Forbidden' })
      return
    }
  }

  // 创建工单路由权限检查：统一使用 authStore.canCreateIssue
  if (to.meta.requiresCreateIssue && permissionCheckAvailable) {
    if (!authStore.canCreateIssue) {
      next({ name: 'Forbidden' })
      return
    }
  }

  // 报表路由权限检查：system:admin 或在任意项目中有 report:view
  if (to.meta.requiresReport && permissionCheckAvailable) {
    const canAccess = authStore.hasGlobalPermission('system:admin') || authStore.hasGlobalPermission('nav:report')
    if (!canAccess) {
      next({ name: 'Forbidden' })
      return
    }
  }

  // 标签管理：打开 Issue 详情时自动创建标签
  const tabStore = useTabStore()
  if (to.name === 'IssueDetail' && to.params.id) {
    tabStore.openTab({
      id: `issue-${to.params.id}`,
      title: `Issue #${to.params.id}`,
      path: to.fullPath,
      closable: true,
      issueId: String(to.params.id)
    })
  }

  next()
})

export default router
