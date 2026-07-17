<template>
  <div class="app-layout">
    <!-- 左侧导航栏（全高） -->
    <aside class="sidebar">
      <div class="sidebar-logo" @click="$router.push('/')">
        <span class="logo-icon">T</span>
        <span class="logo-text">TrackFlow</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/dashboard" class="nav-item" :class="{ active: $route.name === 'Dashboard' }">
          <span class="nav-icon">🏠</span>
          <span class="nav-label">工作台</span>
        </router-link>
        <router-link to="/issues" class="nav-item" :class="{ active: $route.name === 'Issues' }">
          <span class="nav-icon">📋</span>
          <span class="nav-label">问题</span>
          <span v-if="issueBadgeCount > 0" class="nav-badge" :title="`${issueBadgeCount} 个待测试工单`">
            {{ issueBadgeCount > 99 ? '99+' : issueBadgeCount }}
          </span>
        </router-link>
        <router-link to="/projects" class="nav-item" :class="{ active: $route.name === 'Projects' }">
          <span class="nav-icon">📁</span>
          <span class="nav-label">项目</span>
        </router-link>
        <router-link to="/boards" class="nav-item" :class="{ active: $route.name === 'Boards' }">
          <span class="nav-icon">📊</span>
          <span class="nav-label">看板</span>
        </router-link>
        <router-link to="/sprints" class="nav-item" :class="{ active: $route.name === 'Sprints' }">
          <span class="nav-icon">🏃</span>
          <span class="nav-label">迭代</span>
          <span v-if="!canManageSprint && navBadgeLoaded" class="nav-readonly-tag" title="您当前无迭代管理权限">只读</span>
        </router-link>
        <router-link to="/timesheets" class="nav-item" :class="{ active: $route.name === 'Timesheets' }">
          <span class="nav-icon">⏱</span>
          <span class="nav-label">时间表</span>
        </router-link>
        <router-link v-if="canViewReport" to="/reports" class="nav-item" :class="{ active: $route.name === 'Reports' }">
          <span class="nav-icon">📈</span>
          <span class="nav-label">报表</span>
        </router-link>
        <router-link v-if="canManageWorkflow" to="/workflow" class="nav-item" :class="{ active: $route.path.startsWith('/workflow') }">
          <span class="nav-icon">🔄</span>
          <span class="nav-label">工作流</span>
        </router-link>
        <router-link v-if="isAdmin" to="/admin" class="nav-item" :class="{ active: isAdminRoute }">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">管理</span>
        </router-link>
        <router-link to="/trash" class="nav-item" :class="{ active: $route.name === 'Trash' }">
          <span class="nav-icon">🗑️</span>
          <span class="nav-label">回收站</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link v-if="canCreateIssue" to="/issues/create" class="footer-item">
          <span class="nav-icon">➕</span>
          <span class="nav-label">创建</span>
        </router-link>

        <!-- 通知铃铛 -->
        <div class="footer-item notification-trigger" @click="toggleNotificationPanel">
          <span class="nav-icon notification-icon-wrap">
            🔔
            <span v-if="hasUnread" class="notification-badge">
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </span>
          <span class="nav-label">通知</span>
        </div>

        <!-- 主题切换 -->
        <div class="footer-item theme-switcher" @click="cycleTheme">
          <span class="nav-icon">{{ themeIcon }}</span>
          <span class="nav-label">{{ themeLabel }}</span>
        </div>

        <div class="sidebar-user" @click="showUserMenu = !showUserMenu">
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
            <span class="menu-item-icon">👤</span>
            <span>个人设置</span>
          </div>
          <div class="user-menu-item" @click="goNotifications">
            <span class="menu-item-icon">🔔</span>
            <span>通知偏好</span>
          </div>
          <div class="user-menu-divider"></div>
          <div class="user-menu-item danger" @click="handleLogout">
            <span class="menu-item-icon">🚪</span>
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
import { useTheme } from '@/composables/useTheme'
import { useNavBadge } from '@/composables/useNavBadge'
import { useNotification } from '@/composables/useNotification'
import TabBar from './TabBar.vue'
import NotificationPanel from './NotificationPanel.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { issueBadgeCount, canManageSprint, loaded: navBadgeLoaded, init: initNavBadge } = useNavBadge()
const { unreadCount, hasUnread, togglePanel: toggleNotificationPanel, init: initNotification } = useNotification()

// TabBar 仅在 Issue 相关路由显示（Issue 列表、Issue 详情）
const showTabBar = computed(() => {
  const name = route.name
  return name === 'Issues' || name === 'IssueDetail'
})
const { theme, cycleTheme } = useTheme()
const showUserMenu = ref(false)

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

const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const themeIcon = computed(() => {
  return theme.value === 'dark' ? '🌙' : theme.value === 'light' ? '☀️' : '🌿'
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
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  initNavBadge()
  initNotification()
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
  transition: background-color 0.2s;
}

.sidebar-logo {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--tf-border-light);
  cursor: pointer;
}

.logo-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: linear-gradient(135deg, var(--tf-accent), var(--tf-purple));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
}

.logo-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--tf-text-primary);
  letter-spacing: -0.3px;
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

.nav-icon {
  font-size: 14px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
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

.nav-readonly-tag {
  margin-left: auto;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--tf-bg-hover);
  color: var(--tf-text-muted);
  font-weight: 500;
  letter-spacing: 0.3px;
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
  right: 8px;
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
  font-size: 14px;
  width: 18px;
  text-align: center;
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
</style>
