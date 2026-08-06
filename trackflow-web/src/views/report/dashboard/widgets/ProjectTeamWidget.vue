<template>
  <div v-if="teamMembers.length > 0" class="widget-project-team">
    <div v-for="member in teamMembers" :key="member.userId" class="team-member-row">
      <div class="member-avatar" :style="{ background: getAvatarColor(member.displayName || member.username) }">
        {{ getInitial(member.displayName || member.username) }}
      </div>
      <div class="member-info">
        <div class="member-name">{{ member.displayName || member.username }}</div>
        <div class="member-role">{{ member.roleName || '—' }}</div>
      </div>
      <div class="member-issue-count" :class="{ 'has-issues': member.openIssueCount > 0 }">
        <span class="count-number">{{ member.openIssueCount }}</span>
        <span class="count-label">工单</span>
      </div>
    </div>
  </div>
  <div v-else class="widget-configure-hint">
    <icon-user-group :size="32" class="hint-icon" />
    <span class="hint-text">{{ !config.projectId ? '点击「编辑配置」选择项目' : '该项目暂无成员' }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { IconUserGroup } from '@arco-design/web-vue/es/icon'
import { dashboardApi } from '@/api/dashboard'
import type { ProjectTeamMemberVO } from '@/api/dashboard'

const props = defineProps<{
  config: Record<string, any>
}>()

const emit = defineEmits<{
  loaded: []
  error: [message: string]
}>()

const teamMembers = ref<ProjectTeamMemberVO[]>([])

// 根据名字生成确定性的头像颜色
const avatarColors = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#14b8a6', '#f97316', '#6366f1'
]

function getAvatarColor(name: string): string {
  if (!name) return avatarColors[0]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return avatarColors[Math.abs(hash) % avatarColors.length]
}

function getInitial(name: string): string {
  if (!name) return '?'
  // CJK names: return first character
  if (/[\u4e00-\u9fff]/.test(name)) {
    return name.charAt(0)
  }
  // Latin names: return first letter uppercase
  return name.charAt(0).toUpperCase()
}

async function loadData(_force = false) {
  try {
    const projectId = props.config.projectId
    if (!projectId) {
      teamMembers.value = []
      emit('loaded')
      return
    }
    const params: { projectId: string; limit?: number } = { projectId }
    if (props.config.limit && props.config.limit > 0) {
      params.limit = props.config.limit
    }
    const res = await dashboardApi.projectTeam(params)
    teamMembers.value = res.data || []
    emit('loaded')
  } catch (e: any) {
    emit('error', e.response?.data?.message || '加载项目团队数据失败')
  }
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped>
.widget-project-team {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  max-height: 100%;
}

.team-member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.team-member-row:hover {
  background: var(--tf-bg-hover);
}

.member-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.member-info {
  flex: 1;
  min-width: 0;
}

.member-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-issue-count {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  min-width: 36px;
}

.count-number {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  line-height: 1.2;
}

.member-issue-count.has-issues .count-number {
  color: var(--tf-accent);
}

.count-label {
  font-size: 10px;
  color: var(--tf-text-quaternary, var(--tf-text-tertiary));
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
