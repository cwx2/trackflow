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
        redirect: '/issues'
      },
      {
        path: 'issues',
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
        path: 'projects/:projectKey',
        name: 'ProjectDetail',
        component: () => import('@/views/project/ProjectDetailView.vue')
      },
      {
        path: 'projects/:projectKey/settings',
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
        component: () => import('@/views/report/dashboard/CustomDashboardView.vue')
      },
      {
        path: 'sprints',
        name: 'Sprints',
        component: () => import('@/views/sprint/SprintView.vue')
      },
      {
        path: 'sprint-planning',
        name: 'SprintPlanning',
        component: () => import('@/views/sprint/SprintPlanningView.vue'),
        meta: { requiresSprintManage: true }
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
        component: () => import('@/views/report/ReportContainerView.vue'),
        meta: { requiresReport: true },
        children: [
          {
            path: '',
            name: 'ReportOverview',
            component: () => import('@/views/report/ReportDashboardView.vue')
          },
          {
            path: 'list',
            name: 'ReportList',
            component: () => import('@/views/report/ReportListView.vue')
          },
          {
            path: 'time',
            name: 'TimeReport',
            component: () => import('@/views/report/TimeReportPage.vue')
          },
          {
            path: 'estimation',
            name: 'EstimationReport',
            component: () => import('@/views/report/EstimationReportPage.vue')
          },
          {
            path: 'dashboards',
            redirect: '/dashboard'
          },
          {
            path: ':id',
            name: 'ReportDetail',
            component: () => import('@/views/report/ReportDetailView.vue')
          }
        ]
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
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/workflow-definitions',
        name: 'WorkflowDefinitions',
        component: () => import('@/views/admin/WorkflowDefinitionView.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/users',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_users' }
      },
      {
        path: 'admin/users/:id',
        name: 'UserDetail',
        component: () => import('@/views/admin/UserDetailView.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_users' }
      },
      {
        path: 'admin/roles',
        name: 'RoleManagement',
        component: () => import('@/views/admin/RoleManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/organizations',
        name: 'OrgManagement',
        component: () => import('@/views/admin/OrgManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_orgs' }
      },
      {
        path: 'admin/groups',
        name: 'GroupManagement',
        component: () => import('@/views/admin/GroupManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_groups' }
      },
      {
        path: 'admin/custom-fields',
        name: 'CustomFieldManagement',
        component: () => import('@/views/admin/CustomFieldManage.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/admin/AuditLogView.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_users' }
      },
      {
        path: 'admin/time-tracking',
        name: 'TimeTrackingSettings',
        component: () => import('@/views/admin/TimeTrackingSettings.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/work-item-attributes',
        name: 'WorkItemAttributes',
        component: () => import('@/views/admin/WorkItemAttributesView.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/notifications',
        name: 'NotificationManagement',
        component: () => import('@/views/admin/NotificationManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/rules',
        name: 'RuleManagement',
        component: () => import('@/views/admin/RuleManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/webhooks',
        name: 'WebhookManagement',
        component: () => import('@/views/admin/WebhookManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/action-rules',
        name: 'ActionRuleManagement',
        component: () => import('@/views/admin/ActionRuleManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_settings' }
      },
      {
        path: 'admin/integrations',
        name: 'IntegrationManagement',
        component: () => import('@/views/admin/IntegrationManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_roles' }
      },
      {
        path: 'admin/link-types',
        name: 'LinkTypeManagement',
        component: () => import('@/views/admin/LinkTypeManagement.vue'),
        meta: { requiresAdmin: true, requiredPermission: 'system:manage_settings' }
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notification/NotificationView.vue')
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
        path: 'settings/security',
        name: 'AccountSecurity',
        component: () => import('@/views/settings/AccountSecurityView.vue')
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

  // 管理路由权限检查：支持细粒度权限
  // - 有 requiredPermission → 检查该具体权限（system:manage_users 等）
  // - 无 requiredPermission（Admin 入口页）→ 有任一 system:manage_* 即可访问
  if (to.meta.requiresAdmin && permissionCheckAvailable) {
    const requiredPerm = to.meta.requiredPermission as string | undefined
    if (requiredPerm) {
      // 子页面：检查具体细粒度权限
      if (!authStore.hasGlobalPermission(requiredPerm)) {
        next({ name: 'Forbidden' })
        return
      }
    } else {
      // 入口页：有任一管理权限即可进入
      const hasAnyAdminPerm = authStore.hasGlobalPermission('system:manage_users')
        || authStore.hasGlobalPermission('system:manage_roles')
        || authStore.hasGlobalPermission('system:manage_orgs')
        || authStore.hasGlobalPermission('system:manage_groups')
      if (!hasAnyAdminPerm) {
        next({ name: 'Forbidden' })
        return
      }
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

  // Sprint 规划路由权限检查：system:admin 或在任意项目中有 sprint:create/sprint:edit
  if (to.meta.requiresSprintManage && permissionCheckAvailable) {
    const canAccess = authStore.hasGlobalPermission('system:admin') || authStore.hasGlobalPermission('nav:sprint_manage')
    if (!canAccess) {
      next({ name: 'Forbidden' })
      return
    }
  }

  // 标签管理：打开 Issue 详情时自动创建标签
  const tabStore = useTabStore()
  if (to.name === 'IssueDetail' && to.params.id) {
    const paramId = String(to.params.id)
    // 如果参数包含连字符（如 DE4-1473），说明是 issue key，直接用作标题
    // 否则是数据库 ID，先用占位标题，后续由 IssueDetailView 加载后更新
    const isKey = paramId.includes('-') && !/^\d+$/.test(paramId)
    tabStore.openTab({
      id: `issue-${paramId}`,
      title: isKey ? paramId : '加载中...',
      path: to.fullPath,
      closable: true,
      issueId: paramId
    })
  }

  next()
})

export default router
