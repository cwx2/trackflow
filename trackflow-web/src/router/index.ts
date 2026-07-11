import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
        path: '',
        name: 'Issues',
        component: () => import('@/views/issue/IssueListView.vue')
      },
      {
        path: 'issues/create',
        name: 'IssueCreate',
        component: () => import('@/views/issue/IssueCreateView.vue')
      },
      {
        path: 'issues/:id',
        name: 'IssueDetail',
        component: () => import('@/views/issue/IssueDetailView.vue')
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/project/ProjectListView.vue')
      },
      {
        path: 'boards',
        name: 'Boards',
        component: () => import('@/views/dashboard/DashboardView.vue')
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
        path: 'admin/workflow',
        name: 'WorkflowEditor',
        component: () => import('@/views/admin/WorkflowEditor.vue')
      },
      {
        path: 'admin/users',
        name: 'UserManagement',
        component: () => import('@/views/admin/UserManagement.vue')
      },
      {
        path: 'admin/roles',
        name: 'RoleManagement',
        component: () => import('@/views/admin/RoleManagement.vue')
      },
      {
        path: 'admin/organizations',
        name: 'OrgManagement',
        component: () => import('@/views/admin/OrgManagement.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫：未认证跳转登录
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
