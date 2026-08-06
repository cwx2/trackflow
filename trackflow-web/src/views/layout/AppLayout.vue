<template>
  <div class="app-layout">
    <!-- 全局服务状态横幅 -->
    <ServiceStatusBanner />

    <!-- 左侧导航栏（全高） -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <!-- 折叠切换按钮 -->
      <button class="sidebar-toggle" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="toggleSidebar">
        <icon-left v-if="!sidebarCollapsed" />
        <icon-right v-else />
      </button>

      <div class="sidebar-logo" @click="$router.push('/')">
        <img v-if="!sidebarCollapsed" class="logo-image" :src="trackflowLogoUrl" alt="TrackFlow" />
        <img v-else class="logo-icon" :src="trackflowIconUrl" alt="TrackFlow" />
      </div>

      <nav class="sidebar-nav">
        <a-tooltip v-if="sidebarCollapsed" content="仪表盘" position="right" :mini="true">
          <router-link to="/dashboard" class="nav-item" :class="{ active: $route.path === '/dashboard' }">
            <icon-dashboard class="nav-icon" />
            <span class="nav-label">仪表盘</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/dashboard" class="nav-item" :class="{ active: $route.path === '/dashboard' }">
          <icon-dashboard class="nav-icon" />
          <span class="nav-label">仪表盘</span>
        </router-link>

        <a-tooltip v-if="sidebarCollapsed" content="问题" position="right" :mini="true">
          <router-link to="/issues" class="nav-item" :class="{ active: $route.name === 'Issues' }">
            <icon-unordered-list class="nav-icon" />
            <span class="nav-label">问题</span>
            <span v-if="issueBadgeCount > 0" class="nav-badge">{{ issueBadgeCount > 99 ? '99+' : issueBadgeCount }}</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/issues" class="nav-item" :class="{ active: $route.name === 'Issues' }">
          <icon-unordered-list class="nav-icon" />
          <span class="nav-label">问题</span>
          <span v-if="issueBadgeCount > 0" class="nav-badge" :title="`${issueBadgeCount} 个待测试工单`">
            {{ issueBadgeCount > 99 ? '99+' : issueBadgeCount }}
          </span>
        </router-link>

        <a-tooltip v-if="sidebarCollapsed" content="项目" position="right" :mini="true">
          <router-link to="/projects" class="nav-item" :class="{ active: $route.name === 'Projects' }">
            <icon-layers class="nav-icon" /><span class="nav-label">项目</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/projects" class="nav-item" :class="{ active: $route.name === 'Projects' }">
          <icon-layers class="nav-icon" /><span class="nav-label">项目</span>
        </router-link>

        <a-tooltip v-if="sidebarCollapsed" content="看板" position="right" :mini="true">
          <router-link to="/boards" class="nav-item" :class="{ active: $route.name === 'Boards' }">
            <icon-apps class="nav-icon" /><span class="nav-label">看板</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/boards" class="nav-item" :class="{ active: $route.name === 'Boards' }">
          <icon-apps class="nav-icon" /><span class="nav-label">看板</span>
        </router-link>

        <a-tooltip v-if="sidebarCollapsed" content="迭代" position="right" :mini="true">
          <router-link to="/sprints" class="nav-item" :class="{ active: $route.name === 'Sprints' }">
            <icon-thunderbolt class="nav-icon" /><span class="nav-label">迭代</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/sprints" class="nav-item" :class="{ active: $route.name === 'Sprints' }">
          <icon-thunderbolt class="nav-icon" /><span class="nav-label">迭代</span>
        </router-link>

        <template v-if="canViewSprintPlanning">
          <a-tooltip v-if="sidebarCollapsed" content="规划" position="right" :mini="true">
            <router-link to="/sprint-planning" class="nav-item" :class="{ active: $route.name === 'SprintPlanning' }">
              <icon-calendar class="nav-icon" /><span class="nav-label">规划</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/sprint-planning" class="nav-item" :class="{ active: $route.name === 'SprintPlanning' }">
            <icon-calendar class="nav-icon" /><span class="nav-label">规划</span>
          </router-link>
        </template>

        <a-tooltip v-if="sidebarCollapsed" content="时间表" position="right" :mini="true">
          <router-link to="/timesheets" class="nav-item" :class="{ active: $route.name === 'Timesheets' }">
            <icon-clock-circle class="nav-icon" /><span class="nav-label">时间表</span>
          </router-link>
        </a-tooltip>
        <router-link v-else to="/timesheets" class="nav-item" :class="{ active: $route.name === 'Timesheets' }">
          <icon-clock-circle class="nav-icon" /><span class="nav-label">时间表</span>
        </router-link>

        <template v-if="canViewReport">
          <a-tooltip v-if="sidebarCollapsed" content="报表" position="right" :mini="true">
            <router-link to="/reports" class="nav-item" :class="{ active: $route.path.startsWith('/reports') }">
              <icon-bar-chart class="nav-icon" /><span class="nav-label">报表</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/reports" class="nav-item" :class="{ active: $route.path.startsWith('/reports') }">
            <icon-bar-chart class="nav-icon" /><span class="nav-label">报表</span>
          </router-link>
        </template>

        <template v-if="isAdmin">
          <a-tooltip v-if="sidebarCollapsed" content="自动化" position="right" :mini="true">
            <router-link to="/automation" class="nav-item" :class="{ active: $route.path.startsWith('/automation') }">
              <icon-robot class="nav-icon" /><span class="nav-label">自动化</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/automation" class="nav-item" :class="{ active: $route.path.startsWith('/automation') }">
            <icon-robot class="nav-icon" /><span class="nav-label">自动化</span>
          </router-link>
        </template>

        <template v-if="canManageWorkflow">
          <a-tooltip v-if="sidebarCollapsed" content="工作流" position="right" :mini="true">
            <router-link to="/workflow" class="nav-item" :class="{ active: isWorkflowRoute }">
              <icon-share-alt class="nav-icon" /><span class="nav-label">工作流</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/workflow" class="nav-item" :class="{ active: isWorkflowRoute }">
            <icon-share-alt class="nav-icon" /><span class="nav-label">工作流</span>
          </router-link>
        </template>

        <template v-if="isAdmin">
          <a-tooltip v-if="sidebarCollapsed" content="管理" position="right" :mini="true">
            <router-link to="/admin" class="nav-item" :class="{ active: isAdminRoute }">
              <icon-settings class="nav-icon" /><span class="nav-label">管理</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/admin" class="nav-item" :class="{ active: isAdminRoute }">
            <icon-settings class="nav-icon" /><span class="nav-label">管理</span>
          </router-link>
        </template>

        <template v-if="canViewTrash">
          <a-tooltip v-if="sidebarCollapsed" content="回收站" position="right" :mini="true">
            <router-link to="/trash" class="nav-item" :class="{ active: $route.name === 'Trash' }">
              <icon-delete class="nav-icon" /><span class="nav-label">回收站</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/trash" class="nav-item" :class="{ active: $route.name === 'Trash' }">
            <icon-delete class="nav-icon" /><span class="nav-label">回收站</span>
          </router-link>
        </template>
      </nav>

      <div class="sidebar-footer">
        <template v-if="canCreateIssue">
          <a-tooltip v-if="sidebarCollapsed" content="创建" position="right" :mini="true">
            <router-link to="/issues/create" class="footer-item">
              <icon-plus-circle class="nav-icon" /><span class="nav-label">创建</span>
            </router-link>
          </a-tooltip>
          <router-link v-else to="/issues/create" class="footer-item">
            <icon-plus-circle class="nav-icon" /><span class="nav-label">创建</span>
          </router-link>
        </template>

        <!-- 通知铃铛 -->
        <a-tooltip v-if="sidebarCollapsed" content="通知" position="right" :mini="true">
          <div class="footer-item notification-trigger" @click="toggleNotificationPanel">
            <span class="nav-icon notification-icon-wrap">
              <icon-notification />
              <span v-if="hasUnread" class="notification-badge">
                {{ unreadCount > 99 ? '99+' : unreadCount }}
              </span>
            </span>
            <span class="nav-label">通知</span>
          </div>
        </a-tooltip>
        <div v-else class="footer-item notification-trigger" @click="toggleNotificationPanel">
          <span class="nav-icon notification-icon-wrap">
            <icon-notification />
            <span v-if="hasUnread" class="notification-badge">
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </span>
          <span class="nav-label">通知</span>
        </div>

        <!-- 计时器 badge -->
        <div
          v-if="timerStore.isRunning"
          class="footer-item timer-badge"
          :title="`${timerStore.issueKey} ${timerStore.issueTitle} — ${timerStore.elapsedDisplay}`"
          @click="showTimerPopover = !showTimerPopover"
        >
          <span class="nav-icon timer-icon-wrap">
            <icon-clock-circle />
            <span class="timer-pulse"></span>
          </span>
          <span class="nav-label timer-badge-label">
            <span class="timer-badge-key">{{ timerStore.issueKey }}</span>
            <span class="timer-elapsed">{{ timerStore.elapsedDisplay }}</span>
          </span>
        </div>
        <!-- 计时器快捷面板 -->
        <div v-if="showTimerPopover && timerStore.isRunning" class="timer-popover">
          <div class="timer-popover-header">
            <icon-clock-circle class="timer-popover-icon" />
            <span class="timer-popover-title">正在计时</span>
          </div>
          <div class="timer-popover-body">
            <div class="timer-issue" @click="goToTimerIssue">
              <span class="timer-issue-key">{{ timerStore.issueKey }}</span>
              <span class="timer-issue-title">{{ timerStore.issueTitle }}</span>
            </div>
            <div class="timer-elapsed-large">{{ timerStore.elapsedDisplay }}</div>
          </div>
          <div class="timer-popover-footer">
            <button class="timer-stop-btn" :disabled="timerStore.loading" @click="handleStopTimer">
              <icon-minus-circle /> 停止计时
            </button>
          </div>
        </div>

        <!-- 主题切换 -->
        <a-tooltip v-if="sidebarCollapsed" :content="themeLabel" position="right" :mini="true">
          <div class="footer-item theme-switcher" @click="cycleTheme">
            <component :is="themeIconComponent" class="nav-icon" />
            <span class="nav-label">{{ themeLabel }}</span>
          </div>
        </a-tooltip>
        <div v-else class="footer-item theme-switcher" @click="cycleTheme">
          <component :is="themeIconComponent" class="nav-icon" />
          <span class="nav-label">{{ themeLabel }}</span>
        </div>

        <a-tooltip v-if="sidebarCollapsed" :content="userName" position="right" :mini="true">
          <div class="sidebar-user" @click="showUserMenu = !showUserMenu">
            <div class="user-avatar-sm">{{ userInitial }}</div>
            <span class="user-name">{{ userName }}</span>
          </div>
        </a-tooltip>
        <div v-else class="sidebar-user" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar-sm">{{ userInitial }}</div>
          <span class="user-name">{{ userName }}</span>
        </div>
        <div v-if="showUserMenu" class="user-menu">
          <div class="user-menu-header">
            <div class="user-avatar-lg">{{ userInitial }}</div>
            <div class="user-menu-info">
              <span class="user-menu-name">{{ userName }}</span>
              <span class="user-menu-email">{{ userEmail }}</span>
            </div>
          </div>
          <div class="user-menu-divider"></div>
          <div class="user-menu-item" @click="goProfile">
            <icon-user class="menu-item-icon" />
            <span>个人资料</span>
          </div>
          <div class="user-menu-item" @click="goNotifications">
            <icon-notification class="menu-item-icon" />
            <span>通知偏好</span>
          </div>
          <div class="user-menu-divider"></div>
          <div class="user-menu-item danger" @click="handleLogout">
            <icon-export class="menu-item-icon" />
            <span>退出登录</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧（标签栏 + 内容区） -->
    <div class="main-area">
      <TabBar v-if="showTabBar" />
      <div class="main-content">
        <router-view />
      </div>
    </div>

    <!-- 通知面板 -->
    <NotificationPanel />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTimerStore } from '@/stores/timer'
import { useTheme } from '@/composables/useTheme'
import { useNavBadge } from '@/composables/useNavBadge'
import { useNotification } from '@/composables/useNotification'
import { IconMoon, IconSun, IconCommon, IconLeft, IconRight } from '@arco-design/web-vue/es/icon'
import TabBar from './TabBar.vue'
import NotificationPanel from './NotificationPanel.vue'
import ServiceStatusBanner from './ServiceStatusBanner.vue'
import trackflowLogoUrl from '@/assets/trackflow-watermark.svg'
import trackflowIconUrl from '@/assets/trackflow-icon.svg'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const timerStore = useTimerStore()
const { issueBadgeCount, init: initNavBadge } = useNavBadge()
const { unreadCount, hasUnread, togglePanel: toggleNotificationPanel, init: initNotification } = useNotification()

// 侧边栏折叠状态（持久化）
const SIDEBAR_KEY = 'tf_sidebar_collapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === 'true')
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, String(sidebarCollapsed.value))
  // 同步更新 CSS 变量，确保通知面板等依赖此变量的组件跟随移动
  document.documentElement.style.setProperty(
    '--tf-sidebar-width',
    sidebarCollapsed.value ? '56px' : '200px'
  )
}

// TabBar 仅在 Issue 相关路由显示（Issue 列表、Issue 详情）
const showTabBar = computed(() => {
  const name = route.name
  return name === 'Issues' || name === 'IssueDetail'
})
const { theme, cycleTheme } = useTheme()
const showUserMenu = ref(false)
const showTimerPopover = ref(false)

const isAdmin = computed(() => {
  // 主判断：后端 API 返回的全局权限（精确）— 支持细粒度管理权限
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('system:manage_users')
      || authStore.hasGlobalPermission('system:manage_roles')
      || authStore.hasGlobalPermission('system:manage_orgs')
  }
  // 辅助判断：权限未加载时用 Keycloak Token 中的 realm role 做快速前置判断
  const roles: string[] = authStore.user?.roles || []
  return roles.includes('tf_admin')
})

const canManageWorkflow = computed(() => {
  // system:admin 自动拥有所有权限
  if (isAdmin.value) return true
  // 后端在 my-global-permissions 中返回 nav:workflow 表示用户在任意项目中有 project:manage_workflow
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:workflow')
  }
  // 权限未加载时乐观显示，路由守卫 + 后端 @PreAuthorize 做最终拦截
  return true
})

const canViewReport = computed(() => {
  // system:admin 自动拥有所有权限
  if (isAdmin.value) return true
  // 后端在 my-global-permissions 中返回 nav:report 表示用户在任意项目中有 report:view
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:report')
  }
  // 权限未加载时乐观显示，确保所有页面导航一致（包括 403/404 错误页）
  // 路由守卫 + 后端 @PreAuthorize 做最终权限拦截
  return true
})

const canViewTrash = computed(() => {
  // system:admin 自动拥有所有权限
  if (isAdmin.value) return true
  // 后端在 my-global-permissions 中返回 nav:trash 表示用户在任意项目中有 issue:delete
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:trash')
  }
  // 权限未加载时乐观显示
  return true
})

const canViewSprintPlanning = computed(() => {
  // system:admin 自动拥有所有权限
  if (isAdmin.value) return true
  // 后端在 my-global-permissions 中返回 nav:sprint_manage 表示用户在任意项目中有 sprint:create
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('nav:sprint_manage')
  }
  // 权限未加载时乐观显示
  return true
})

const canCreateIssue = computed(() => {
  // system:admin 自动拥有所有权限
  if (isAdmin.value) return true
  // 权限已加载时精确判断
  if (authStore.permissionsLoaded) {
    return authStore.canCreateIssue
  }
  // 权限未加载时乐观显示，确保所有页面导航一致（包括 403/404 错误页）
  // 点击后路由守卫会等待权限加载完成再做拦截
  return true
})

const isAdminRoute = computed(() => route.path.startsWith('/admin') && !route.path.startsWith('/admin/workflow'))

const isWorkflowRoute = computed(() => route.path.startsWith('/workflow') || route.path.startsWith('/admin/workflow'))

const themeIconComponent = computed(() => {
  return theme.value === 'dark' ? IconMoon : theme.value === 'light' ? IconSun : IconCommon
})
const themeLabel = computed(() => {
  return theme.value === 'dark' ? '暗色' : theme.value === 'light' ? '亮色' : '护眼'
})

const userInitial = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || '用户'
})

const userEmail = computed(() => {
  return authStore.user?.email || ''
})

function goProfile() {
  showUserMenu.value = false
  router.push('/settings/profile')
}

function goNotifications() {
  showUserMenu.value = false
  router.push('/settings/notifications')
}

function goToTimerIssue() {
  showTimerPopover.value = false
  if (timerStore.issueKey) {
    router.push(`/issues/${timerStore.issueKey}`)
  } else if (timerStore.issueId) {
    router.push(`/issues/${timerStore.issueId}`)
  }
}

async function handleStopTimer() {
  const result = await timerStore.stopTimer()
  if (result.success) {
    showTimerPopover.value = false
  }
}

function handleLogout() {
  showUserMenu.value = false
  authStore.logout()
}

// 点击外部关闭菜单
function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.sidebar-user') && !target.closest('.user-menu')) {
    showUserMenu.value = false
  }
  if (!target.closest('.timer-badge') && !target.closest('.timer-popover')) {
    showTimerPopover.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  // 初始化 CSS 变量，确保页面刷新时折叠态也能正确反映到面板定位
  document.documentElement.style.setProperty(
    '--tf-sidebar-width',
    sidebarCollapsed.value ? '56px' : '200px'
  )
  initNavBadge()
  initNotification()
  timerStore.init()
})
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* ===== 左侧导航栏 ===== */
.sidebar {
  width: var(--tf-sidebar-width, 200px);
  background: var(--tf-sidebar-bg);
  border-right: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s ease, background-color 0.2s;
  position: relative;
  overflow: visible;
}

/* 折叠状态 */
.sidebar.collapsed {
  width: 56px;
}

/* 折叠切换按钮 */
.sidebar-toggle {
  position: absolute;
  right: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--tf-border);
  background: var(--tf-bg-elevated);
  color: var(--tf-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  font-size: 12px;
  transition: background 0.15s, color 0.15s, opacity 0.15s;
  opacity: 0;
  padding: 0;
  line-height: 1;
}
.sidebar:hover .sidebar-toggle {
  opacity: 1;
}
.sidebar-toggle:hover {
  background: var(--tf-accent);
  color: #fff;
  border-color: var(--tf-accent);
}

.sidebar-logo {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--tf-border-light);
  cursor: pointer;
  overflow: hidden;
  min-height: 58px;
}

.logo-image {
  display: block;
  width: 132px;
  height: 34px;
  object-fit: contain;
  object-position: left center;
  flex-shrink: 0;
  transition: opacity 0.15s;
}

.logo-icon {
  display: block;
  width: 32px;
  height: 32px;
  object-fit: contain;
  flex-shrink: 0;
}

/* 折叠时 logo 居中 */
.sidebar.collapsed .sidebar-logo {
  padding: 12px 8px;
  justify-content: center;
}

.sidebar-nav {
  flex: 1;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  height: 36px;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--tf-radius-md);
  color: var(--tf-sidebar-text);
  text-decoration: none;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
  cursor: pointer;
  overflow: hidden;
  white-space: nowrap;
}
.nav-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
  text-decoration: none;
}
.nav-item.active {
  background: var(--tf-sidebar-active-bg);
  color: var(--tf-sidebar-active-text);
}

/* 折叠时导航项居中 */
.sidebar.collapsed .nav-item {
  padding: 0;
  justify-content: center;
  gap: 0;
}
.sidebar.collapsed .footer-item {
  padding: 0;
  justify-content: center;
  gap: 0;
}
.sidebar.collapsed .sidebar-user {
  padding: 8px 0;
  justify-content: center;
}

/* 折叠时隐藏文字 */
.sidebar.collapsed .nav-label,
.sidebar.collapsed .user-name,
.sidebar.collapsed .timer-badge-label,
.sidebar.collapsed .nav-badge {
  display: none;
}

.nav-icon {
  font-size: 18px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  color: inherit;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--tf-accent);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  flex-shrink: 0;
}

/* 底部 */
.sidebar-footer {
  padding: 8px 6px;
  border-top: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
}

.footer-item {
  display: flex;
  align-items: center;
  height: 36px;
  gap: 10px;
  padding: 0 12px;
  border-radius: var(--tf-radius-md);
  color: var(--tf-sidebar-text);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  text-decoration: none;
}
.footer-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
  text-decoration: none;
}

.theme-switcher {
  border: 1px dashed var(--tf-border);
  margin: 2px 0;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-top: 4px;
  border-radius: var(--tf-radius-md);
  cursor: pointer;
}
.sidebar-user:hover { background: var(--tf-sidebar-hover); }

.user-avatar-sm {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  font-size: 12px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 用户菜单 */
.user-menu {
  position: absolute;
  bottom: 56px;
  left: 8px;
  width: 220px;
  min-width: 180px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  padding: 8px;
  z-index: 100;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
}

.user-avatar-lg {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.user-menu-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.user-menu-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-email {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-menu-divider {
  height: 1px;
  background: var(--tf-border-light);
  margin: 4px 0;
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.user-menu-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
}
.user-menu-item.danger:hover {
  background: rgba(248, 81, 73, 0.1);
  color: var(--tf-danger);
}

.menu-item-icon {
  font-size: 16px;
  width: 18px;
  text-align: center;
  color: inherit;
  flex-shrink: 0;
}

/* ===== 右侧主内容区 ===== */
.main-area {
  flex: 1;
  overflow: hidden;
  background: var(--tf-bg-body);
  transition: background-color 0.2s;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  overflow: hidden;
}

/* ===== 通知铃铛 ===== */
.notification-trigger {
  position: relative;
}

.notification-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.notification-badge {
  position: absolute;
  top: -4px;
  right: -8px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--tf-danger, #f85149);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

/* ===== 计时器 Badge ===== */
.timer-badge {
  position: relative;
}

.timer-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.timer-pulse {
  position: absolute;
  top: -2px;
  right: -4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tf-success, #3fb950);
  animation: timer-pulse-anim 1.5s ease-in-out infinite;
}

@keyframes timer-pulse-anim {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.3); }
}

.timer-badge-label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.timer-badge-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent, #58a6ff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80px;
}

.timer-elapsed {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-success, #3fb950);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  flex-shrink: 0;
}

/* ===== 计时器弹出面板 ===== */
.timer-popover {
  position: absolute;
  bottom: 100%;
  left: 8px;
  margin-bottom: 8px;
  width: 220px;
  background: var(--tf-bg-elevated, #2a2d33);
  border: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.2);
  z-index: 100;
}

.timer-popover-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.timer-popover-icon {
  font-size: 16px;
  color: inherit;
}

.timer-popover-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
}

.timer-popover-body {
  margin-bottom: 12px;
}

.timer-issue {
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
  transition: background 150ms;
}

.timer-issue:hover {
  background: var(--tf-bg-hover, rgba(255,255,255,0.06));
}

.timer-issue-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent, #58a6ff);
  margin-right: 6px;
}

.timer-issue-title {
  font-size: 12px;
  color: var(--tf-text-secondary, #9ca3af);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timer-elapsed-large {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  text-align: center;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
}

.timer-popover-footer {
  border-top: 1px solid var(--tf-border, rgba(255,255,255,0.1));
  padding-top: 10px;
}

.timer-stop-btn {
  width: 100%;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: var(--tf-danger, #f85149);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 150ms;
}

.timer-stop-btn:hover {
  opacity: 0.9;
}

.timer-stop-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
