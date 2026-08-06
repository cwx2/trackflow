<template>
  <!-- 通知渠道 -->
  <div class="settings-section">
    <h3 class="section-title">通知渠道</h3>
    <p class="section-desc">配置通知的接收方式。站内通知始终启用；邮件通知可按事件类型独立控制。</p>

    <div class="channel-items">
      <div class="channel-item">
        <div class="channel-info">
          <span class="channel-icon">🔔</span>
          <div class="channel-text">
            <span class="channel-label">站内通知</span>
            <span class="channel-desc">通过系统内通知中心接收（始终启用）</span>
          </div>
        </div>
        <a-tag color="green" size="small">始终启用</a-tag>
      </div>
      <div class="channel-item">
        <div class="channel-info">
          <span class="channel-icon">📧</span>
          <div class="channel-text">
            <span class="channel-label">邮件通知</span>
            <span class="channel-desc">{{ emailStatusDesc }}</span>
          </div>
        </div>
        <a-switch
          v-model="form.emailEnabled"
          size="small"
          :disabled="!emailAvailable"
          @change="onSave"
        />
        <a-tag v-if="!emailAvailable && emailStatusReason" size="small" color="orangered" class="email-unavailable-tag">
          {{ emailStatusReason }}
        </a-tag>
      </div>
    </div>

    <!-- Per-event 邮件渠道控制 -->
    <div v-if="form.emailEnabled && emailAvailable" class="email-per-event">
      <div class="email-per-event-header">
        <span class="email-per-event-title">📬 邮件通知事件选择</span>
        <span class="email-per-event-desc">选择哪些事件除站内通知外还通过邮件发送，确保重要事件不遗漏。</span>
      </div>
      <div class="email-event-list">
        <div class="email-event-group">
          <div class="email-event-group-title">工单事件</div>
          <div class="email-event-items">
            <EmailEventItem label="工单分配给我" v-model="form.emailOnIssueAssigned" @change="onSave" />
            <EmailEventItem label="工单状态变更" v-model="form.emailOnIssueStatusChanged" @change="onSave" />
            <EmailEventItem label="新评论" v-model="form.emailOnIssueCommented" @change="onSave" />
            <EmailEventItem label="@提及我" v-model="form.emailOnMentioned" @change="onSave" />
            <EmailEventItem label="我报告的工单被解决" v-model="form.emailOnIssueResolved" @change="onSave" />
            <EmailEventItem label="工单字段更新" v-model="form.emailOnIssueUpdated" @change="onSave" />
            <EmailEventItem label="关注的工单有更新" v-model="form.emailOnWatchedUpdated" @change="onSave" />
            <EmailEventItem label="工单收到投票" v-model="form.emailOnIssueVoted" @change="onSave" />
            <EmailEventItem label="工时记录变更" v-model="form.emailOnIssueSpentTime" @change="onSave" />
          </div>
        </div>
        <div class="email-event-group">
          <div class="email-event-group-title">项目与迭代</div>
          <div class="email-event-items">
            <EmailEventItem label="Sprint 启动" v-model="form.emailOnSprintStarted" @change="onSave" />
            <EmailEventItem label="Sprint 完成" v-model="form.emailOnSprintCompleted" @change="onSave" />
            <EmailEventItem label="成员变更" v-model="form.emailOnProjectMemberChanged" @change="onSave" />
            <EmailEventItem label="项目归档/恢复" v-model="form.emailOnProjectLifecycle" @change="onSave" />
          </div>
        </div>
        <div class="email-event-group">
          <div class="email-event-group-title">日期提醒</div>
          <div class="email-event-items">
            <EmailEventItem label="即将到期" v-model="form.emailOnDueDate" @change="onSave" />
            <EmailEventItem label="已逾期" v-model="form.emailOnOverdue" @change="onSave" />
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 静音时段 -->
  <div class="settings-section">
    <h3 class="section-title">静音时段</h3>
    <p class="section-desc">在指定时段内不发送邮件通知，避免非工作时间被打扰。站内通知不受影响，静音结束后可在通知中心查看。</p>

    <div class="quiet-hours">
      <div class="quiet-hours-toggle">
        <span class="quiet-label">启用静音时段</span>
        <a-switch :model-value="quietHoursEnabled" size="small" @change="handleQuietToggle" />
      </div>
      <div v-if="quietHoursEnabled" class="quiet-hours-range">
        <a-time-picker
          v-model="form.quietHoursStart"
          format="HH:mm"
          placeholder="开始时间"
          size="small"
          style="width: 120px"
          @change="onSave"
        />
        <span class="quiet-separator">至</span>
        <a-time-picker
          v-model="form.quietHoursEnd"
          format="HH:mm"
          placeholder="结束时间"
          size="small"
          style="width: 120px"
          @change="onSave"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EmailEventItem from './EmailEventItem.vue'
import type { EmailAvailabilityVO } from '@/api/notificationPreference'

interface NotificationForm {
  emailEnabled: boolean
  emailOnIssueAssigned: boolean
  emailOnIssueStatusChanged: boolean
  emailOnIssueCommented: boolean
  emailOnMentioned: boolean
  emailOnIssueResolved: boolean
  emailOnIssueUpdated: boolean
  emailOnSprintStarted: boolean
  emailOnSprintCompleted: boolean
  emailOnProjectMemberChanged: boolean
  emailOnProjectLifecycle: boolean
  emailOnDueDate: boolean
  emailOnOverdue: boolean
  emailOnWatchedUpdated: boolean
  emailOnIssueVoted: boolean
  emailOnIssueSpentTime: boolean
  quietHoursStart: string | null
  quietHoursEnd: string | null
}

const props = defineProps<{
  form: NotificationForm
  emailStatus: EmailAvailabilityVO | null
  emailAvailable: boolean
  emailStatusDesc: string
  emailStatusReason: string | null
}>()

const emit = defineEmits<{
  save: []
}>()

const quietHoursEnabled = computed(() => !!(props.form.quietHoursStart && props.form.quietHoursEnd))

function onSave() {
  emit('save')
}

function handleQuietToggle(enabled: boolean | string | number) {
  if (enabled) {
    // Set default quiet hours when user enables
    if (!props.form.quietHoursStart) {
      props.form.quietHoursStart = '22:00'
    }
    if (!props.form.quietHoursEnd) {
      props.form.quietHoursEnd = '08:00'
    }
    onSave()
  } else {
    props.form.quietHoursStart = null
    props.form.quietHoursEnd = null
    onSave()
  }
}
</script>

<style scoped>
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

.email-unavailable-tag {
  margin-left: 8px;
  opacity: 0.85;
}

.email-per-event {
  margin-top: 16px;
  padding: 16px;
  background: var(--tf-bg-surface, #22252a);
  border-radius: 6px;
  border: 1px solid var(--tf-border-light);
}

.email-per-event-header {
  margin-bottom: 16px;
}

.email-per-event-title {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
  margin-bottom: 4px;
}

.email-per-event-desc {
  display: block;
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.email-event-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.email-event-group-title {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.email-event-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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
</style>
