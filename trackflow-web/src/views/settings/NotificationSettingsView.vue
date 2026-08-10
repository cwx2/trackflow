<template>
  <div class="notification-settings-page">
    <div class="page-header">
      <h2 class="page-title">通知设置</h2>
      <p class="page-desc">配置哪些事件触发通知，以及通知的接收方式。</p>
    </div>

    <a-spin :loading="prefLoading" class="settings-content">
      <!-- 订阅规则管理 -->
      <NotificationSubscriptions />

      <!-- 事件订阅 + 自动关注 -->
      <EventSubscriptionSettings :form="form" @save="handleSave" />

      <!-- 通知渠道 + 静音时段 -->
      <NotificationChannelSettings
        :form="form"
        :email-status="emailStatus ?? null"
        :email-available="emailAvailable"
        :email-status-desc="emailStatusDesc"
        :email-status-reason="emailStatusReason"
        @save="handleSave"
      />

      <!-- 已静音的工单 -->
      <MutedThreadsList />

      <!-- 项目级偏好覆盖 -->
      <div class="settings-section">
        <ProjectNotificationPreferences />
      </div>
    </a-spin>

    <!-- 底部导航提示 -->
    <div class="settings-footer">
      <router-link to="/settings/profile" class="footer-link">
        ← 个人设置
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { notificationPreferenceApi } from '@/api'
import type { NotificationPreferenceVO, EmailAvailabilityVO } from '@/api/notificationPreference'
import { useRequest } from '@/composables/useRequest'
import ProjectNotificationPreferences from './ProjectNotificationPreferences.vue'
import NotificationSubscriptions from './NotificationSubscriptions.vue'
import EventSubscriptionSettings from './components/EventSubscriptionSettings.vue'
import NotificationChannelSettings from './components/NotificationChannelSettings.vue'
import MutedThreadsList from './MutedThreadsList.vue'

// --- Email status (useRequest) ---
const { data: emailStatus } = useRequest<EmailAvailabilityVO>(
  () => notificationPreferenceApi.getEmailStatus(),
  { immediate: true }
)

const emailAvailable = computed(() => emailStatus.value?.available === true)
const emailStatusReason = computed(() => emailStatus.value?.reason ?? null)
const emailStatusDesc = computed(() => {
  if (emailAvailable.value) return '将通知发送到你的注册邮箱'
  if (!emailStatus.value?.globalEnabled) return '管理员尚未启用邮件通知渠道'
  if (!emailStatus.value?.smtpConfigured) return '邮件服务器尚未配置'
  return '将通知发送到你的注册邮箱'
})

// --- Preference form ---
const form = reactive({
  onIssueAssigned: true,
  onIssueStatusChanged: true,
  onIssueCommented: true,
  onMentioned: true,
  onIssueResolved: true,
  onIssueUpdated: true,
  onSprintStarted: false,
  onSprintCompleted: false,
  onProjectMemberChanged: true,
  onProjectLifecycle: true,
  notifyOwnChanges: false,
  emailEnabled: false,
  emailOnIssueAssigned: true,
  emailOnIssueStatusChanged: true,
  emailOnIssueCommented: false,
  emailOnMentioned: true,
  emailOnIssueResolved: true,
  emailOnIssueUpdated: false,
  emailOnSprintStarted: false,
  emailOnSprintCompleted: false,
  emailOnProjectMemberChanged: true,
  emailOnProjectLifecycle: false,
  emailOnDueDate: true,
  emailOnOverdue: true,
  emailOnWatchedUpdated: false,
  emailOnIssueVoted: false,
  emailOnIssueSpentTime: false,
  quietHoursStart: null as string | null,
  quietHoursEnd: null as string | null,
  onWatchedUpdated: true,
  onIssueVoted: true,
  onIssueSpentTime: true,
  autoWatchOnCreate: true,
  autoWatchOnComment: true,
  autoWatchOnUpdate: false,
  autoWatchOnAssign: true
})

// --- Load preference using useRequest ---
const { loading: prefLoading } = useRequest<NotificationPreferenceVO>(
  () => notificationPreferenceApi.get(),
  {
    immediate: true,
    onSuccess: (data) => applyData(data),
    onError: () => Message.error('加载通知偏好失败')
  }
)

function applyData(data: NotificationPreferenceVO) {
  form.onIssueAssigned = data.onIssueAssigned
  form.onIssueStatusChanged = data.onIssueStatusChanged
  form.onIssueCommented = data.onIssueCommented
  form.onMentioned = data.onMentioned
  form.onIssueResolved = data.onIssueResolved
  form.onIssueUpdated = data.onIssueUpdated
  form.onSprintStarted = data.onSprintStarted
  form.onSprintCompleted = data.onSprintCompleted
  form.onProjectMemberChanged = data.onProjectMemberChanged
  form.onProjectLifecycle = data.onProjectLifecycle
  form.notifyOwnChanges = data.notifyOwnChanges
  form.emailEnabled = data.emailEnabled
  form.emailOnIssueAssigned = data.emailOnIssueAssigned ?? true
  form.emailOnIssueStatusChanged = data.emailOnIssueStatusChanged ?? true
  form.emailOnIssueCommented = data.emailOnIssueCommented ?? false
  form.emailOnMentioned = data.emailOnMentioned ?? true
  form.emailOnIssueResolved = data.emailOnIssueResolved ?? true
  form.emailOnIssueUpdated = data.emailOnIssueUpdated ?? false
  form.emailOnSprintStarted = data.emailOnSprintStarted ?? false
  form.emailOnSprintCompleted = data.emailOnSprintCompleted ?? false
  form.emailOnProjectMemberChanged = data.emailOnProjectMemberChanged ?? true
  form.emailOnProjectLifecycle = data.emailOnProjectLifecycle ?? false
  form.emailOnDueDate = data.emailOnDueDate ?? true
  form.emailOnOverdue = data.emailOnOverdue ?? true
  form.emailOnWatchedUpdated = data.emailOnWatchedUpdated ?? false
  form.emailOnIssueVoted = data.emailOnIssueVoted ?? false
  form.emailOnIssueSpentTime = data.emailOnIssueSpentTime ?? false
  form.quietHoursStart = data.quietHoursStart
  form.quietHoursEnd = data.quietHoursEnd
  form.onWatchedUpdated = data.onWatchedUpdated ?? true
  form.onIssueVoted = data.onIssueVoted ?? true
  form.onIssueSpentTime = data.onIssueSpentTime ?? true
  form.autoWatchOnCreate = data.autoWatchOnCreate ?? true
  form.autoWatchOnComment = data.autoWatchOnComment ?? true
  form.autoWatchOnUpdate = data.autoWatchOnUpdate ?? false
  form.autoWatchOnAssign = data.autoWatchOnAssign ?? true
}

// --- Debounced save ---
let saveTimeout: ReturnType<typeof setTimeout> | null = null

function handleSave() {
  if (saveTimeout) clearTimeout(saveTimeout)
  saveTimeout = setTimeout(async () => {
    try {
      const res = await notificationPreferenceApi.update({ ...form })
      if (res.code === 0) {
        Message.success('通知偏好已保存')
      }
    } catch {
      Message.error('保存失败，请重试')
    }
  }, 500)
}
</script>

<style scoped>
.notification-settings-page {
  padding: 32px;
  max-width: 680px;
  overflow-y: auto;
  height: 100%;
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.page-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.settings-content {
  display: block;
}

.settings-section {
  margin-bottom: 32px;
}

.settings-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.footer-link {
  font-size: 12px;
  color: var(--tf-text-accent);
  text-decoration: none;
  transition: opacity 150ms;
}

.footer-link:hover {
  opacity: 0.8;
}
</style>
