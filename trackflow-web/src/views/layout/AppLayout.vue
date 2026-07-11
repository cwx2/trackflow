<template>
  <div class="app-layout">
    <!-- 左侧导航栏（全高） -->
    <aside class="sidebar">
      <div class="sidebar-logo" @click="$router.push('/')">
        <span class="logo-icon">T</span>
        <span class="logo-text">TrackFlow</span>
      </div>

      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" :class="{ active: $route.name === 'Issues' }">
          <span class="nav-icon">📋</span>
          <span class="nav-label">问题</span>
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
        </router-link>
        <router-link to="/admin/workflow" class="nav-item" :class="{ active: $route.name === 'WorkflowEditor' }">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">工作流</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/issues/create" class="footer-item">
          <span class="nav-icon">➕</span>
          <span class="nav-label">创建</span>
        </router-link>

        <!-- 主题切换 -->
        <div class="footer-item theme-switcher" @click="cycleTheme">
          <span class="nav-icon">{{ themeIcon }}</span>
          <span class="nav-label">{{ themeLabel }}</span>
        </div>

        <div class="footer-item" @click="showAdminMenu = !showAdminMenu">
          <span class="nav-icon">⚙️</span>
          <span class="nav-label">管理</span>
        </div>
        <div v-if="showAdminMenu" class="admin-submenu">
          <router-link to="/admin/users" class="submenu-item" @click="showAdminMenu = false">用户管理</router-link>
          <router-link to="/admin/roles" class="submenu-item" @click="showAdminMenu = false">角色管理</router-link>
          <router-link to="/admin/organizations" class="submenu-item" @click="showAdminMenu = false">组织管理</router-link>
        </div>

        <div class="sidebar-user" @click="handleLogout">
          <div class="user-avatar-sm">{{ userInitial }}</div>
          <span class="user-name">{{ userName }}</span>
        </div>
      </div>
    </aside>

    <!-- 右侧（标签栏 + 内容区） -->
    <div class="main-area">
      <TabBar />
      <div class="main-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import TabBar from './TabBar.vue'

const authStore = useAuthStore()
const { theme, cycleTheme } = useTheme()
const showAdminMenu = ref(false)

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

function handleLogout() {
  authStore.logout()
}
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

/* 底部 */
.sidebar-footer {
  padding: 8px 6px;
  border-top: 1px solid var(--tf-border-light);
  display: flex;
  flex-direction: column;
  gap: 4px;
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

.admin-submenu {
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.submenu-item {
  display: block;
  padding: 6px 12px;
  color: var(--tf-text-secondary);
  text-decoration: none;
  font-size: 12px;
  border-radius: 4px;
  transition: background 0.15s, color 0.15s;
}
.submenu-item:hover {
  background: var(--tf-sidebar-hover);
  color: var(--tf-text-primary);
  text-decoration: none;
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
</style>
