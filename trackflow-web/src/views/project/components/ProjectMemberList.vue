<template>
  <div class="section">
    <h2 class="section-title">项目成员</h2>
    <div v-if="loading" class="members-loading">
      <a-spin :size="20" />
    </div>
    <div v-else-if="members.length > 0" class="members-list">
      <div v-for="member in members" :key="member.id" class="member-row">
        <UserAvatar :name="member.displayName || member.username || '?'" :size="28" />

        <div class="member-info">
          <span class="member-name">{{ member.displayName || member.username }}</span>
          <span class="member-email">{{ member.email }}</span>
        </div>
        <span class="member-role">{{ getMemberRoleNames(member) }}</span>
      </div>
    </div>
    <div v-else class="members-empty">
      <p>暂无成员信息</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { projectApi, workflowApi } from '@/api'
import type { ProjectMemberVO } from '@/api/types'
import { UserAvatar } from '@/components/base'

const props = defineProps<{
  projectId: string
}>()

const members = ref<ProjectMemberVO[]>([])
const loading = ref(false)
const roleMap = ref<Record<string, string>>({})

function getRoleName(roleId: string): string {
  return roleMap.value[roleId] || `角色 ${roleId}`
}

function getMemberRoleNames(member: any): string {
  if (member.roleNames && member.roleNames.length > 0) {
    return member.roleNames.join(', ')
  }
  if (member.roleIds && member.roleIds.length > 0) {
    return member.roleIds.map((rid: string) => getRoleName(rid)).join(', ')
  }
  return getRoleName(member.roleId)
}

async function loadMembers() {
  loading.value = true
  try {
    const res = await projectApi.listMembers(props.projectId)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await workflowApi.listProjectRoles()
    const roles = res.data || []
    const map: Record<string, string> = {}
    roles.forEach((r) => { map[String(r.id)] = r.name })
    roleMap.value = map
  } catch {
    roleMap.value = {}
  }
}

onMounted(async () => {
  await Promise.all([loadMembers(), loadRoles()])
})
</script>

<style scoped>
.section { margin-bottom: 32px; }
.section-title { font-size: 14px; font-weight: 600; color: var(--tf-text-primary); margin: 0 0 12px; }

.members-loading { padding: 16px; text-align: center; }

.members-list { display: flex; flex-direction: column; gap: 4px; }

.member-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background 0.15s;
}
.member-row:hover { background: var(--tf-bg-hover); }

.member-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.member-name { font-size: 13px; font-weight: 500; color: var(--tf-text-primary); }
.member-email {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-role {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  background: var(--tf-bg-surface);
  padding: 2px 8px;
  border-radius: 3px;
  white-space: nowrap;
}

.members-empty { font-size: 13px; color: var(--tf-text-tertiary); padding: 16px 0; }
.members-empty p { margin: 0; }
</style>
