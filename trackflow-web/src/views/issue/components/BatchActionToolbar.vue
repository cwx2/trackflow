<template>
  <div class="batch-toolbar">
    <div class="batch-info">
      <span class="batch-count">已选 {{ selectedCount }} 个工单</span>
    </div>
    <div class="batch-actions">
      <!-- 变更状态 -->
      <a-trigger
        v-model:popup-visible="showStatusDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-swap /></template>
          变更状态
        </a-button>
        <template #content>
          <div class="batch-dropdown">
            <div v-if="statusLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                v-for="status in allStatuses"
                :key="status.id"
                class="dropdown-item"
                @click="handleBatchState(status)"
              >
                <span class="status-dot" :style="{ background: status.color }"></span>
                <span>{{ status.name }}</span>
              </div>
              <div v-if="allStatuses.length === 0" class="dropdown-empty">
                无可用状态
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 分配 -->
      <a-trigger
        v-model:popup-visible="showAssignDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-user /></template>
          分配
        </a-button>
        <template #content>
          <div class="batch-dropdown member-dropdown">
            <div class="dropdown-search">
              <a-input
                v-model="memberSearch"
                placeholder="搜索成员..."
                size="small"
                allow-clear
              >
                <template #prefix><icon-search /></template>
              </a-input>
            </div>
            <div v-if="memberLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                class="dropdown-item"
                @click="handleBatchAssign(null)"
              >
                <span class="unassigned-icon">—</span>
                <span>未分配</span>
              </div>
              <div
                v-for="member in filteredMembers"
                :key="member.userId"
                class="dropdown-item"
                @click="handleBatchAssign(member)"
              >
                <span class="member-avatar">{{ member.displayName?.charAt(0) }}</span>
                <span>{{ member.displayName }}</span>
              </div>
              <div v-if="filteredMembers.length === 0 && !memberLoading" class="dropdown-empty">
                无匹配成员
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 移至 Sprint -->
      <a-trigger
        v-model:popup-visible="showSprintDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-calendar /></template>
          移至 Sprint
        </a-button>
        <template #content>
          <div class="batch-dropdown">
            <div v-if="sprintWarning" class="dropdown-warning">
              {{ sprintWarning }}
            </div>
            <div v-else-if="sprintLoading" class="dropdown-loading">
              <a-spin :size="16" />
            </div>
            <template v-else>
              <div
                class="dropdown-item"
                @click="handleBatchSprint(null)"
              >
                <span>无 Sprint</span>
              </div>
              <template v-for="group in sprintGroups" :key="group.label">
                <div class="dropdown-group-label">{{ group.label }}</div>
                <div
                  v-for="sprint in group.items"
                  :key="sprint.id"
                  class="dropdown-item"
                  @click="handleBatchSprint(sprint)"
                >
                  <span>{{ sprint.name }}</span>
                </div>
              </template>
              <div v-if="sprintGroups.length === 0" class="dropdown-empty">
                无可用 Sprint
              </div>
            </template>
          </div>
        </template>
      </a-trigger>

      <!-- 变更优先级 -->
      <a-trigger
        v-model:popup-visible="showPriorityDropdown"
        trigger="click"
        position="bl"
        :popup-offset="4"
      >
        <a-button size="small" type="outline">
          <template #icon><icon-fire /></template>
          变更优先级
        </a-button>
        <template #content>
          <div class="batch-dropdown">
            <div
              v-for="p in priorityOptions"
              :key="p.value"
              class="dropdown-item"
              @click="handleBatchPriority(p.value)"
            >
              <span class="priority-dot" :class="'priority-' + p.value.toLowerCase()"></span>
              <span>{{ p.label }}</span>
            </div>
          </div>
        </template>
      </a-trigger>

      <!-- 批量删除 -->
      <a-button size="small" type="outline" status="danger" @click="confirmBatchDelete">
        <template #icon><icon-delete /></template>
        删除
      </a-button>
    </div>

    <a-button size="small" type="text" @click="$emit('deselect-all')">
      取消全选
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { IconSwap, IconUser, IconSearch, IconCalendar, IconFire, IconDelete } from '@arco-design/web-vue/es/icon'
import { Modal } from '@arco-design/web-vue'
import { issueApi, projectApi, sprintApi } from '@/api'
import type { IssueVO, IssueStatusVO, ProjectMemberVO, SprintVO } from '@/api/types'

const props = defineProps<{
  selectedCount: number
  selectedIssues: IssueVO[]
}>()

const emit = defineEmits<{
  'deselect-all': []
  'batch-state': [statusId: string]
  'batch-assign': [assigneeId: string | null]
  'batch-sprint': [sprintId: string | null]
  'batch-priority': [priority: string]
  'batch-delete': []
}>()

// ========== 状态下拉 ==========
const showStatusDropdown = ref(false)
const statusLoading = ref(false)
const allStatuses = ref<IssueStatusVO[]>([])

watch(showStatusDropdown, async (visible) => {
  if (visible) {
    statusLoading.value = true
    try {
      const res = await issueApi.listStatuses()
      allStatuses.value = res.data || []
    } catch {
      allStatuses.value = []
    } finally {
      statusLoading.value = false
    }
  }
})

function handleBatchState(status: IssueStatusVO) {
  showStatusDropdown.value = false
  emit('batch-state', status.id)
}

// ========== 分配下拉 ==========
const showAssignDropdown = ref(false)
const memberLoading = ref(false)
const memberSearch = ref('')
const members = ref<ProjectMemberVO[]>([])

const filteredMembers = computed(() => {
  if (!memberSearch.value) return members.value
  const kw = memberSearch.value.toLowerCase()
  return members.value.filter(m => m.displayName?.toLowerCase().includes(kw))
})

watch(showAssignDropdown, async (visible) => {
  if (visible) {
    memberSearch.value = ''
    memberLoading.value = true
    try {
      // 获取选中 Issue 所属项目的成员
      const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId))]
      const allMembers: ProjectMemberVO[] = []
      for (const pid of projectIds) {
        const res = await projectApi.listMembers(pid)
        allMembers.push(...(res.data || []))
      }
      // 去重 by userId
      const seen = new Set<string>()
      members.value = allMembers.filter(m => {
        if (seen.has(m.userId)) return false
        seen.add(m.userId)
        return true
      })
    } catch {
      members.value = []
    } finally {
      memberLoading.value = false
    }
  }
})

function handleBatchAssign(member: ProjectMemberVO | null) {
  showAssignDropdown.value = false
  emit('batch-assign', member?.userId || null)
}

// ========== Sprint 下拉 ==========
const showSprintDropdown = ref(false)
const sprintLoading = ref(false)
const sprintWarning = ref('')
const sprints = ref<SprintVO[]>([])

interface SprintGroup {
  label: string
  items: SprintVO[]
}

const sprintGroups = computed<SprintGroup[]>(() => {
  const active = sprints.value.filter(s => s.status?.toLowerCase() === 'active')
  const planned = sprints.value.filter(s => s.status?.toLowerCase() === 'planned')
  const groups: SprintGroup[] = []
  if (active.length) groups.push({ label: '进行中', items: active })
  if (planned.length) groups.push({ label: '计划中', items: planned })
  return groups
})

watch(showSprintDropdown, async (visible) => {
  if (visible) {
    sprintWarning.value = ''
    const projectIds = [...new Set(props.selectedIssues.map(i => i.projectId))]
    if (projectIds.length > 1) {
      sprintWarning.value = '批量移动 Sprint 仅支持同一项目的工单'
      return
    }
    sprintLoading.value = true
    try {
      const res = await sprintApi.listByProject(projectIds[0])
      sprints.value = res.data || []
    } catch {
      sprints.value = []
    } finally {
      sprintLoading.value = false
    }
  }
})

function handleBatchSprint(sprint: SprintVO | null) {
  showSprintDropdown.value = false
  emit('batch-sprint', sprint?.id || null)
}

// ========== 优先级下拉 ==========
const showPriorityDropdown = ref(false)
const priorityOptions = [
  { value: 'Critical', label: '紧急' },
  { value: 'High', label: '高' },
  { value: 'Normal', label: '普通' },
  { value: 'Low', label: '低' }
]

function handleBatchPriority(priority: string) {
  showPriorityDropdown.value = false
  emit('batch-priority', priority)
}

// ========== 批量删除 ==========
function confirmBatchDelete() {
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${props.selectedCount} 个工单吗？工单将移入回收站，可随时恢复。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk() {
      emit('batch-delete')
    }
  })
}
</script>

<style scoped>
.batch-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: var(--tf-bg-elevated);
  border-bottom: 1px solid var(--tf-border);
  gap: 12px;
  animation: slideDown 200ms ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.batch-info {
  display: flex;
  align-items: center;
}

.batch-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--tf-text-primary);
}

.batch-actions {
  display: flex;
  gap: 8px;
  flex: 1;
}

/* 下拉菜单 */
.batch-dropdown {
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 4px;
  min-width: 160px;
  max-height: 280px;
  overflow-y: auto;
}

.member-dropdown {
  min-width: 200px;
}

.dropdown-search {
  padding: 4px;
  margin-bottom: 4px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--tf-text-primary);
  transition: background 0.15s;
}
.dropdown-item:hover {
  background: var(--tf-bg-hover);
}

.dropdown-group-label {
  padding: 6px 8px 2px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.dropdown-loading {
  display: flex;
  justify-content: center;
  padding: 12px;
}

.dropdown-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.dropdown-warning {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--tf-warning);
  background: rgba(210, 153, 34, 0.1);
  border-radius: 4px;
  margin: 4px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.member-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}

.unassigned-icon {
  width: 24px;
  text-align: center;
  color: var(--tf-text-tertiary);
}

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.priority-critical { background: var(--tf-danger); }
.priority-high { background: var(--tf-warning); }
.priority-normal { background: var(--tf-accent); }
.priority-low { background: var(--tf-text-tertiary); }
</style>
