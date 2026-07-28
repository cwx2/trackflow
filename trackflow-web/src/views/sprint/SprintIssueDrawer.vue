<template>
  <a-drawer
    :visible="visible"
    :title="drawerTitle"
    :width="640"
    :footer="false"
    unmount-on-close
    @cancel="handleClose"
  >
    <!-- 筛选栏 -->
    <div class="drawer-toolbar">
      <a-input
        v-model="keyword"
        placeholder="搜索工单..."
        size="small"
        allow-clear
        style="width: 200px"
      >
        <template #prefix><icon-search /></template>
      </a-input>
      <a-select
        v-model="filterAssignee"
        placeholder="负责人筛选"
        size="small"
        allow-clear
        style="width: 140px"
      >
        <a-option value="unassigned">未分配</a-option>
        <a-option v-for="m in members" :key="m.userId" :value="m.userId">{{ m.displayName }}</a-option>
      </a-select>
    </div>

    <!-- Issue 列表 -->
    <div class="drawer-issue-list" v-if="!loading && filteredIssues.length > 0">
      <div
        v-for="issue in filteredIssues"
        :key="issue.id"
        class="drawer-issue-item"
      >
        <div class="issue-left">
          <span class="issue-status-dot" :style="{ background: issue.statusColor || '#6b7280' }"></span>
          <span class="issue-key">{{ issue.issueKey }}</span>
          <span class="issue-title" :title="issue.title">{{ issue.title }}</span>
        </div>
        <div class="issue-right" @click.stop>
          <a-trigger
            v-model:popup-visible="assigneeDropdowns[issue.id]"
            trigger="click"
            position="bl"
            :popup-offset="4"
          >
            <span
              class="assignee-cell"
              :class="{ unassigned: !issue.assigneeName, editing: savingIssueIds.has(issue.id) }"
              @click="openAssigneeEdit(issue)"
            >
              <span class="assignee-avatar" :class="{ 'unassigned-avatar': !issue.assigneeName }">
                {{ issue.assigneeName ? issue.assigneeName.charAt(0) : '?' }}
              </span>
              <span class="assignee-text">{{ issue.assigneeName || '未分配' }}</span>
              <icon-loading v-if="savingIssueIds.has(issue.id)" class="cell-spinner" />
            </span>
            <template #content>
              <div class="inline-dropdown member-dropdown">
                <div class="dropdown-search">
                  <a-input
                    v-model="assigneeSearch"
                    placeholder="搜索成员..."
                    size="mini"
                    allow-clear
                    @keydown.stop
                  >
                    <template #prefix><icon-search /></template>
                  </a-input>
                </div>
                <div v-if="membersLoading" class="dropdown-loading"><a-spin :size="16" /></div>
                <template v-else>
                  <div class="dropdown-item" @click="selectAssignee(issue, null)">
                    <span class="unassigned-icon">&mdash;</span><span>未分配</span>
                  </div>
                  <div
                    v-for="m in filteredMemberOptions"
                    :key="m.userId"
                    class="dropdown-item"
                    :class="{ active: m.userId === issue.assigneeId }"
                    @click="selectAssignee(issue, m)"
                  >
                    <span class="member-avatar">{{ m.displayName?.charAt(0) }}</span>
                    <span>{{ m.displayName }}</span>
                    <icon-check v-if="m.userId === issue.assigneeId" class="check-icon" />
                  </div>
                </template>
              </div>
            </template>
          </a-trigger>
        </div>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" class="drawer-loading">
      <a-spin :size="24" />
      <span>加载工单列表…</span>
    </div>

    <!-- 空状态 -->
    <div v-else class="drawer-empty">
      <div class="empty-icon">📋</div>
      <p class="empty-text">{{ emptyText }}</p>
    </div>

    <!-- 底部统计 -->
    <div class="drawer-footer-stats" v-if="!loading && issues.length > 0">
      <span>共 {{ issues.length }} 个工单</span>
      <span v-if="unassignedCount > 0" class="unassigned-stat">· {{ unassignedCount }} 个未分配</span>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconSearch, IconLoading, IconCheck } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi } from '@/api'
import type { IssueVO, ProjectMemberVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  sprintId: string
  sprintName: string
  projectId?: string
  /** 初始筛选条件：只展示未分配 / 指定负责人 / 全部 */
  initialFilter?: 'unassigned' | string | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'assigned'): void
}>()

const loading = ref(false)
const issues = ref<IssueVO[]>([])
const members = ref<ProjectMemberVO[]>([])
const membersLoading = ref(false)
const keyword = ref('')
const filterAssignee = ref<string | undefined>(undefined)
const assigneeSearch = ref('')
const assigneeDropdowns = ref<Record<string, boolean>>({})
const savingIssueIds = ref<Set<string>>(new Set())

const drawerTitle = computed(() => {
  if (filterAssignee.value === 'unassigned') {
    return `${props.sprintName} — 未分配工单`
  }
  return `${props.sprintName} — 工单列表`
})

const unassignedCount = computed(() => issues.value.filter(i => !i.assigneeId).length)

const emptyText = computed(() => {
  if (keyword.value || filterAssignee.value) return '没有符合条件的工单'
  return '该迭代暂无工单'
})

const filteredIssues = computed(() => {
  let result = issues.value

  // 按负责人筛选
  if (filterAssignee.value === 'unassigned') {
    result = result.filter(i => !i.assigneeId)
  } else if (filterAssignee.value) {
    result = result.filter(i => i.assigneeId === filterAssignee.value)
  }

  // 按关键词搜索
  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    result = result.filter(i =>
      i.title.toLowerCase().includes(kw) ||
      i.issueKey.toLowerCase().includes(kw) ||
      (i.assigneeName || '').toLowerCase().includes(kw)
    )
  }

  return result
})

const filteredMemberOptions = computed(() => {
  if (!assigneeSearch.value) return members.value
  const kw = assigneeSearch.value.toLowerCase()
  return members.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})

// 当 drawer 打开时加载数据
watch(() => props.visible, async (val) => {
  if (val) {
    // 设置初始筛选
    if (props.initialFilter === 'unassigned') {
      filterAssignee.value = 'unassigned'
    } else if (props.initialFilter) {
      filterAssignee.value = props.initialFilter
    } else {
      filterAssignee.value = undefined
    }
    keyword.value = ''
    await loadData()
  }
})

async function loadData() {
  loading.value = true
  try {
    // 并行加载：工单列表 + 成员列表
    const [issueRes, memberRes] = await Promise.all([
      issueApi.list({ sprintId: props.sprintId, pageSize: 200, sort: 'priority' }),
      props.projectId ? projectApi.listAssignableMembers(props.projectId) : Promise.resolve({ data: [] as ProjectMemberVO[] })
    ])
    issues.value = issueRes.data?.list || []
    members.value = (memberRes as any).data || []
  } catch (e: any) {
    Message.error('加载工单列表失败')
    issues.value = []
    members.value = []
  } finally {
    loading.value = false
  }
}

function openAssigneeEdit(issue: IssueVO) {
  if (savingIssueIds.value.has(issue.id)) return
  assigneeDropdowns.value[issue.id] = true
  assigneeSearch.value = ''

  // 如果成员列表为空且有 projectId，尝试加载
  if (members.value.length === 0 && issue.projectId) {
    membersLoading.value = true
    projectApi.listAssignableMembers(issue.projectId)
      .then(res => { members.value = res.data || [] })
      .catch(() => {})
      .finally(() => { membersLoading.value = false })
  }
}

async function selectAssignee(issue: IssueVO, member: ProjectMemberVO | null) {
  // 关闭下拉
  Object.keys(assigneeDropdowns.value).forEach(k => { assigneeDropdowns.value[k] = false })

  const newAssigneeId = member?.userId || '0'
  const newAssigneeName = member?.displayName || undefined

  // 乐观更新
  const idx = issues.value.findIndex(i => i.id === issue.id)
  const oldAssigneeId = issue.assigneeId
  const oldAssigneeName = issue.assigneeName
  if (idx !== -1) {
    issues.value[idx] = { ...issues.value[idx], assigneeId: member?.userId, assigneeName: newAssigneeName }
  }

  savingIssueIds.value.add(issue.id)
  try {
    await issueApi.assign(issue.id, newAssigneeId)
    Message.success(member ? `已分配给 ${member.displayName}` : '已取消分配')
    emit('assigned')
  } catch (e: any) {
    // 回滚
    if (idx !== -1) {
      issues.value[idx] = { ...issues.value[idx], assigneeId: oldAssigneeId, assigneeName: oldAssigneeName }
    }
    Message.error(e.response?.data?.message || '分配失败')
  } finally {
    savingIssueIds.value.delete(issue.id)
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<style scoped>
.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border);
}

.drawer-issue-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.drawer-issue-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background 0.15s;
}
.drawer-issue-item:hover {
  background: var(--color-fill-1);
}

.issue-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.issue-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.issue-key {
  font-size: 12px;
  font-family: monospace;
  color: var(--color-text-3);
  flex-shrink: 0;
}

.issue-title {
  font-size: 13px;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-right {
  flex-shrink: 0;
  margin-left: 12px;
}

/* Assignee cell */
.assignee-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 12px;
}
.assignee-cell:hover {
  background: var(--color-fill-2);
}
.assignee-cell.unassigned {
  color: rgb(var(--warning-6));
}
.assignee-cell.unassigned:hover {
  background: rgba(var(--warning-6), 0.08);
}
.assignee-cell.editing {
  opacity: 0.6;
  pointer-events: none;
}

.assignee-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-fill-3);
  color: var(--color-text-2);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.assignee-avatar.unassigned-avatar {
  background: rgba(var(--warning-6), 0.15);
  color: rgb(var(--warning-6));
}

.assignee-text {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-spinner {
  font-size: 12px;
  color: var(--color-text-3);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Dropdown */
.inline-dropdown {
  background: var(--color-bg-popup);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  min-width: 180px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
}

.dropdown-search {
  padding: 4px 8px 8px;
}

.dropdown-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-1);
  transition: background 0.1s;
}
.dropdown-item:hover {
  background: var(--color-fill-2);
}
.dropdown-item.active {
  background: rgba(var(--primary-6), 0.08);
}

.unassigned-icon {
  width: 20px;
  text-align: center;
  color: var(--color-text-3);
}

.member-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-fill-3);
  color: var(--color-text-2);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.check-icon {
  margin-left: auto;
  font-size: 12px;
  color: rgb(var(--primary-6));
}

/* Loading & empty */
.drawer-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--color-text-3);
  font-size: 13px;
}

.drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
}
.drawer-empty .empty-icon {
  font-size: 32px;
  margin-bottom: 12px;
}
.drawer-empty .empty-text {
  font-size: 13px;
  color: var(--color-text-3);
}

/* Footer stats */
.drawer-footer-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-3);
}
.unassigned-stat {
  color: rgb(var(--warning-6));
  font-weight: 500;
}
</style>
