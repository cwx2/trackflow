<template>
  <div class="settings-members">
    <!-- 归档提示 -->
    <div v-if="isArchived" class="archived-notice">
      <icon-lock class="notice-icon" />
      <span>项目已归档，成员管理为只读状态</span>
    </div>

    <!-- 添加成员区域 -->
    <div v-if="canManage && !isArchived" class="add-member-section">
      <div class="add-member-row">
        <a-select
          v-model="addForm.userId"
          placeholder="搜索并选择用户..."
          allow-search
          style="flex: 1"
          :loading="usersLoading"
          @focus="loadAvailableUsers"
          @search="handleUserSearch"
        >
          <a-option v-for="u in availableUsers" :key="u.id" :value="u.id">
            {{ u.displayName || u.username }}
            <span v-if="u.email" class="option-hint">{{ u.email }}</span>
          </a-option>
        </a-select>
        <a-select
          v-model="addForm.roleIds"
          placeholder="选择角色..."
          multiple
          style="width: 220px"
          :loading="rolesLoading"
          @focus="loadRolesIfNeeded"
        >
          <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
            {{ r.name }}
          </a-option>
        </a-select>
        <a-button
          type="primary"
          :disabled="!addForm.userId || addForm.roleIds.length === 0"
          :loading="adding"
          @click="addMember"
        >
          添加成员
        </a-button>
      </div>
    </div>

    <!-- 成员列表 -->
    <div class="members-section">
      <div class="members-header">
        <span class="members-count">{{ members.length }} 名成员</span>
      </div>

      <div v-if="membersLoading" class="loading-state">
        <a-spin :size="20" />
      </div>

      <a-table
        v-else
        :data="members"
        :pagination="false"
        :bordered="false"
        size="medium"
        row-key="userId"
        class="members-table"
      >
        <template #columns>
          <a-table-column title="用户" data-index="displayName" :width="280">
            <template #cell="{ record }">
              <div class="member-cell">
                <UserAvatar :name="record.displayName || record.username || '?'" :size="32" />
                <div class="member-info">
                  <span class="member-name">{{ record.displayName || record.username }}</span>
                  <span class="member-email">{{ record.email || '' }}</span>
                </div>
              </div>
            </template>
          </a-table-column>
          <a-table-column title="角色" :width="240">
            <template #cell="{ record }">
              <template v-if="canManage && !isArchived">
                <a-select
                  :model-value="record.roleIds || [record.roleId]"
                  size="small"
                  multiple
                  :max-tag-count="2"
                  @change="(val: any) => changeMemberRole(record.userId, val)"
                  @focus="loadRolesIfNeeded"
                >
                  <a-option v-for="r in projectRoles" :key="r.id" :value="r.id">
                    {{ r.name }}
                  </a-option>
                </a-select>
              </template>
              <template v-else>
                <span class="role-tags">
                  <a-tag v-for="name in getMemberRoleNames(record)" :key="name" size="small">
                    {{ name }}
                  </a-tag>
                </span>
              </template>
            </template>
          </a-table-column>
          <a-table-column title="加入时间" data-index="joinedAt" :width="140">
            <template #cell="{ record }">
              <span class="date-text">{{ formatDate(record.joinedAt) }}</span>
            </template>
          </a-table-column>
          <a-table-column v-if="canManage && !isArchived" title="操作" :width="80" align="center">
            <template #cell="{ record }">
              <a-button
                type="text"
                size="small"
                status="danger"
                @click="confirmRemoveMember(record)"
              >
                移除
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </div>

    <!-- 活动日志 Section -->
    <div class="activity-section">
      <h3 class="section-title">成员活动日志</h3>
      <div v-if="activityLoading" class="loading-state">
        <a-spin :size="20" />
      </div>
      <EmptyState v-else-if="activities.length === 0" icon="calendar" title="暂无活动记录" description="成员变动操作将记录在此处" :compact="true" />
      <div v-else class="activity-list">
        <div v-for="act in activities" :key="act.id" class="activity-item">
          <div class="activity-dot" :class="getActivityDotClass(act.action)"></div>
          <div class="activity-content">
            <span class="activity-text">{{ formatActivityText(act) }}</span>
            <span class="activity-time">{{ formatRelativeTime(act.createdAt) }}</span>
          </div>
        </div>
        <div v-if="activityHasMore" class="load-more">
          <a-button type="text" size="small" @click="loadMoreActivities">加载更多</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { IconLock } from '@arco-design/web-vue/es/icon'
import { Message } from '@arco-design/web-vue'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { projectApi, userApi, workflowApi } from '@/api'
import type { ProjectDetailVO, ProjectMemberVO, ProjectActivityVO } from '@/api/types'
import { UserAvatar, EmptyState } from '@/components/base'

const props = defineProps<{
  project: ProjectDetailVO
  canManage: boolean
  isArchived: boolean
}>()

// Members
const members = ref<ProjectMemberVO[]>([])
const membersLoading = ref(false)

// Add member form
const addForm = reactive({
  userId: undefined as string | undefined,
  roleIds: [] as string[]
})
const adding = ref(false)

// Available users (for adding)
const availableUsers = ref<any[]>([])
const usersLoading = ref(false)
const usersLoaded = ref(false)

// Project roles
const projectRoles = ref<{ id: string; name: string }[]>([])
const rolesLoading = ref(false)
const rolesLoaded = ref(false)

// Activity
const activities = ref<ProjectActivityVO[]>([])
const activityLoading = ref(false)
const activityPage = ref(1)
const activityHasMore = ref(false)

// Color pool


function getMemberRoleNames(member: ProjectMemberVO): string[] {
  if (member.roleNames && member.roleNames.length > 0) {
    return member.roleNames
  }
  return ['未分配']
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
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
  return formatDate(dateStr)
}

function getActivityDotClass(action: string): string {
  if (action === 'add_member' || action === 'member_added') return 'dot-add'
  if (action === 'remove_member' || action === 'member_removed') return 'dot-remove'
  return 'dot-change'
}

function formatActivityText(act: ProjectActivityVO): string {
  const operator = act.userName || '未知用户'
  const target = act.targetUserName || '未知用户'
  let detail: any = {}
  try { detail = act.detail ? JSON.parse(act.detail) : {} } catch { /* JSON 解析容错，降级为空对象 */ }

  switch (act.action) {
    case 'member_added':
    case 'add_member': {
      const roleName = detail.role_names || detail.role_name || ''
      return roleName
        ? `${operator} 添加了成员 ${target}（角色：${roleName}）`
        : `${operator} 添加了成员 ${target}`
    }
    case 'member_removed':
    case 'remove_member':
      return `${operator} 移除了成员 ${target}`
    case 'member_role_changed':
    case 'change_role': {
      const oldRole = detail.old_role_names || detail.old_role_name || '未知角色'
      const newRole = detail.new_role_names || detail.new_role_name || '未知角色'
      return `${operator} 将 ${target} 的角色从「${oldRole}」变更为「${newRole}」`
    }
    case 'change_lead':
    case 'lead_changed': {
      const newLead = detail.new_lead_name || target
      return `${operator} 将负责人变更为 ${newLead}`
    }
    case 'update_project':
    case 'project_updated': {
      const field = detail.field
      if (field === 'name') {
        return `${operator} 将项目名称从「${detail.old_value || ''}」变更为「${detail.new_value || ''}」`
      } else if (field === 'description') {
        return `${operator} 更新了项目描述`
      }
      return `${operator} 更新了项目设置`
    }
    case 'change_visibility':
    case 'visibility_changed': {
      const visibilityMap: Record<string, string> = { private: '私有', internal: '内部', public: '公开' }
      const oldVis = visibilityMap[detail.old_value] || detail.old_value || ''
      const newVis = visibilityMap[detail.new_value] || detail.new_value || ''
      return `${operator} 将项目可见性从「${oldVis}」变更为「${newVis}」`
    }
    case 'archive_project':
    case 'project_archived': {
      const sprintInfo = detail.suspended_sprint_count
        ? `（${detail.suspended_sprint_count} 个活跃 Sprint 已暂停）`
        : ''
      return `${operator} 归档了项目${sprintInfo}`
    }
    case 'restore_project':
    case 'project_restored':
      return `${operator} 恢复了项目`
    default:
      return `${operator} ${act.action.replace(/_/g, ' ')}`
  }
}

// Load members
async function loadMembers() {
  membersLoading.value = true
  try {
    const res = await projectApi.listMembers(props.project.key)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

// Load available users (for adding)
async function loadAvailableUsers() {
  if (usersLoaded.value) return
  usersLoading.value = true
  try {
    const res = await userApi.list({ page: 1, pageSize: 200 })
    const allUsers = res.data?.list || []
    // Filter out already members
    const memberIds = new Set(members.value.map(m => m.userId))
    availableUsers.value = allUsers.filter(u => !memberIds.has(u.id))
    usersLoaded.value = true
  } catch {
    availableUsers.value = []
  } finally {
    usersLoading.value = false
  }
}

function handleUserSearch(_keyword: string) {
  // Arco allow-search handles filtering automatically
}

// Load roles
async function loadRolesIfNeeded() {
  if (rolesLoaded.value) return
  rolesLoading.value = true
  try {
    const res = await workflowApi.listProjectRoles()
    projectRoles.value = (res.data || []).map((r: any) => ({ id: String(r.id), name: r.name }))
    rolesLoaded.value = true
  } catch {
    projectRoles.value = []
  } finally {
    rolesLoading.value = false
  }
}

// Add member
async function addMember() {
  if (!addForm.userId || addForm.roleIds.length === 0) return
  adding.value = true
  try {
    await projectApi.addMember(props.project.key, {
      userId: addForm.userId,
      roleIds: addForm.roleIds.map(id => Number(id))
    })
    Message.success('成员已添加')
    addForm.userId = undefined
    addForm.roleIds = []
    usersLoaded.value = false // refresh available users
    await loadMembers()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '添加成员失败')
  } finally {
    adding.value = false
  }
}

// Change member role
async function changeMemberRole(userId: string, roleIds: string[]) {
  if (!roleIds || roleIds.length === 0) {
    Message.warning('至少需要分配一个角色')
    return
  }
  try {
    const res = await projectApi.updateMemberRole(props.project.key, userId, roleIds.map(id => Number(id)))
    const affectedCount = res.data?.affectedIssueCount || 0
    if (affectedCount > 0) {
      Message.success(`角色已更新，已清空 ${affectedCount} 个工单的负责人（因角色变更后该用户不再可被分配）`)
    } else {
      Message.success('角色已更新')
    }
    await loadMembers()
  } catch (e: any) {
    Message.error(e.response?.data?.message || '更新角色失败')
  }
}

// Remove member
async function confirmRemoveMember(member: ProjectMemberVO) {
  // First check assigned issues
  try {
    const res = await projectApi.getAssignedIssueCount(props.project.key, member.userId)
    const count = res.data?.count || 0

    if (count > 0) {
      const { confirmDangerDelete } = useConfirmDelete()
      confirmDangerDelete({
        itemName: `成员「${member.displayName || member.username}」`,
        impactDescription: `当前有 ${count} 个工单指派，移除后这些工单的负责人将被清空`,
        confirmText: '确认移除',
        onConfirm: async () => {
          try {
            await projectApi.removeMember(props.project.key, member.userId)
            Message.success('成员已移除')
            usersLoaded.value = false
            await loadMembers()
          } catch (e: any) {
            Message.error(e.response?.data?.message || '移除成员失败')
          }
        }
      })
    } else {
      const { confirmDelete } = useConfirmDelete()
      confirmDelete({
        itemName: `成员「${member.displayName || member.username}」`,
        confirmText: '确认移除',
        onConfirm: async () => {
          try {
            await projectApi.removeMember(props.project.key, member.userId)
            Message.success('成员已移除')
            usersLoaded.value = false
            await loadMembers()
          } catch (e: any) {
            Message.error(e.response?.data?.message || '移除成员失败')
          }
        }
      })
    }
  } catch (e: any) {
    // If pre-check fails, still allow removal with basic confirmation
    const { confirmDelete } = useConfirmDelete()
    confirmDelete({
      itemName: `成员「${member.displayName || member.username}」`,
      confirmText: '确认移除',
      onConfirm: async () => {
        try {
          await projectApi.removeMember(props.project.key, member.userId)
          Message.success('成员已移除')
          usersLoaded.value = false
          await loadMembers()
        } catch (err: any) {
          Message.error(err.response?.data?.message || '移除成员失败')
        }
      }
    })
  }
}

// Activity
async function loadActivities() {
  activityLoading.value = true
  activityPage.value = 1
  try {
    const res = await projectApi.listActivities(props.project.id, { page: 1, pageSize: 15 })
    const data = res.data
    activities.value = data?.list || []
    const total = data?.pagination?.total || 0
    activityHasMore.value = activities.value.length < total
  } catch {
    activities.value = []
  } finally {
    activityLoading.value = false
  }
}

async function loadMoreActivities() {
  activityPage.value++
  try {
    const res = await projectApi.listActivities(props.project.id, { page: activityPage.value, pageSize: 15 })
    const data = res.data
    const moreList = data?.list || []
    activities.value.push(...moreList)
    const total = data?.pagination?.total || 0
    activityHasMore.value = activities.value.length < total
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadMembers()
  loadActivities()
  loadRolesIfNeeded()
})
</script>

<style scoped>
.settings-members {
  max-width: 800px;
}

/* Archived notice */
.archived-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  margin-bottom: 24px;
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.notice-icon {
  font-size: 16px;
  color: var(--tf-text-tertiary);
}

/* Add member */
.add-member-section {
  margin-bottom: 24px;
}

.add-member-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.option-hint {
  color: var(--tf-text-tertiary);
  margin-left: 4px;
  font-size: 11px;
}

/* Members section */
.members-section {
  margin-bottom: 32px;
}

.members-header {
  margin-bottom: 12px;
}

.members-count {
  font-size: 13px;
  color: var(--tf-text-tertiary);
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 32px;
}

.members-table :deep(.arco-table-th) {
  background: var(--tf-bg-surface);
  font-size: 12px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
}

.members-table :deep(.arco-table-td) {
  font-size: 13px;
}

.member-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.member-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.member-email {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.role-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.date-text {
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

/* Activity section */
.activity-section {
  border-top: 1px solid var(--tf-border-light, var(--tf-border));
  padding-top: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tf-text-primary);
  margin: 0 0 16px;
}

.activity-empty {
  text-align: center;
  padding: 32px 16px;
}

/* Activity list */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
}

.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
  flex-shrink: 0;
}

.dot-add {
  background: var(--tf-success);
}

.dot-remove {
  background: var(--tf-danger);
}

.dot-change {
  background: var(--tf-accent);
}

.activity-content {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.activity-text {
  font-size: 13px;
  color: var(--tf-text-secondary);
}

.activity-time {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  white-space: nowrap;
}

.load-more {
  text-align: center;
  margin-top: 8px;
}
</style>
