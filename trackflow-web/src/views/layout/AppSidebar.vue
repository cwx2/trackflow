<template>
  <!--
    ===== AppSidebar — 左侧导航栏 =====
    管理：折叠/展开状态、logo 区、导航区、底部功能区（创建/通知/计时器/主题/用户）
  -->
  <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">

    <!-- ===== 折叠切换按钮 ===== -->
    <button
      class="sidebar-toggle"
      :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
      @click="toggleSidebar"
    >
      <icon-left v-if="!sidebarCollapsed" />
      <icon-right v-else />
    </button>

    <!-- ===== Logo 区 ===== -->
    <div class="sidebar-logo" @click="$router.push('/')">
      <img v-if="!sidebarCollapsed" class="logo-image" :src="trackflowLogoUrl" alt="TrackFlow" />
      <img v-else class="logo-icon" :src="trackflowIconUrl" alt="TrackFlow" />
    </div>

    <!-- ===== 主导航区 ===== -->
    <nav class="sidebar-nav">
      <SidebarNavItem
        to="/dashboard"
        label="仪表盘"
        :collapsed="sidebarCollapsed"
        :active="$route.path === '/dashboard'"
      >
        <template #icon><icon-dashboard class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        to="/issues"
        label="问题"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Issues'"
        :badge="issueBadgeCount"
        badge-title="个待测试工单"
      >
        <template #icon><icon-unordered-list class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        to="/projects"
        label="项目"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Projects'"
      >
        <template #icon><icon-layers class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        to="/boards"
        label="看板"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Boards'"
      >
        <template #icon><icon-apps class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        to="/sprints"
        label="迭代"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Sprints'"
      >
        <template #icon><icon-thunderbolt class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="canViewSprintPlanning"
        to="/sprint-planning"
        label="规划"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'SprintPlanning'"
      >
        <template #icon><icon-calendar class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        to="/timesheets"
        label="时间表"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Timesheets'"
      >
        <template #icon><icon-clock-circle class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="canViewReport"
        to="/reports"
        label="报表"
        :collapsed="sidebarCollapsed"
        :active="$route.path.startsWith('/reports')"
      >
        <template #icon><icon-bar-chart class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="isAdmin"
        to="/automation"
        label="自动化"
        :collapsed="sidebarCollapsed"
        :active="$route.path.startsWith('/automation')"
      >
        <template #icon><icon-robot class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="canManageWorkflow"
        to="/workflow"
        label="工作流"
        :collapsed="sidebarCollapsed"
        :active="isWorkflowRoute"
      >
        <template #icon><icon-share-alt class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="isAdmin"
        to="/admin"
        label="管理"
        :collapsed="sidebarCollapsed"
        :active="isAdminRoute"
      >
        <template #icon><icon-settings class="nav-icon" /></template>
      </SidebarNavItem>

      <SidebarNavItem
        v-if="canViewTrash"
        to="/trash"
        label="回收站"
        :collapsed="sidebarCollapsed"
        :active="$route.name === 'Trash'"
      >
        <template #icon><icon-delete class="nav-icon" /></template>
      </SidebarNavItem>
    </nav>

    <!-- ===== 底部功能区 ===== -->
    <div class="sidebar-footer">

      <!-- 创建工单 -->
      <SidebarNavItem
        v-if="canCreateIssue"
        label="创建"
        :collapsed="sidebarCollapsed"
        @click="$emit('openCreate')"
      >
        <template #icon><icon-plus-circle class="nav-icon" /></template>
      </SidebarNavItem>

      <!-- 通知铃铛 -->
      <SidebarNavItem
        label="通知"
        :collapsed="sidebarCollapsed"
        @click="toggleNotificationPanel"
      >
        <template #icon>
          <span class="nav-icon notification-icon-wrap">
            <icon-notification />
            <span v-if="hasUnread" class="notification-badge">
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </span>
        </template>
      </SidebarNavItem>

      <!-- 计时器 Badge + 浮层 -->
      <TimerPopover v-model:visible="showTimerPopover" :collapsed="sidebarCollapsed" />

      <!-- 主题切换 -->
      <SidebarNavItem
        :label="themeLabel"
        :collapsed="sidebarCollapsed"
        class="theme-switcher"
        @click="cycleTheme"
      >
        <template #icon>
          <component :is="themeIconComponent" class="nav-icon" />
        </template>
      </SidebarNavItem>

      <!-- 用户头像 -->
      <a-tooltip
        v-if="sidebarCollapsed"
        :content="userName"
        position="right"
        :mini="true"
      >
        <div class="sidebar-user" @click="showUserMenu = !showUserMenu">
          <UserAvatar :name="userName" :size="24" />
          <span class="user-name">{{ userName }}</span>
        </div>
      </a-tooltip>
      <div v-else class="sidebar-user" @click="showUserMenu = !showUserMenu">
        <UserAvatar :name="userName" :size="24" />
        <span class="user-name">{{ userName }}</span>
      </div>

      <!-- ===== 用户下拉菜单 ===== -->
      <div v-if="showUserMenu" class="user-menu">
        <div class="user-menu-header">
          <UserAvatar :name="userName" :size="32" />
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
</template>

<script setup lang="ts">
/**
 * AppSidebar — 左侧导航栏
 *
 * 职责：
 * - 管理侧边栏折叠/展开状态（持久化到 localStorage）
 * - 渲染全部导航项（用 SidebarNavItem 统一封装）
 * - 底部：创建工单入口、通知铃铛、计时器、主题切换、用户菜单
 * - 同步更新 CSS 变量 --tf-sidebar-width，供依赖定位的组件使用
 *
 * Emits：
 *   openCreate — 点击「创建」按钮时，通知父级 AppLayout 打开创建面板
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { useNavBadge } from '@/composables/useNavBadge'
import { useNotification } from '@/composables/useNotification'
import { IconMoon, IconSun, IconCommon, IconLeft, IconRight } from '@arco-design/web-vue/es/icon'
import SidebarNavItem from './SidebarNavItem.vue'
import TimerPopover from './TimerPopover.vue'
import { UserAvatar } from '@/components/base'
import trackflowLogoUrl from '@/assets/trackflow-watermark.svg'
import trackflowIconUrl from '@/assets/trackflow-icon.svg'

defineEmits<{ openCreate: [] }>()

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { issueBadgeCount, init: initNavBadge } = useNavBadge()
const { unreadCount, hasUnread, togglePanel: toggleNotificationPanel, init: initNotification } = useNotification()
const { theme, cycleTheme } = useTheme()

// ===== 折叠状态（持久化） =====
const SIDEBAR_KEY = 'tf_sidebar_collapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === 'true')

function syncCssVar() {
  document.documentElement.style.setProperty(
    '--tf-sidebar-width',
    sidebarCollapsed.value ? '56px' : '200px'
  )
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, String(sidebarCollapsed.value))
  syncCssVar()
}

// ===== 权限计算 =====
const isAdmin = computed(() => {
  if (authStore.permissionsLoaded) {
    return authStore.hasGlobalPermission('system:manage_users')
      || authStore.hasGlobalPermission('system:manage_roles')
      || authStore.hasGlobalPermission('system:manage_orgs')
  }
  return false
})

const canManageWorkflow = computed(() => {
  if (isAdmin.value) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:workflow')
  return false
})

const canViewReport = computed(() => {
  if (isAdmin.value) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:report')
  return false
})

const canViewTrash = computed(() => {
  if (isAdmin.value) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:trash')
  return false
})

const canViewSprintPlanning = computed(() => {
  if (isAdmin.value) return true
  if (authStore.permissionsLoaded) return authStore.hasGlobalPermission('nav:sprint_manage')
  return false
})

const canCreateIssue = computed(() => {
  if (isAdmin.value) return true
  if (authStore.permissionsLoaded) return authStore.canCreateIssue
  return false
})

// ===== 路由激活状态 =====
const isAdminRoute = computed(() =>
  route.path.startsWith('/admin') && !route.path.startsWith('/admin/workflow')
)
const isWorkflowRoute = computed(() =>
  route.path.startsWith('/workflow') || route.path.startsWith('/admin/workflow')
)

// ===== 主题 =====
const themeIconComponent = computed(() =>
  theme.value === 'dark' ? IconMoon : theme.value === 'light' ? IconSun : IconCommon
)
const themeLabel = computed(() =>
  theme.value === 'dark' ? '暗色' : theme.value === 'light' ? '亮色' : '护眼'
)

// ===== 用户信息 =====
const userName = computed(() =>
  authStore.user?.displayName || authStore.user?.username || '用户'
)
const userEmail = computed(() => authStore.user?.email || '')

const showUserMenu = ref(false)
const showTimerPopover = ref(false)

function goProfile() {
  showUserMenu.value = false
  router.push('/settings/profile')
}
function goNotifications() {
  showUserMenu.value = false
  router.push('/settings/notifications')
}
function handleLogout() {
  showUserMenu.value = false
  authStore.logout()
}

// ===== 点击外部关闭 =====
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
  syncCssVar()
  initNavBadge()
  initNotification()
})
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
/* ===== 侧边栏容器 ===== */
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
.sidebar.collapsed { width: 56px; }

/* ===== 折叠切换按钮 ===== */
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
.sidebar:hover .sidebar-toggle { opacity: 1; }
.sidebar-toggle:hover {
  background: var(--tf-accent);
  color: var(--tf-text-on-accent);
  border-color: var(--tf-accent);
}

/* ===== Logo ===== */
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
.sidebar.collapsed .sidebar-logo {
  padding: 12px 8px;
  justify-content: center;
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

/* ===== 导航区 ===== */
.sidebar-nav {
  flex: 1;
  padding: 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-icon {
  font-size: 18px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
  color: inherit;
}

/* ===== 底部功能区 ===== */
.sidebar-footer {
  padding: 8px 6px;
  border-top: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
}

.theme-switcher {
  border: 1px dashed var(--tf-border);
  margin: 2px 0;
}

/* ===== 通知铃铛 ===== */
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
  background: var(--tf-danger);
  color: var(--tf-text-on-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

/* ===== 用户区 ===== */
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

.sidebar.collapsed .sidebar-user {
  padding: 8px 0;
  justify-content: center;
}

.user-name {
  font-size: 12px;
  color: var(--tf-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sidebar.collapsed .user-name { display: none; }

/* ===== 用户下拉菜单 ===== */
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
  box-shadow: var(--tf-shadow-xl);
}
.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
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
  background: var(--tf-danger-bg);
  color: var(--tf-danger);
}
.menu-item-icon {
  font-size: 16px;
  width: 18px;
  text-align: center;
  color: inherit;
  flex-shrink: 0;
}
</style>
