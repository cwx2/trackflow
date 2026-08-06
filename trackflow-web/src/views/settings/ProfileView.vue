<template>
  <div class="profile-page">
    <!-- 顶部：头像 + 基本信息概览 -->
    <div class="profile-header">
      <div class="profile-avatar">
        <span class="avatar-text">{{ userInitial }}</span>
      </div>
      <div class="profile-header-info">
        <h1 class="profile-name">{{ displayName }}</h1>
        <span class="profile-username">@{{ username }}</span>
      </div>
    </div>

    <!-- 面包屑 -->
    <div class="profile-breadcrumb">
      <span class="breadcrumb-item">用户</span>
      <span class="breadcrumb-sep">/</span>
      <span class="breadcrumb-item current">{{ username }}</span>
    </div>

    <!-- Tab 标签页结构 -->
    <a-tabs v-model:active-key="activeTab" class="profile-tabs">
      <a-tab-pane key="general" title="常规">
        <div class="tab-content">
          <div class="info-section">
            <h3 class="section-title">基本信息</h3>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">全名</span>
                <span class="info-value">{{ displayName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ username }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">邮箱</span>
                <span class="info-value">{{ email || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">注册日期</span>
                <span class="info-value">{{ formattedCreatedAt }}</span>
              </div>
            </div>
            <p class="info-hint">账号信息由公司身份系统 (Keycloak) 管理，如需修改请联系管理员。</p>
          </div>

          <div class="info-section">
            <h3 class="section-title">界面偏好</h3>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-label">主题模式</span>
                <span class="pref-desc">选择界面显示风格</span>
              </div>
              <a-select v-model="currentTheme" size="small" style="width: 120px" @change="onThemeChange">
                <a-option value="dark">暗色</a-option>
                <a-option value="light">亮色</a-option>
              </a-select>
            </div>
          </div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="workspace" title="工作空间" disabled>
        <div class="tab-content tab-placeholder">
          <icon-common class="placeholder-icon" />
          <p class="placeholder-text">工作空间设置即将推出</p>
        </div>
      </a-tab-pane>
      <a-tab-pane key="notifications" title="通知" disabled>
        <div class="tab-content tab-placeholder">
          <icon-notification class="placeholder-icon" />
          <p class="placeholder-text">通知设置即将推出</p>
        </div>
      </a-tab-pane>
      <a-tab-pane key="security" title="账户安全" disabled>
        <div class="tab-content tab-placeholder">
          <icon-lock class="placeholder-icon" />
          <p class="placeholder-text">账户安全设置即将推出</p>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { authApi } from '@/api'

const authStore = useAuthStore()
const { theme, setTheme } = useTheme()

const activeTab = ref('general')
const currentTheme = ref(theme.value)
const createdAt = ref<string | null>(null)

const user = computed(() => authStore.user)
const displayName = computed(() => user.value?.displayName || user.value?.username || '-')
const username = computed(() => user.value?.username || '-')
const email = computed(() => user.value?.email || '')

const userInitial = computed(() => {
  const name = displayName.value
  return name.charAt(0).toUpperCase()
})

const formattedCreatedAt = computed(() => {
  if (!createdAt.value) return '加载中...'
  try {
    const date = new Date(createdAt.value)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    })
  } catch {
    return createdAt.value
  }
})

function onThemeChange(val: string) {
  setTheme(val as 'dark' | 'light')
}

onMounted(async () => {
  // 获取用户注册日期（通过 /auth/me/profile 接口，任何已认证用户可访问）
  try {
    const res = await authApi.getMyProfile()
    if (res.code === 0 && res.data) {
      createdAt.value = res.data.createdAt
    }
  } catch {
    // 静默处理，注册日期显示为 '-'
    createdAt.value = null
  }
})
</script>

<style scoped>
.profile-page {
  padding: 32px;
  max-width: 720px;
  overflow-y: auto;
  height: 100%;
}

/* ===== 顶部 Header ===== */
.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-text {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
}

.profile-header-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0;
  line-height: 1.3;
}

.profile-username {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

/* ===== 面包屑 ===== */
.profile-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.breadcrumb-sep {
  color: var(--tf-text-quaternary);
}

.breadcrumb-item.current {
  color: var(--tf-text-secondary);
}

/* ===== Tabs ===== */
.profile-tabs {
  margin-top: 8px;
}

.tab-content {
  padding: 16px 0;
}

/* ===== 信息区块 ===== */
.info-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--tf-border-light);
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info-label {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  width: 80px;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.info-hint {
  margin-top: 16px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
}

/* ===== 偏好设置 ===== */
.pref-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.pref-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pref-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.pref-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* ===== Placeholder Tabs ===== */
.tab-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  gap: 12px;
}

.placeholder-icon {
  font-size: 32px;
  color: var(--tf-text-quaternary);
}

.placeholder-text {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
}
</style>
