<template>
  <div class="settings-section">
    <h3 class="section-title">事件订阅</h3>
    <p class="section-desc">选择哪些事件会向你发送站内通知。</p>

    <!-- 全局行为开关 -->
    <div class="global-toggle">
      <div class="global-toggle-info">
        <span class="global-toggle-label">自己的操作也通知我</span>
        <span class="global-toggle-desc">启用后，你对工单的操作（状态变更、评论、分配等）也会生成通知发送给你自己。默认关闭，因为通常不需要被自己的操作通知。</span>
      </div>
      <a-switch v-model="form.notifyOwnChanges" size="small" @change="onSave" />
    </div>

    <div class="event-group">
      <div class="event-group-title">参与中的工单</div>
      <div class="event-items">
        <EventItem label="工单分配给我" desc="有工单被分配给你时通知" v-model="form.onIssueAssigned" @change="onSave" />
        <EventItem label="工单状态变更" desc="你参与的工单状态发生变化时通知" v-model="form.onIssueStatusChanged" @change="onSave" />
        <EventItem label="新评论" desc="你参与的工单有新评论时通知" v-model="form.onIssueCommented" @change="onSave" />
        <EventItem label="@提及我" desc="评论中被 @提及时通知" v-model="form.onMentioned" @change="onSave" />
        <EventItem label="我报告的工单被解决" desc="你创建的工单被标记为已解决时通知" v-model="form.onIssueResolved" @change="onSave" />
        <EventItem label="工单字段更新" desc="你参与的工单优先级、截止日期、描述、迭代、标签等字段变更时通知" v-model="form.onIssueUpdated" @change="onSave" />
        <EventItem label="我关注的工单有更新" desc="你主动关注或自动关注的工单有任何变更时通知" v-model="form.onWatchedUpdated" @change="onSave" />
        <EventItem label="工单收到投票" desc="你参与的工单有人投票时通知" v-model="form.onIssueVoted" @change="onSave" />
        <EventItem label="工时记录变更" desc="你参与的工单有人记录或修改工时时通知" v-model="form.onIssueSpentTime" @change="onSave" />
      </div>
    </div>

    <div class="event-group">
      <div class="event-group-title">项目动态</div>
      <div class="event-items">
        <EventItem label="Sprint 启动" desc="你所在项目有新 Sprint 启动时通知" v-model="form.onSprintStarted" @change="onSave" />
        <EventItem label="Sprint 完成" desc="你所在项目有 Sprint 完成时通知" v-model="form.onSprintCompleted" @change="onSave" />
      </div>
    </div>

    <div class="event-group">
      <div class="event-group-title">项目事件</div>
      <div class="event-items">
        <EventItem label="成员变更" desc="你被添加/移出项目、角色变更、负责人变更时通知" v-model="form.onProjectMemberChanged" @change="onSave" />
        <EventItem label="项目归档/恢复" desc="你所在项目被归档或恢复时通知" v-model="form.onProjectLifecycle" @change="onSave" />
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

  <!-- 自动关注 -->
  <div class="settings-section">
    <h3 class="section-title">自动关注</h3>
    <p class="section-desc">配置哪些操作自动将你加入工单的关注列表。关注后，该工单的后续更新会通知你。</p>

    <div class="event-items auto-watch-items">
      <EventItem label="我创建工单时" desc="创建工单后自动关注该工单" v-model="form.autoWatchOnCreate" @change="onSave" />
      <EventItem label="我发表评论时" desc="在工单中发表评论后自动关注该工单" v-model="form.autoWatchOnComment" @change="onSave" />
      <EventItem label="我修改工单时" desc="更新工单字段后自动关注该工单" v-model="form.autoWatchOnUpdate" @change="onSave" />
      <EventItem label="我被设为负责人时" desc="被分配为工单负责人后自动关注该工单" v-model="form.autoWatchOnAssign" @change="onSave" />
    </div>
  </div>
</template>

<script setup lang="ts">
import EventItem from './EventItem.vue'

interface NotificationForm {
  onIssueAssigned: boolean
  onIssueStatusChanged: boolean
  onIssueCommented: boolean
  onMentioned: boolean
  onIssueResolved: boolean
  onIssueUpdated: boolean
  onSprintStarted: boolean
  onSprintCompleted: boolean
  onProjectMemberChanged: boolean
  onProjectLifecycle: boolean
  notifyOwnChanges: boolean
  onWatchedUpdated: boolean
  onIssueVoted: boolean
  onIssueSpentTime: boolean
  autoWatchOnCreate: boolean
  autoWatchOnComment: boolean
  autoWatchOnUpdate: boolean
  autoWatchOnAssign: boolean
  [key: string]: any
}

defineProps<{
  form: NotificationForm
}>()

const emit = defineEmits<{
  save: []
}>()

function onSave() {
  emit('save')
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

.event-group {
  margin-bottom: 20px;
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
</style>
