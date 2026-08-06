<template>
  <div class="section">
    <h2 class="section-title">近期活动</h2>
    <div v-if="loading" class="activities-loading">
      <a-spin :size="20" />
    </div>
    <div v-else-if="activities.length > 0" class="activities-list">
      <div v-for="activity in activities" :key="activity.id" class="activity-item">
        <div class="activity-dot"></div>
        <div class="activity-content">
          <div class="activity-main">
            <span class="activity-user">{{ activity.userName }}</span>
            <span class="activity-action">{{ formatActivityAction(activity) }}</span>
          </div>
          <span class="activity-time">{{ formatRelativeTime(activity.createdAt) }}</span>
        </div>
      </div>
    </div>
    <div v-else class="activities-empty">
      <icon-history class="empty-icon" />
      <p class="empty-title">暂无活动记录</p>
      <p class="empty-desc">项目成员的操作记录将在此展示</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconHistory } from '@arco-design/web-vue/es/icon'
import { projectApi } from '@/api'
import type { ProjectActivityVO } from '@/api/types'

const props = defineProps<{
  projectId: string
}>()

const activities = ref<ProjectActivityVO[]>([])
const loading = ref(false)

async function loadActivities() {
  loading.value = true
  try {
    const res = await projectApi.listActivities(props.projectId, { page: 1, pageSize: 10 })
    activities.value = res.data?.list || []
  } catch {
    activities.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.projectId, (newId) => {
  if (newId) loadActivities()
}, { immediate: true })

function formatActivityAction(activity: ProjectActivityVO): string {
  const action = activity.action
  const detail = activity.detail ? (() => { try { return JSON.parse(activity.detail!) } catch { return {} } })() : {}

  switch (action) {
    case 'member_added':
    case 'add_member': {
      const roleName = detail.role_names || detail.role_name || ''
      return roleName
        ? `添加了成员 ${activity.targetUserName || ''}（角色：${roleName}）`
        : `添加了成员 ${activity.targetUserName || ''}`
    }
    case 'member_removed':
    case 'remove_member':
      return `移除了成员 ${activity.targetUserName || ''}`
    case 'member_role_changed':
    case 'change_role': {
      const oldRole = detail.old_role_names || detail.old_role_name || ''
      const newRole = detail.new_role_names || detail.new_role_name || ''
      if (oldRole && newRole) {
        return `将 ${activity.targetUserName || ''} 的角色从「${oldRole}」变更为「${newRole}」`
      }
      return `变更了 ${activity.targetUserName || ''} 的角色`
    }
    case 'project_created':
    case 'create_project':
      return '创建了项目'
    case 'project_updated':
    case 'update_project': {
      const field = detail.field
      if (field === 'name') {
        return `将项目名称从「${detail.old_value || ''}」变更为「${detail.new_value || ''}」`
      } else if (field === 'description') {
        return '更新了项目描述'
      }
      return `更新了项目${detail.fields ? '（' + detail.fields + '）' : '设置'}`
    }
    case 'project_archived':
    case 'archive_project':
      return '归档了项目'
    case 'project_restored':
    case 'restore_project':
      return '恢复了项目'
    case 'lead_changed':
    case 'change_lead':
      return `将负责人变更为 ${activity.targetUserName || ''}`
    case 'visibility_changed':
    case 'change_visibility': {
      const visibilityMap: Record<string, string> = { private: '私有', internal: '内部', public: '公开' }
      const newVis = visibilityMap[detail.new_value] || detail.visibility || detail.new_value || ''
      return `将项目可见性变更为「${newVis}」`
    }
    case 'create_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `创建了迭代「${sprintName}」` : '创建了迭代'
    }
    case 'activate_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `激活了迭代「${sprintName}」` : '激活了迭代'
    }
    case 'complete_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `完成了迭代「${sprintName}」` : '完成了迭代'
    }
    case 'update_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `更新了迭代「${sprintName}」` : '更新了迭代'
    }
    case 'archive_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `归档了迭代「${sprintName}」` : '归档了迭代'
    }
    case 'auto_complete_sprint': {
      const sprintName = detail.sprint_name || detail.name || ''
      return sprintName ? `自动完成了迭代「${sprintName}」` : '自动完成了迭代'
    }
    default:
      return action.replace(/_/g, ' ')
  }
}

function formatRelativeTime(dateStr: string): string {
  const now = new Date()
  const date = new Date(dateStr)
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay < 7) return `${diffDay}天前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}
</script>

<style scoped>
.section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--color-text-1);
}

.activities-loading {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

.activities-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-fill-4);
  margin-top: 6px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.activity-main {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 13px;
}

.activity-user {
  font-weight: 500;
  color: var(--color-text-1);
}

.activity-action {
  color: var(--color-text-2);
}

.activity-time {
  font-size: 12px;
  color: var(--color-text-4);
  white-space: nowrap;
}

.activities-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--color-text-4);
}

.activities-empty .empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.activities-empty .empty-title {
  margin: 0 0 4px;
  font-size: 14px;
  color: var(--color-text-3);
}

.activities-empty .empty-desc {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-4);
}
</style>
