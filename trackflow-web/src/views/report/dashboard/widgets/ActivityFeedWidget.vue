<template>
  <div v-if="activityFeedData.length > 0" class="widget-activity-feed">
    <div v-for="item in activityFeedData" :key="item.id" class="activity-item">
      <div class="activity-header">
        <span class="activity-user">{{ item.userName || '系统' }}</span>
        <span class="activity-time">{{ formatActivityTime(item.createdAt) }}</span>
      </div>
      <div class="activity-body">
        <span class="activity-action">{{ formatActivityAction(item.action, item.fieldName) }}</span>
        <span class="activity-issue">{{ item.issueKey }}</span>
        <span v-if="item.issueTitle" class="activity-issue-title">{{ item.issueTitle }}</span>
      </div>
      <div v-if="item.oldValue || item.newValue" class="activity-change">
        <span v-if="item.oldValue" class="change-old">{{ item.oldValue }}</span>
        <span v-if="item.oldValue && item.newValue" class="change-arrow">→</span>
        <span v-if="item.newValue" class="change-new">{{ item.newValue }}</span>
      </div>
    </div>
  </div>
  <div v-else class="widget-configure-hint">
    <icon-notification :size="32" class="hint-icon" />
    <span class="hint-text">暂无活动记录，点击「编辑配置」设置筛选条件</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { IconNotification } from '@arco-design/web-vue/es/icon'
import { dashboardApi } from '@/api/dashboard'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
  'permission-denied': []
}>()

interface ActivityItem {
  id: string
  issueId: string
  issueKey: string
  issueTitle: string
  userId: string
  userName: string
  userAvatar?: string
  action: string
  fieldName?: string
  oldValue?: string
  newValue?: string
  createdAt: string
}

const activityFeedData = ref<ActivityItem[]>([])

const activityActionLabels: Record<string, string> = {
  created: '创建了',
  commented: '评论了',
  status_changed: '变更了状态',
  status_reverted: '撤销了状态变更',
  field_change: '修改了',
  update: '更新了',
  updated: '更新了',
  assigned: '分配了',
  auto_assigned: '自动分配了',
  attachment_added: '添加了附件',
  link_added: '添加了关联',
  link_removed: '移除了关联',
  tag_added: '修改了标签',
  time_logged: '记录了工时',
  time_updated: '更新了工时',
  time_removed: '删除了工时',
  deleted: '删除了',
  restored: '恢复了',
  moved_to_project: '移动了项目',
  create_sprint: '创建了迭代',
  activate_sprint: '激活了迭代',
  complete_sprint: '完成了迭代',
  update_sprint: '更新了迭代',
  archive_sprint: '归档了迭代',
  auto_complete_sprint: '自动完成了迭代'
}

function formatActivityAction(action: string, fieldName?: string): string {
  const label = activityActionLabels[action] || action
  if (action === 'field_change' && fieldName) {
    return `修改了 ${fieldName}`
  }
  return label
}

function formatActivityTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function loadData(_force = false) {
  try {
    const config = props.config
    const params: Record<string, any> = {}
    if (config.projectIds && config.projectIds.length > 0) params.projectIds = config.projectIds
    if (config.actions && config.actions.length > 0) params.actions = config.actions
    if (config.userIds && config.userIds.length > 0) params.userIds = config.userIds
    params.limit = config.limit || 10
    const res = await dashboardApi.activityFeed(params)
    activityFeedData.value = res.data || []
    emit('loaded')
  } catch (e: any) {
    const status = e.response?.status
    if (status === 403) {
      emit('permission-denied')
    } else {
      emit('error', e.response?.data?.message || '加载活动数据失败')
    }
  }
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-activity-feed {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  max-height: 100%;
}

.activity-item {
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.activity-item:hover {
  background: var(--tf-bg-hover);
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.activity-user {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.activity-body {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.activity-action {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

.activity-issue {
  font-size: 11px;
  font-weight: 500;
  color: var(--tf-accent);
  white-space: nowrap;
}

.activity-issue-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.activity-change {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
}

.change-old {
  color: var(--tf-text-tertiary);
  text-decoration: line-through;
}

.change-arrow {
  color: var(--tf-text-quaternary);
}

.change-new {
  color: var(--tf-text-secondary);
  font-weight: 500;
}

.widget-configure-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 8px;
  padding: 12px;
}

.hint-icon {
  color: var(--tf-text-tertiary);
  opacity: 0.4;
}

.hint-text {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-align: center;
  line-height: 1.4;
}
</style>
