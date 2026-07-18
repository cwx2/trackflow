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
          <span>邮件通知需要配置 SMTP 服务器连接。请在下方填写邮件服务器信息。</span>
        </div>
      </div>

      <!-- SMTP 邮件服务器配置（仅在邮件通知启用时显示） -->
      <div v-if="form.emailEnabled" class="settings-section">
        <div class="section-header">
          <h2 class="section-title">⚙️ 邮件服务器配置</h2>
          <p class="section-desc">配置 SMTP 服务器连接参数。密码加密存储，不会明文展示。</p>
        </div>

        <div class="email-config-form">
          <div class="email-form-row">
            <div class="email-form-field">
              <label class="field-label">连接协议</label>
              <a-select
                v-model="emailConfig.protocol"
                :style="{ width: '100%' }"
                placeholder="选择协议"
              >
                <a-option value="plain">SMTP（明文）</a-option>
                <a-option value="ssl">SMTPS（SSL）</a-option>
                <a-option value="starttls">STARTTLS</a-option>
              </a-select>
              <p class="field-hint">推荐使用 STARTTLS 或 SSL 加密连接</p>
            </div>
          </div>

          <div class="email-form-row email-form-row--two-col">
            <div class="email-form-field">
              <label class="field-label">服务器地址 <span class="required">*</span></label>
              <a-input
                v-model="emailConfig.host"
                placeholder="如 smtp.company.com"
              />
            </div>
            <div class="email-form-field email-form-field--port">
              <label class="field-label">端口 <span class="required">*</span></label>
              <a-input-number
                v-model="emailConfig.port"
                :min="1"
                :max="65535"
                :style="{ width: '100%' }"
                placeholder="587"
              />
            </div>
          </div>

          <div class="email-form-row email-form-row--two-col">
            <div class="email-form-field">
              <label class="field-label">用户名</label>
              <a-input
                v-model="emailConfig.username"
                placeholder="SMTP 认证用户名"
              />
            </div>
            <div class="email-form-field">
              <label class="field-label">密码</label>
              <a-input-password
                v-model="emailConfig.password"
                :placeholder="emailConfig.passwordConfigured ? '已配置（留空不修改）' : '输入 SMTP 密码'"
              />
            </div>
          </div>

          <div class="email-form-row">
            <div class="email-form-field email-form-field--switch">
              <label class="field-label">启用 SSL 验证</label>
              <a-switch v-model="emailConfig.sslEnabled" size="small" />
            </div>
          </div>

          <div class="email-form-row email-form-row--two-col">
            <div class="email-form-field">
              <label class="field-label">发件人地址 <span class="required">*</span></label>
              <a-input
                v-model="emailConfig.fromAddress"
                placeholder="noreply@company.com"
              />
              <p class="field-hint">邮件的 From 地址</p>
            </div>
            <div class="email-form-field">
              <label class="field-label">回复地址</label>
              <a-input
                v-model="emailConfig.replyToAddress"
                placeholder="support@company.com（可选）"
              />
              <p class="field-hint">邮件的 Reply-To 地址</p>
            </div>
          </div>

          <div class="email-config-status">
            <span v-if="emailConfigStatus === 'configured'" class="status-badge status-badge--ok">
              ✓ 已配置
            </span>
            <span v-else class="status-badge status-badge--incomplete">
              ○ 未完成配置
            </span>
          </div>

          <div class="email-config-actions">
            <a-button type="primary" size="small" :loading="savingEmail" @click="saveEmailConfig">
              保存邮件配置
            </a-button>
          </div>

          <!-- 发送测试邮件 -->
          <div v-if="emailConfigStatus === 'configured'" class="test-email-section">
            <div class="test-email-header">
              <h3 class="test-email-title">📤 发送测试邮件</h3>
              <p class="test-email-desc">验证 SMTP 配置是否可以正常发送邮件</p>
            </div>
            <div class="test-email-form">
              <a-input
                v-model="testEmailAddress"
                placeholder="输入接收测试邮件的地址"
                :style="{ flex: 1 }"
              />
              <a-button
                type="outline"
                size="small"
                :loading="sendingTest"
                @click="sendTestEmail"
              >
                发送测试邮件
              </a-button>
            </div>
          </div>
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
          <h3 class="pref-group-title">通知行为</h3>
          <div class="pref-list">
            <div class="pref-item">
              <div class="pref-info">
                <span class="pref-name">自己的操作也通知我</span>
                <span class="pref-desc">是否默认接收自己操作产生的通知（通常建议关闭）</span>
              </div>
              <a-switch v-model="form.defaultNotifyOwnChanges" size="small" />
            </div>
          </div>
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
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { Message } from '@arco-design/web-vue'
import { notificationAdminApi } from '@/api/notificationAdmin'
import type { NotificationSettingsVO, NotificationStatsVO, EmailConfigVO } from '@/api/notificationAdmin'

const loading = ref(true)
const saving = ref(false)
const savingEmail = ref(false)
const sendingTest = ref(false)
const testEmailAddress = ref('')

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
  defaultNotifyOwnChanges: false,
  defaultEmailEnabled: false
})

const emailConfig = reactive({
  host: '',
  port: 587,
  protocol: 'starttls',
  username: '',
  password: '',
  sslEnabled: false,
  fromAddress: '',
  replyToAddress: '',
  passwordConfigured: false
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

const emailConfigStatus = computed(() => {
  if (emailConfig.host && emailConfig.port && emailConfig.fromAddress) {
    return 'configured'
  }
  return 'incomplete'
})

// 通知类型名称映射（与后端 NotificationType 枚举同步）
const typeLabels: Record<string, string> = {
  issue_assigned: '工单分配',
  issue_auto_assigned: '工单自动分配',
  issue_commented: '工单评论',
  issue_status_changed: '状态变更',
  mention: '@ 提及',
  member_added: '成员添加',
  member_removed: '成员移除',
  role_changed: '角色变更',
  lead_changed: '负责人变更',
  project_archived: '项目归档',
  project_restored: '项目恢复',
  project_deleted: '项目删除',
  sprint_started: 'Sprint 开始',
  sprint_completed: 'Sprint 完成',
  due_date_alert: '到期提醒',
  overdue_alert: '逾期提醒'
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

    // 如果邮件已启用，加载邮件配置
    if (form.emailEnabled) {
      await loadEmailConfig()
    }
  } catch (e) {
    Message.error('加载通知管理设置失败')
  } finally {
    loading.value = false
  }
}

async function loadEmailConfig() {
  try {
    const res = await notificationAdminApi.getEmailConfig()
    if (res.code === 0 && res.data) {
      emailConfig.host = res.data.host || ''
      emailConfig.port = res.data.port || 587
      emailConfig.protocol = res.data.protocol || 'starttls'
      emailConfig.username = res.data.username || ''
      emailConfig.sslEnabled = res.data.sslEnabled || false
      emailConfig.fromAddress = res.data.fromAddress || ''
      emailConfig.replyToAddress = res.data.replyToAddress || ''
      emailConfig.password = ''
      emailConfig.passwordConfigured = res.data.password === '******'
    }
  } catch (e) {
    // 静默失败，邮件配置可能尚未保存过
  }
}

// 当邮件开关从关到开时，加载邮件配置
watch(() => form.emailEnabled, (newVal, oldVal) => {
  if (newVal && !oldVal) {
    loadEmailConfig()
  }
})

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
      defaultNotifyOwnChanges: form.defaultNotifyOwnChanges,
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

async function saveEmailConfig() {
  // 校验必填
  if (!emailConfig.host) {
    Message.warning('请填写服务器地址')
    return
  }
  if (!emailConfig.fromAddress) {
    Message.warning('请填写发件人地址')
    return
  }

  savingEmail.value = true
  try {
    const dto: Record<string, any> = {
      host: emailConfig.host,
      port: emailConfig.port,
      protocol: emailConfig.protocol,
      username: emailConfig.username,
      sslEnabled: emailConfig.sslEnabled,
      fromAddress: emailConfig.fromAddress,
      replyToAddress: emailConfig.replyToAddress
    }
    // 仅当用户输入了新密码时才发送
    if (emailConfig.password) {
      dto.password = emailConfig.password
    }

    const res = await notificationAdminApi.updateEmailConfig(dto)
    if (res.code === 0 && res.data) {
      emailConfig.password = ''
      emailConfig.passwordConfigured = res.data.password === '******'
      Message.success('邮件服务器配置已保存')
    }
  } catch (e: any) {
    Message.error(e.response?.data?.message || '保存邮件配置失败')
  } finally {
    savingEmail.value = false
  }
}

async function sendTestEmail() {
  if (!testEmailAddress.value) {
    Message.warning('请输入接收测试邮件的地址')
    return
  }
  // Simple email format check
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(testEmailAddress.value)) {
    Message.warning('邮件地址格式不正确')
    return
  }

  sendingTest.value = true
  try {
    const res = await notificationAdminApi.sendTestEmail({ toAddress: testEmailAddress.value })
    if (res.code === 0) {
      Message.success(`测试邮件已发送至 ${testEmailAddress.value}`)
    } else {
      Message.error(res.message || '测试邮件发送失败')
    }
  } catch (e: any) {
    const msg = e.response?.data?.message || '测试邮件发送失败'
    Message.error(msg)
  } finally {
    sendingTest.value = false
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

/* Email config form */
.email-config-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.email-form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.email-form-row--two-col {
  flex-direction: row;
  gap: 16px;
}

.email-form-row--two-col .email-form-field {
  flex: 1;
}

.email-form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.email-form-field--port {
  max-width: 120px;
}

.email-form-field--switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}

.email-form-field .field-label {
  margin-bottom: 0;
}

.required {
  color: var(--color-danger-6, #f53f3f);
}

.email-config-status {
  padding: 8px 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 3px;
}

.status-badge--ok {
  color: var(--color-success-6, #00b42a);
  background: var(--color-success-light-1, rgba(0, 180, 42, 0.1));
}

.status-badge--incomplete {
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
}

.email-config-actions {
  display: flex;
  gap: 12px;
}

/* Test email section */
.test-email-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--tf-border-light);
}

.test-email-header {
  margin-bottom: 12px;
}

.test-email-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin: 0 0 4px 0;
}

.test-email-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin: 0;
}

.test-email-form {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
