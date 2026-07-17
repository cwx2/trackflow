<template>
  <div class="notification-management">
    <!-- Header -->
    <div class="settings-header">
      <router-link to="/admin" class="back-link">← 返回管理</router-link>
      <h1 class="settings-title">通知管理</h1>
      <p class="settings-desc">配置全局通知策略、默认偏好和保留策略。管理系统级通知行为。</p>
    </div>

    <div v-if="loading" class="settings-loading">
      <a-spin :size="20" />
      <span>加载中...</span>
    </div>

    <template v-else>
      <!-- 通知统计概览 -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">📊 通知统计</h2>
          <p class="section-desc">系统通知的整体概况</p>
        </div>
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-value">{{ stats.totalCount }}</div>
            <div class="stat-label">总通知数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value stat-unread">{{ stats.unreadCount }}</div>
            <div class="stat-label">未读通知</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.todayCount }}</div>
            <div class="stat-label">今日发送</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ stats.weekCount }}</div>
            <div class="stat-label">本周发送</div>
          </div>
        </div>
        <div v-if="Object.keys(stats.typeDistribution).length > 0" class="type-distribution">
          <h3 class="dist-title">类型分布</h3>
          <div class="dist-bars">
            <div
              v-for="(count, type) in stats.typeDistribution"
              :key="type"
              class="dist-item"
            >
              <span class="dist-type">{{ formatType(type as string) }}</span>
              <div class="dist-bar-wrapper">
                <div class="dist-bar" :style="{ width: getBarWidth(count as number) }"></div>
              </div>
              <span class="dist-count">{{ count }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 通知渠道 -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">📡 通知渠道</h2>
          <p class="section-desc">控制系统级的通知发送渠道。关闭后所有用户均不会收到对应渠道的通知。</p>
        </div>
        <div class="channel-list">
          <div class="channel-item">
            <div class="channel-info">
              <span class="channel-name">🔔 站内通知</span>
              <span class="channel-desc">系统内实时通知推送</span>
            </div>
            <a-switch
              v-model="form.inAppEnabled"
              size="small"
            />
          </div>
          <div class="channel-item">
            <div class="channel-info">
              <span class="channel-name">📧 邮件通知</span>
              <span class="channel-desc">通过 SMTP 发送邮件通知（需配置邮件服务器）</span>
            </div>
            <a-switch
              v-model="form.emailEnabled"
              size="small"
            />
          </div>
        </div>
        <div v-if="form.emailEnabled" class="email-hint">
          <span class="hint-icon">⚠️</span>
          <span>邮件通知需要配置 SMTP 服务器连接。当前尚未对接邮件服务，启用后仅记录日志。</span>
        </div>
      </div>

      <!-- 保留策略 -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">🗄️ 保留策略</h2>
          <p class="section-desc">配置已读通知的自动清理策略。系统每天凌晨 2:30 执行清理任务。</p>
        </div>
        <div class="field-row">
          <label class="field-label">已读通知保留天数</label>
          <div class="field-input">
            <a-input-number
              v-model="form.retentionDays"
              :min="0"
              :max="365"
              :step="1"
              :style="{ width: '120px' }"
            />
            <span class="field-suffix">天</span>
          </div>
          <p class="field-hint">设为 0 表示永久保留（不自动清理）。默认 90 天。</p>
        </div>
      </div>

      <!-- 新用户默认偏好 -->
      <div class="settings-section">
        <div class="section-header">
          <h2 class="section-title">👤 新用户默认偏好</h2>
          <p class="section-desc">当新用户首次登录时，使用以下默认通知偏好。用户可在个人设置中自行修改。</p>
        </div>

        <div class="pref-group">
          <h3 class="pref-group-title">工单事件</h3>
          <div class="pref-list">
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">工单被分配给我</span>
              </div>
              <a-switch v-model="form.defaultOnIssueAssigned" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">工单状态变更</span>
              </div>
              <a-switch v-model="form.defaultOnIssueStatusChanged" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">工单被评论</span>
              </div>
              <a-switch v-model="form.defaultOnIssueCommented" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">被 @ 提及</span>
              </div>
              <a-switch v-model="form.defaultOnMentioned" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">工单被解决</span>
              </div>
              <a-switch v-model="form.defaultOnIssueResolved" size="small" />
            </div>
          </div>
        </div>

        <div class="pref-group">
          <h3 class="pref-group-title">Sprint 事件</h3>
          <div class="pref-list">
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">Sprint 开始</span>
              </div>
              <a-switch v-model="form.defaultOnSprintStarted" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">Sprint 完成</span>
              </div>
              <a-switch v-model="form.defaultOnSprintCompleted" size="small" />
            </div>
          </div>
        </div>

        <div class="pref-group">
          <h3 class="pref-group-title">项目事件</h3>
          <div class="pref-list">
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">项目成员变更</span>
              </div>
              <a-switch v-model="form.defaultOnProjectMemberChanged" size="small" />
            </div>
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">项目生命周期</span>
              </div>
              <a-switch v-model="form.defaultOnProjectLifecycle" size="small" />
            </div>
          </div>
        </div>

        <div class="pref-group">
          <h3 class="pref-group-title">邮件通知</h3>
          <div class="pref-list">
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">默认启用邮件通知</span>
                <span class="pref-desc">新用户是否默认开启邮件通知（需先开启全局邮件渠道）</span>
              </div>
              <a-switch v-model="form.defaultEmailEnabled" size="small" />
            </div>
          </div>
        </div>
      </div>

      <!-- Save actions -->
      <div class="settings-actions">
        <a-button type="primary" :loading="saving" @click="saveSettings">
          保存设置
        </a-button>
        <a-button @click="resetForm">
          重置
        </a-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message } from '@arco-design/web-vue'
import { notificationAdminApi } from '@/api/notificationAdmin'
import type { NotificationSettingsVO, NotificationStatsVO } from '@/api/notificationAdmin'

const loading = ref(true)
const saving = ref(false)

const form = reactive({
  inAppEnabled: true,
  emailEnabled: false,
  retentionDays: 90,
  defaultOnIssueAssigned: true,
  defaultOnIssueStatusChanged: true,
  defaultOnIssueCommented: true,
  defaultOnMentioned: true,
  defaultOnIssueResolved: true,
  defaultOnSprintStarted: false,
  defaultOnSprintCompleted: false,
  defaultOnProjectMemberChanged: true,
  defaultOnProjectLifecycle: true,
  defaultEmailEnabled: false
})

const stats = reactive<NotificationStatsVO>({
  totalCount: 0,
  unreadCount: 0,
  readCount: 0,
  todayCount: 0,
  weekCount: 0,
  typeDistribution: {}
})

let originalSettings: NotificationSettingsVO | null = null

// 通知类型名称映射
const typeLabels: Record<string, string> = {
  issue_assigned: '工单分配',
  issue_status_changed: '状态变更',
  issue_commented: '工单评论',
  mention: '@ 提及',
  issue_resolved: '工单解决',
  sprint_started: 'Sprint 开始',
  sprint_completed: 'Sprint 完成',
  project_member_changed: '成员变更',
  project_lifecycle: '项目生命周期'
}

function formatType(type: string): string {
  return typeLabels[type] || type
}

function getBarWidth(count: number): string {
  if (stats.totalCount === 0) return '0%'
  const pct = Math.max(2, (count / stats.totalCount) * 100)
  return `${pct}%`
}

async function loadData() {
  loading.value = true
  try {
    const [settingsRes, statsRes] = await Promise.all([
      notificationAdminApi.getSettings(),
      notificationAdminApi.getStats()
    ])

    if (settingsRes.code === 0 && settingsRes.data) {
      Object.assign(form, settingsRes.data)
      originalSettings = { ...settingsRes.data }
    }

    if (statsRes.code === 0 && statsRes.data) {
      Object.assign(stats, statsRes.data)
    }
  } catch (e) {
    Message.error('加载通知管理设置失败')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    const res = await notificationAdminApi.updateSettings({
      inAppEnabled: form.inAppEnabled,
      emailEnabled: form.emailEnabled,
      retentionDays: form.retentionDays,
      defaultOnIssueAssigned: form.defaultOnIssueAssigned,
      defaultOnIssueStatusChanged: form.defaultOnIssueStatusChanged,
      defaultOnIssueCommented: form.defaultOnIssueCommented,
      defaultOnMentioned: form.defaultOnMentioned,
      defaultOnIssueResolved: form.defaultOnIssueResolved,
      defaultOnSprintStarted: form.defaultOnSprintStarted,
      defaultOnSprintCompleted: form.defaultOnSprintCompleted,
      defaultOnProjectMemberChanged: form.defaultOnProjectMemberChanged,
      defaultOnProjectLifecycle: form.defaultOnProjectLifecycle,
      defaultEmailEnabled: form.defaultEmailEnabled
    })
    if (res.code === 0 && res.data) {
      originalSettings = { ...res.data }
      Message.success('通知设置已保存')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  if (originalSettings) {
    Object.assign(form, originalSettings)
  }
}

onMounted(loadData)
</script>

<style scoped>
.notification-management {
  padding: 32px;
  max-width: 720px;
  height: 100%;
  overflow-y: auto;
}

.settings-header {
  margin-bottom: 32px;
}

.back-link {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-decoration: none;
  display: inline-block;
  margin-bottom: 12px;
  transition: color 0.15s;
}

.back-link:hover {
  color: var(--tf-accent);
}

.settings-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 8px 0;
}

.settings-desc {
  font-size: 13px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.5;
}

.settings-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--tf-text-tertiary);
  font-size: 13px;
}

.settings-section {
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--tf-border-light);
}

.settings-section:last-of-type {
  border-bottom: none;
}

.section-header {
  margin-bottom: 16px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.section-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin: 0;
  line-height: 1.4;
}

/* Stats */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  padding: 12px;
  text-align: center;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.stat-value.stat-unread {
  color: var(--tf-accent);
}

.stat-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.type-distribution {
  margin-top: 16px;
}

.dist-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin: 0 0 8px 0;
}

.dist-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dist-item {
  display: grid;
  grid-template-columns: 100px 1fr 40px;
  align-items: center;
  gap: 8px;
}

.dist-type {
  font-size: 11px;
  color: var(--tf-text-secondary);
  text-align: right;
}

.dist-bar-wrapper {
  height: 6px;
  background: var(--tf-bg-body);
  border-radius: 3px;
  overflow: hidden;
}

.dist-bar {
  height: 100%;
  background: var(--tf-accent);
  border-radius: 3px;
  transition: width 0.3s ease;
  min-width: 2px;
}

.dist-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: right;
}

/* Channel */
.channel-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.channel-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--tf-border-light);
}

.channel-item:last-child {
  border-bottom: none;
}

.channel-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.channel-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.channel-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.email-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--color-warning-light-4, #ffe4b0);
  border-radius: 4px;
  font-size: 11px;
  color: var(--tf-text-secondary);
  line-height: 1.4;
}

.hint-icon {
  flex-shrink: 0;
}

/* Field row */
.field-row {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 6px;
}

.field-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-suffix {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.field-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin: 4px 0 0 0;
}

/* Preference groups */
.pref-group {
  margin-bottom: 20px;
}

.pref-group:last-child {
  margin-bottom: 0;
}

.pref-group-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  margin: 0 0 8px 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.pref-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.pref-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--tf-border-light);
}

.pref-item:last-child {
  border-bottom: none;
}

.pref-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pref-name {
  font-size: 13px;
  color: var(--tf-text-primary);
}

.pref-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

/* Actions */
.settings-actions {
  display: flex;
  gap: 12px;
  padding-top: 8px;
}
</style>
