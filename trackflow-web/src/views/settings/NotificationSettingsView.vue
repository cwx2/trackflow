<template>
  <div class="notification-settings-page">
    <div class="page-header">
      <h2 class="page-title">通知设置</h2>
      <p class="page-desc">配置哪些事件触发通知，以及通知的接收方式。</p>
    </div>

    <a-spin :loading="loading" class="settings-content">
      <!-- 事件订阅 -->
      <div class="settings-section">
        <h3 class="section-title">事件订阅</h3>
        <p class="section-desc">选择哪些事件会向你发送站内通知。</p>

        <!-- 全局行为开关 -->
        <div class="global-toggle">
          <div class="global-toggle-info">
            <span class="global-toggle-label">自己的操作也通知我</span>
            <span class="global-toggle-desc">启用后，你对工单的操作（状态变更、评论、分配等）也会生成通知发送给你自己。默认关闭，因为通常不需要被自己的操作通知。</span>
          </div>
          <a-switch v-model="form.notifyOwnChanges" size="small" @change="handleSave" />
        </div>

        <div class="event-group">
          <div class="event-group-title">参与中的工单</div>
          <div class="event-items">
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">工单分配给我</span>
                <span class="event-desc">有工单被分配给你时通知</span>
              </div>
              <a-switch v-model="form.onIssueAssigned" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">工单状态变更</span>
                <span class="event-desc">你参与的工单状态发生变化时通知</span>
              </div>
              <a-switch v-model="form.onIssueStatusChanged" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">新评论</span>
                <span class="event-desc">你参与的工单有新评论时通知</span>
              </div>
              <a-switch v-model="form.onIssueCommented" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">@提及我</span>
                <span class="event-desc">评论中被 @提及时通知</span>
              </div>
              <a-switch v-model="form.onMentioned" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">我报告的工单被解决</span>
                <span class="event-desc">你创建的工单被标记为已解决时通知</span>
              </div>
              <a-switch v-model="form.onIssueResolved" size="small" @change="handleSave" />
            </div>
          </div>
        </div>

        <div class="event-group">
          <div class="event-group-title">项目动态</div>
          <div class="event-items">
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">Sprint 启动</span>
                <span class="event-desc">你所在项目有新 Sprint 启动时通知</span>
              </div>
              <a-switch v-model="form.onSprintStarted" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">Sprint 完成</span>
                <span class="event-desc">你所在项目有 Sprint 完成时通知</span>
              </div>
              <a-switch v-model="form.onSprintCompleted" size="small" @change="handleSave" />
            </div>
          </div>
        </div>

        <div class="event-group">
          <div class="event-group-title">项目事件</div>
          <div class="event-items">
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">成员变更</span>
                <span class="event-desc">你被添加/移出项目、角色变更、负责人变更时通知</span>
              </div>
              <a-switch v-model="form.onProjectMemberChanged" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">项目归档/恢复</span>
                <span class="event-desc">你所在项目被归档或恢复时通知</span>
              </div>
              <a-switch v-model="form.onProjectLifecycle" size="small" @change="handleSave" />
            </div>
            <div class="event-item">
              <div class="event-info">
                <span class="event-label">项目删除</span>
                <span class="event-desc">你所在项目被删除时通知（不可关闭）</span>
              </div>
              <a-switch :model-value="true" size="small" disabled />
            </div>
          </div>
        </div>
      </div>

      <!-- 通知渠道 -->
      <div class="settings-section">
        <h3 class="section-title">通知渠道</h3>
        <p class="section-desc">配置通知的接收方式。</p>

        <div class="channel-items">
          <div class="channel-item">
            <div class="channel-info">
              <span class="channel-icon">🔔</span>
              <div class="channel-text">
                <span class="channel-label">站内通知</span>
                <span class="channel-desc">通过系统内通知中心接收</span>
              </div>
            </div>
            <a-tag color="green" size="small">始终启用</a-tag>
          </div>
          <div class="channel-item">
            <div class="channel-info">
              <span class="channel-icon">📧</span>
              <div class="channel-text">
                <span class="channel-label">邮件通知</span>
                <span class="channel-desc">将通知发送到你的注册邮箱</span>
              </div>
            </div>
            <a-switch v-model="form.emailEnabled" size="small" @change="handleSave" disabled />
            <a-tag size="small" class="coming-soon-tag">即将推出</a-tag>
          </div>
        </div>
      </div>

      <!-- 静音时段 -->
      <div class="settings-section">
        <h3 class="section-title">静音时段</h3>
        <p class="section-desc">在指定时段内暂停推送通知。通知不会丢失，静音结束后可在通知中心查看。</p>

        <div class="quiet-hours">
          <div class="quiet-hours-toggle">
            <span class="quiet-label">启用静音时段</span>
            <a-switch v-model="quietHoursEnabled" size="small" @change="handleQuietToggle" />
          </div>
          <div v-if="quietHoursEnabled" class="quiet-hours-range">
            <a-time-picker
              v-model="form.quietHoursStart"
              format="HH:mm"
              placeholder="开始时间"
              size="small"
              style="width: 120px"
              @change="handleSave"
            />
            <span class="quiet-separator">至</span>
            <a-time-picker
              v-model="form.quietHoursEnd"
              format="HH:mm"
              placeholder="结束时间"
              size="small"
              style="width: 120px"
              @change="handleSave"
            />
          </div>
        </div>
      </div>

      <!-- 已静音的工单 -->
      <div class="settings-section">
        <h3 class="section-title">已静音的工单</h3>
        <p class="section-desc">你不会收到这些工单的通知（@提及除外）。取消静音后恢复正常通知推送。</p>

        <div v-if="mutedThreadsLoading" class="muted-loading">
          <a-spin size="small" />
          <span>加载中...</span>
        </div>
        <div v-else-if="mutedThreads.length === 0" class="muted-empty">
          <span class="muted-empty-icon">🔔</span>
          <span class="muted-empty-text">暂无静音的工单</span>
        </div>
        <div v-else class="muted-list">
          <div v-for="thread in mutedThreads" :key="thread.id" class="muted-item">
            <div class="muted-item-info">
              <span class="muted-item-title">{{ thread.resourceTitle }}</span>
              <span class="muted-item-time">{{ formatMutedTime(thread.createdAt) }}</span>
            </div>
            <button class="muted-item-unmute" @click="handleUnmute(thread)">取消静音</button>
          </div>
        </div>
      </div>

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
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { notificationPreferenceApi, notificationApi } from '@/api'
import type { NotificationPreferenceVO } from '@/api/notificationPreference'
import type { MutedThreadVO } from '@/api/notification'
import ProjectNotificationPreferences from './ProjectNotificationPreferences.vue'

const loading = ref(true)
const quietHoursEnabled = ref(false)

const form = reactive({
  onIssueAssigned: true,
  onIssueStatusChanged: true,
  onIssueCommented: true,
  onMentioned: true,
  onIssueResolved: true,
  onSprintStarted: false,
  onSprintCompleted: false,
  onProjectMemberChanged: true,
  onProjectLifecycle: true,
  notifyOwnChanges: false,
  emailEnabled: false,
  quietHoursStart: null as string | null,
  quietHoursEnd: null as string | null
})

let saveTimeout: ReturnType<typeof setTimeout> | null = null

// Muted threads state
const mutedThreads = ref<MutedThreadVO[]>([])
const mutedThreadsLoading = ref(false)

onMounted(async () => {
  await loadPreference()
  await loadMutedThreads()
})

async function loadPreference() {
  try {
    loading.value = true
    const res = await notificationPreferenceApi.get()
    if (res.code === 0 && res.data) {
      applyData(res.data)
    }
  } catch (e: any) {
    Message.error('加载通知偏好失败')
  } finally {
    loading.value = false
  }
}

function applyData(data: NotificationPreferenceVO) {
  form.onIssueAssigned = data.onIssueAssigned
  form.onIssueStatusChanged = data.onIssueStatusChanged
  form.onIssueCommented = data.onIssueCommented
  form.onMentioned = data.onMentioned
  form.onIssueResolved = data.onIssueResolved
  form.onSprintStarted = data.onSprintStarted
  form.onSprintCompleted = data.onSprintCompleted
  form.onProjectMemberChanged = data.onProjectMemberChanged
  form.onProjectLifecycle = data.onProjectLifecycle
  form.notifyOwnChanges = data.notifyOwnChanges
  form.emailEnabled = data.emailEnabled
  form.quietHoursStart = data.quietHoursStart
  form.quietHoursEnd = data.quietHoursEnd
  quietHoursEnabled.value = !!(data.quietHoursStart && data.quietHoursEnd)
}

function handleQuietToggle(enabled: boolean | string | number) {
  if (!enabled) {
    form.quietHoursStart = null
    form.quietHoursEnd = null
    handleSave()
  }
}

function handleSave() {
  // 防抖：多次快速切换只发一次请求
  if (saveTimeout) clearTimeout(saveTimeout)
  saveTimeout = setTimeout(async () => {
    try {
      const res = await notificationPreferenceApi.update({
        onIssueAssigned: form.onIssueAssigned,
        onIssueStatusChanged: form.onIssueStatusChanged,
        onIssueCommented: form.onIssueCommented,
        onMentioned: form.onMentioned,
        onIssueResolved: form.onIssueResolved,
        onSprintStarted: form.onSprintStarted,
        onSprintCompleted: form.onSprintCompleted,
        onProjectMemberChanged: form.onProjectMemberChanged,
        onProjectLifecycle: form.onProjectLifecycle,
        notifyOwnChanges: form.notifyOwnChanges,
        emailEnabled: form.emailEnabled,
        quietHoursStart: form.quietHoursStart,
        quietHoursEnd: form.quietHoursEnd
      })
      if (res.code === 0) {
        Message.success('通知偏好已保存')
      }
    } catch (e: any) {
      Message.error('保存失败，请重试')
    }
  }, 500)
}

async function loadMutedThreads() {
  mutedThreadsLoading.value = true
  try {
    const res = await notificationApi.listMutedThreads()
    if (res.code === 0 && res.data) {
      mutedThreads.value = res.data
    }
  } catch {
    // 静默失败
  } finally {
    mutedThreadsLoading.value = false
  }
}

async function handleUnmute(thread: MutedThreadVO) {
  try {
    const res = await notificationApi.unmuteThread(thread.resourceType, thread.resourceId)
    if (res.code === 0) {
      mutedThreads.value = mutedThreads.value.filter(t => t.id !== thread.id)
      Message.success('已取消静音')
    }
  } catch {
    Message.error('操作失败')
  }
}

function formatMutedTime(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + ' 静音'
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

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0 0 16px 0;
}

.event-group {
  margin-bottom: 20px;
}

.global-toggle {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 20px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.global-toggle-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 480px;
}

.global-toggle-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.global-toggle-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  line-height: 1.4;
}

.event-group-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--tf-border-light);
}

.event-items {
  display: flex;
  flex-direction: column;
}

.event-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.event-item + .event-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.event-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.event-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.event-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* 通知渠道 */
.channel-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.channel-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-icon {
  font-size: 18px;
}

.channel-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.channel-label {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.channel-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.coming-soon-tag {
  margin-left: 8px;
  opacity: 0.7;
}

/* 静音时段 */
.quiet-hours {
  padding: 12px 16px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.quiet-hours-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.quiet-label {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.quiet-hours-range {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border-light);
}

.quiet-separator {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* 底部导航 */
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

/* 已静音工单 */
.muted-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.muted-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.muted-empty-icon {
  font-size: 16px;
  opacity: 0.5;
}

.muted-empty-text {
  color: var(--tf-text-tertiary);
}

.muted-list {
  display: flex;
  flex-direction: column;
}

.muted-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-radius: 6px;
  transition: background 0.15s;
}

.muted-item:hover {
  background: var(--tf-bg-hover);
}

.muted-item + .muted-item {
  border-top: 1px solid var(--tf-border-subtle, rgba(255,255,255,0.04));
}

.muted-item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.muted-item-title {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.muted-item-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.muted-item-unmute {
  padding: 4px 10px;
  border: 1px solid var(--tf-border);
  background: transparent;
  border-radius: 4px;
  font-size: 11px;
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.muted-item-unmute:hover {
  background: var(--tf-bg-hover);
  border-color: var(--tf-accent);
  color: var(--tf-accent);
}
</style>
