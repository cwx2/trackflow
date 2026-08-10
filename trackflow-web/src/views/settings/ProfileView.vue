<template>
  <div class="profile-page">
    <!-- 顶部：头像 + 基本信息概览 -->
    <div class="profile-header">
      <div class="profile-avatar">
        <UserAvatar :name="profileData?.displayName || displayName" :size="64" />
      </div>
      <div class="profile-header-info">
        <h1 class="profile-name">{{ profileData?.displayName || displayName }}</h1>
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
          <a-spin :loading="loading" style="width: 100%">
            <div class="info-section">
              <h3 class="section-title">基本信息</h3>
              <div class="info-grid">
                <!-- 全名（可编辑） -->
                <div class="info-item">
                  <span class="info-label">全名</span>
                  <div class="info-value editable-field">
                    <template v-if="!editingName">
                      <span>{{ profileData?.displayName || displayName }}</span>
                      <a-button type="text" size="mini" class="edit-btn" @click="startEditName">
                        <template #icon><icon-edit /></template>
                      </a-button>
                    </template>
                    <template v-else>
                      <a-input
                        v-model="editForm.displayName"
                        size="small"
                        :max-length="100"
                        style="width: 200px"
                        @press-enter="saveProfile"
                      />
                      <a-button type="primary" size="mini" :loading="saving" @mousedown.prevent @click="saveProfile" style="margin-left: 8px">
                        保存
                      </a-button>
                      <a-button type="text" size="mini" @mousedown.prevent @click="cancelEditName" style="margin-left: 4px">
                        取消
                      </a-button>
                    </template>
                  </div>
                </div>
                <!-- 用户名（只读） -->
                <div class="info-item">
                  <span class="info-label">用户名</span>
                  <span class="info-value readonly-value">{{ username }}</span>
                </div>
                <!-- 邮箱（只读） -->
                <div class="info-item">
                  <span class="info-label">邮箱</span>
                  <span class="info-value readonly-value">{{ email || '-' }}</span>
                </div>
                <!-- 注册日期（只读） -->
                <div class="info-item">
                  <span class="info-label">注册日期</span>
                  <span class="info-value readonly-value">{{ formattedCreatedAt }}</span>
                </div>
              </div>
              <p class="info-hint">用户名和邮箱由公司身份系统 (Keycloak) 管理，如需修改请联系管理员。</p>
            </div>

            <div class="info-section">
              <h3 class="section-title">界面偏好</h3>
              <!-- 主题模式 -->
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
              <!-- 本地时区 -->
              <div class="pref-item">
                <div class="pref-info">
                  <span class="pref-label">本地时区</span>
                  <span class="pref-desc">用于显示日期和时间</span>
                </div>
                <a-select
                  v-model="editForm.timezone"
                  size="small"
                  style="width: 200px"
                  :options="timezoneOptions"
                  @change="savePreference"
                />
              </div>
              <!-- 语言 -->
              <div class="pref-item">
                <div class="pref-info">
                  <span class="pref-label">语言</span>
                  <span class="pref-desc">界面显示语言</span>
                </div>
                <a-select
                  v-model="editForm.language"
                  size="small"
                  style="width: 200px"
                  @change="savePreference"
                >
                  <a-option value="zh-CN">中文</a-option>
                  <a-option value="en-US">English</a-option>
                </a-select>
              </div>
              <!-- 日期格式 -->
              <div class="pref-item">
                <div class="pref-info">
                  <span class="pref-label">日期格式</span>
                  <span class="pref-desc">日期的显示格式</span>
                </div>
                <a-select
                  v-model="editForm.dateFormat"
                  size="small"
                  style="width: 200px"
                  @change="savePreference"
                >
                  <a-option value="yyyy-MM-dd">2026-08-06 (yyyy-MM-dd)</a-option>
                  <a-option value="dd/MM/yyyy">06/08/2026 (dd/MM/yyyy)</a-option>
                  <a-option value="MM/dd/yyyy">08/06/2026 (MM/dd/yyyy)</a-option>
                  <a-option value="yyyy年MM月dd日">2026年08月06日</a-option>
                </a-select>
              </div>
              <!-- 每周第一天 -->
              <div class="pref-item">
                <div class="pref-info">
                  <span class="pref-label">每周第一天</span>
                  <span class="pref-desc">日历和周视图的起始日</span>
                </div>
                <a-select
                  v-model="editForm.firstDayOfWeek"
                  size="small"
                  style="width: 200px"
                  @change="savePreference"
                >
                  <a-option value="MONDAY">周一</a-option>
                  <a-option value="SUNDAY">周日</a-option>
                </a-select>
              </div>
            </div>
          </a-spin>
        </div>
      </a-tab-pane>
      <a-tab-pane key="workspace" title="工作空间">
        <div class="tab-content">
          <ProfileWorkspaceTab />
        </div>
      </a-tab-pane>
      <a-tab-pane key="notifications" title="通知">
        <div class="tab-content tab-embedded-page">
          <NotificationSettingsView />
        </div>
      </a-tab-pane>
      <a-tab-pane key="security" title="账户安全">
        <div class="tab-content tab-embedded-page">
          <AccountSecurityView />
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { authApi } from '@/api'
import type { UserProfileVO } from '@/api/user'
import { UserAvatar } from '@/components/base'
import ProfileWorkspaceTab from './ProfileWorkspaceTab.vue'
import NotificationSettingsView from './NotificationSettingsView.vue'
import AccountSecurityView from './AccountSecurityView.vue'

const authStore = useAuthStore()
const { theme, setTheme } = useTheme()

const activeTab = ref('general')
const currentTheme = ref(theme.value)
const loading = ref(false)
const saving = ref(false)
const editingName = ref(false)

const profileData = ref<UserProfileVO | null>(null)

const editForm = reactive({
  displayName: '',
  timezone: 'Asia/Shanghai',
  language: 'zh-CN',
  dateFormat: 'yyyy-MM-dd',
  firstDayOfWeek: 'MONDAY'
})

const timezoneOptions = [
  { label: 'Asia/Shanghai (UTC+8)', value: 'Asia/Shanghai' },
  { label: 'Asia/Tokyo (UTC+9)', value: 'Asia/Tokyo' },
  { label: 'Asia/Singapore (UTC+8)', value: 'Asia/Singapore' },
  { label: 'Asia/Hong_Kong (UTC+8)', value: 'Asia/Hong_Kong' },
  { label: 'America/New_York (UTC-5)', value: 'America/New_York' },
  { label: 'America/Los_Angeles (UTC-8)', value: 'America/Los_Angeles' },
  { label: 'Europe/London (UTC+0)', value: 'Europe/London' },
  { label: 'Europe/Berlin (UTC+1)', value: 'Europe/Berlin' },
  { label: 'Australia/Sydney (UTC+11)', value: 'Australia/Sydney' },
  { label: 'Pacific/Auckland (UTC+12)', value: 'Pacific/Auckland' }
]

const user = computed(() => authStore.user)
const displayName = computed(() => user.value?.displayName || user.value?.username || '-')
const username = computed(() => user.value?.username || '-')
const email = computed(() => user.value?.email || '')

const formattedCreatedAt = computed(() => {
  const dateStr = profileData.value?.createdAt
  if (!dateStr) return '加载中...'
  try {
    const date = new Date(dateStr)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    })
  } catch {
    return dateStr
  }
})

function startEditName() {
  editForm.displayName = profileData.value?.displayName || displayName.value
  editingName.value = true
}

function cancelEditName() {
  editingName.value = false
}

function onThemeChange(val: any) {
  setTheme(val as 'dark' | 'light')
}

async function saveProfile() {
  if (!editForm.displayName.trim()) {
    Message.warning('全名不能为空')
    return
  }
  saving.value = true
  try {
    const res = await authApi.updateMyProfile({
      displayName: editForm.displayName.trim(),
      timezone: editForm.timezone,
      language: editForm.language,
      dateFormat: editForm.dateFormat,
      firstDayOfWeek: editForm.firstDayOfWeek
    })
    if (res.code === 0 && res.data) {
      profileData.value = res.data
      editingName.value = false
      Message.success('保存成功')
      // 同步更新 auth store 中的 displayName
      if (authStore.user) {
        authStore.user.displayName = res.data.displayName
      }
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function savePreference() {
  // 当下拉选项变更时，自动保存
  saving.value = true
  try {
    const res = await authApi.updateMyProfile({
      displayName: profileData.value?.displayName || editForm.displayName || displayName.value,
      timezone: editForm.timezone,
      language: editForm.language,
      dateFormat: editForm.dateFormat,
      firstDayOfWeek: editForm.firstDayOfWeek
    })
    if (res.code === 0 && res.data) {
      profileData.value = res.data
      Message.success('保存成功')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await authApi.getMyProfile()
    if (res.code === 0 && res.data) {
      profileData.value = res.data
      // 填充编辑表单
      editForm.displayName = res.data.displayName || ''
      editForm.timezone = res.data.timezone || 'Asia/Shanghai'
      editForm.language = res.data.language || 'zh-CN'
      editForm.dateFormat = res.data.dateFormat || 'yyyy-MM-dd'
      editForm.firstDayOfWeek = res.data.firstDayOfWeek || 'MONDAY'
    }
  } catch {
    // 静默处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProfile()
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
  display: flex;
  align-items: center;
  flex-shrink: 0;
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
  min-height: 32px;
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

.readonly-value {
  color: var(--tf-text-secondary);
}

.editable-field {
  display: flex;
  align-items: center;
  gap: 4px;
}

.edit-btn {
  opacity: 0;
  transition: opacity 150ms;
}

.info-item:hover .edit-btn {
  opacity: 1;
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
  border-bottom: 1px solid var(--tf-border-lightest);
}

.pref-item:last-child {
  border-bottom: none;
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

/* ===== Embedded Pages (Notifications, Security) ===== */
.tab-embedded-page {
  padding: 0 !important;
}

.tab-embedded-page :deep(.notification-settings-page),
.tab-embedded-page :deep(.settings-page) {
  padding: 0;
  max-width: none;
  height: auto;
  overflow: visible;
}

.tab-embedded-page :deep(.page-header),
.tab-embedded-page :deep(.page-title) {
  display: none;
}

.tab-embedded-page :deep(.settings-footer) {
  display: none;
}

</style>
