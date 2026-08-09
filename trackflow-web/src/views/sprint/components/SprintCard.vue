<template>
  <div
    class="sprint-card"
    :class="[
      sprint.status?.toLowerCase(),
      {
        'sprint-overdue': sprint.overdue,
        'sprint-next': isNext,
        'just-completed': isJustCompleted
      }
    ]"
  >
    <!-- Header -->
    <div class="sprint-header">
      <div class="sprint-info">
        <span class="sprint-status-badge" :class="statusBadgeClass">{{ statusBadgeText }}</span>
        <span v-if="showProjectBadge && sprint.projectKey" class="sprint-project-badge">{{ sprint.projectKey }}</span>
        <div class="sprint-name-wrapper" @mouseenter="hovered = true" @mouseleave="hovered = false">
          <template v-if="isInlineEditing">
            <input
              ref="inlineEditInputRef"
              v-model="inlineEditName"
              class="sprint-name-input"
              @keydown.enter="confirmInlineEdit"
              @keydown.esc="cancelInlineEdit"
              @blur="cancelInlineEdit"
              @click.stop
            />
          </template>
          <template v-else>
            <h3 class="sprint-name" :class="{ 'active-name': isActive }">{{ sprint.name }}</h3>
            <a-tooltip content="编辑迭代名称" v-if="canEdit && hovered && inlineEditable">
              <span class="sprint-name-edit-icon" @click.stop="startInlineEdit">
                <icon-edit />
              </span>
            </a-tooltip>
          </template>
        </div>
        <span class="sprint-remaining" :class="{ 'sprint-remaining-overdue': timeInfo && (timeInfo.type === 'overdue' || timeInfo.type === 'today') }" v-if="timeInfo">
          <template v-if="timeInfo.type === 'not-started'">
            <span class="remaining-icon not-started">📅</span> {{ timeInfo.days }} 天后开始
          </template>
          <template v-else-if="timeInfo.type === 'remaining'">
            <span class="remaining-icon">⏳</span> 还剩 {{ timeInfo.days }} 天
          </template>
          <template v-else-if="timeInfo.type === 'today'">
            <span class="remaining-icon warning">⚠️</span> 今天截止
          </template>
          <template v-else>
            <span class="remaining-icon overdue">🚨</span> 已超期 {{ timeInfo.days }} 天
          </template>
        </span>
      </div>
      <div class="sprint-dates" v-if="sprint.startDate">
        {{ formatDate(sprint.startDate) }} — {{ formatDate(sprint.endDate) }}
      </div>
      <div class="sprint-dates sprint-dates-missing" v-else-if="isActive">
        <span class="dates-missing-icon">📅</span> 未设置日期
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('editDates', sprint)">设置</a-button>
      </div>
    </div>

    <!-- Status Warning (active sprints) -->
    <div class="sprint-status-warning" v-if="isActive && sprint.statusHint">
      <span class="warning-icon">⚠️</span>
      <span class="warning-text">{{ sprint.statusHint }}</span>
      <div class="warning-actions" v-if="isStartDateNotReachedWarning && canEdit">
        <a-button size="mini" type="text" @click="$emit('editDates', sprint)">修改日期</a-button>
        <a-button size="mini" type="text" @click="$emit('revertToPlanned', sprint)">回退为计划中</a-button>
      </div>
    </div>

    <!-- Status Hint (planned sprints) -->
    <div class="sprint-status-hint" v-if="isPlanned && sprint.statusHint">
      <span class="hint-icon">💡</span>
      <span class="hint-text">{{ sprint.statusHint }}</span>
    </div>

    <!-- Sprint Goal -->
    <div class="sprint-goal-banner" v-if="sprint.goal">
      <span class="sprint-goal-banner-icon">🎯</span>
      <span class="sprint-goal-banner-text">{{ sprint.goal }}</span>
    </div>

    <!-- Progress -->
    <SprintProgress
      :sprint="sprint"
      :show-empty-hint="isPlanned"
      :always-show-details="isActive || isPlanned"
      @view-category="(cat: string) => $emit('viewCategory', sprint, cat)"
      @view-total="$emit('viewTotal', sprint)"
      @view-overdue="$emit('viewOverdue', sprint)"
      @view-unassigned="$emit('viewUnassigned', sprint)"
    />

    <!-- Burndown Chart (active) -->
    <SprintBurndownChart
      v-if="isActive && sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
      :sprint-id="sprint.id"
      :sprint-end-date="sprint.endDate"
      :is-completed="false"
    />

    <!-- Burndown Chart (completed/archived - expandable) -->
    <SprintBurndownChart
      v-if="(isCompleted || isArchived) && showBurndown && sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
      :sprint-id="sprint.id"
      :sprint-end-date="sprint.endDate"
      :is-completed="true"
    />

    <!-- Assignee Distribution (active/planned) -->
    <SprintAssigneeDistribution
      v-if="(isActive || isPlanned) && sprint.totalIssues > 0"
      :sprint-id="sprint.id"
      :sprint-name="sprint.name"
      :project-key="projectKey || sprint.projectKey"
      @view-issues="(filter: string) => $emit('viewIssuesFiltered', sprint, filter)"
    />

    <!-- Actions -->
    <div class="sprint-actions">
      <!-- Common: view issues -->
      <a-button v-if="sprint.totalIssues > 0" size="mini" type="text" @click="$emit('viewIssues', sprint)">查看工单</a-button>
      <a-button v-if="(isActive || isPlanned) && sprint.totalIssues > 0" size="mini" type="text" @click="$emit('viewOnBoard', sprint)">在看板中查看</a-button>

      <!-- Active actions -->
      <template v-if="isActive">
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('edit', sprint)">编辑</a-button>
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('archive', sprint)">归档</a-button>
        <a-button v-if="canEdit" size="mini" @click="$emit('complete', sprint)">完成迭代</a-button>
      </template>

      <!-- Planned actions -->
      <template v-if="isPlanned">
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('edit', sprint)">编辑</a-button>
        <a-tooltip :content="activateTooltip">
          <span class="tooltip-wrapper">
            <a-button type="primary" size="mini" :disabled="!canActivate" @click="$emit('activate', sprint.id)">开始迭代</a-button>
          </span>
        </a-tooltip>
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('archivePlanned', sprint)">归档</a-button>
        <a-button v-if="canDelete" size="mini" status="danger" @click="$emit('delete', sprint)">删除</a-button>
      </template>

      <!-- Completed actions -->
      <template v-if="isCompleted">
        <a-button v-if="sprint.totalIssues > 0" size="mini" type="text" @click="$emit('viewOnBoard', sprint)">在看板中查看</a-button>
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('edit', sprint)">编辑</a-button>
        <a-button
          v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          size="mini"
          type="text"
          @click="$emit('toggleBurndown', sprint.id)"
        >
          {{ showBurndown ? '收起燃尽图' : '查看燃尽图' }}
        </a-button>
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('archivePlanned', sprint)">归档</a-button>
      </template>

      <!-- Archived actions -->
      <template v-if="isArchived">
        <a-button
          v-if="sprint.startDate && sprint.endDate && sprint.totalIssues > 0"
          size="mini"
          type="text"
          @click="$emit('toggleBurndown', sprint.id)"
        >
          {{ showBurndown ? '收起燃尽图' : '查看燃尽图' }}
        </a-button>
        <a-button v-if="canEdit" size="mini" type="text" @click="$emit('restore', sprint)">恢复</a-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { IconEdit } from '@arco-design/web-vue/es/icon'
import type { SprintVO } from '@/api/types'
import SprintProgress from './SprintProgress.vue'
import SprintBurndownChart from './SprintBurndownChart.vue'
import SprintAssigneeDistribution from './SprintAssigneeDistribution.vue'

const props = withDefaults(defineProps<{
  sprint: SprintVO
  /** 是否显示项目 badge（跨项目视图） */
  showProjectBadge?: boolean
  /** 当前项目 key（用于 AssigneeDistribution） */
  projectKey?: string
  /** 是否可编辑 */
  canEdit: boolean
  /** 是否可删除 */
  canDelete: boolean
  /** 是否标记为"下一个"（仅 planned） */
  isNext?: boolean
  /** 是否刚完成高亮（仅 completed） */
  isJustCompleted?: boolean
  /** 是否有活跃 Sprint（影响激活按钮状态） */
  hasActiveSprint?: boolean
  /** 是否可以激活（开始日期/结束日期校验通过） */
  isStartable?: boolean
  /** 激活按钮 tooltip */
  activateTooltip?: string
  /** 燃尽图是否展开 */
  showBurndown?: boolean
  /** 是否支持内联编辑名称（active/planned） */
  inlineEditable?: boolean
}>(), {
  showProjectBadge: false,
  projectKey: undefined,
  isNext: false,
  isJustCompleted: false,
  hasActiveSprint: false,
  isStartable: true,
  activateTooltip: undefined,
  showBurndown: false,
  inlineEditable: true
})

const emit = defineEmits<{
  (e: 'viewIssues', sprint: SprintVO): void
  (e: 'viewOnBoard', sprint: SprintVO): void
  (e: 'viewCategory', sprint: SprintVO, category: string): void
  (e: 'viewTotal', sprint: SprintVO): void
  (e: 'viewOverdue', sprint: SprintVO): void
  (e: 'viewUnassigned', sprint: SprintVO): void
  (e: 'viewIssuesFiltered', sprint: SprintVO, filter: string): void
  (e: 'edit', sprint: SprintVO): void
  (e: 'editDates', sprint: SprintVO): void
  (e: 'complete', sprint: SprintVO): void
  (e: 'activate', id: string): void
  (e: 'archive', sprint: SprintVO): void
  (e: 'archivePlanned', sprint: SprintVO): void
  (e: 'delete', sprint: SprintVO): void
  (e: 'restore', sprint: SprintVO): void
  (e: 'revertToPlanned', sprint: SprintVO): void
  (e: 'toggleBurndown', id: string): void
  (e: 'inlineRename', sprintId: string, newName: string): void
}>()

// State
const hovered = ref(false)
const isInlineEditing = ref(false)
const inlineEditName = ref('')
const inlineEditInputRef = ref<HTMLInputElement | null>(null)

// Computed status helpers
const isActive = computed(() => {
  const s = props.sprint.status?.toLowerCase()
  return s === 'active'
})
const isPlanned = computed(() => {
  const s = props.sprint.status?.toLowerCase()
  return s === 'planned'
})
const isCompleted = computed(() => {
  const s = props.sprint.status?.toLowerCase()
  return s === 'completed'
})
const isArchived = computed(() => {
  const s = props.sprint.status?.toLowerCase()
  return s === 'archived'
})

const canActivate = computed(() =>
  props.canEdit && props.isStartable && !props.hasActiveSprint
)

const statusBadgeClass = computed(() => {
  if (isActive.value) return props.sprint.overdue ? 'active overdue' : 'active'
  if (isPlanned.value) return props.isNext ? 'next' : 'planned'
  if (isCompleted.value) return 'completed'
  return 'archived'
})

const statusBadgeText = computed(() => {
  if (isActive.value) return props.sprint.overdue ? '已超期' : '进行中'
  if (isPlanned.value) return props.isNext ? '下一个' : '计划中'
  if (isCompleted.value) return '已完成'
  return '已归档'
})

interface SprintTimeInfo {
  type: 'not-started' | 'remaining' | 'today' | 'overdue'
  days: number
}

const timeInfo = computed<SprintTimeInfo | null>(() => {
  if (!props.sprint.endDate) return null
  // Only show for active/planned
  if (!isActive.value && !isPlanned.value) return null

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const end = new Date(props.sprint.endDate)
  end.setHours(0, 0, 0, 0)

  if (props.sprint.startDate) {
    const start = new Date(props.sprint.startDate)
    start.setHours(0, 0, 0, 0)
    if (today.getTime() < start.getTime()) {
      const daysUntilStart = Math.ceil((start.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
      return { type: 'not-started', days: daysUntilStart }
    }
  }

  const remainingDays = Math.ceil((end.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  if (remainingDays > 0) return { type: 'remaining', days: remainingDays }
  if (remainingDays === 0) return { type: 'today', days: 0 }
  return { type: 'overdue', days: Math.abs(remainingDays) }
})

const isStartDateNotReachedWarning = computed(() =>
  props.sprint.statusHint?.includes('开始日期尚未到达') ?? false
)

// Inline edit methods
function startInlineEdit() {
  isInlineEditing.value = true
  inlineEditName.value = props.sprint.name
  nextTick(() => {
    const input = inlineEditInputRef.value
    if (input) {
      input.focus()
      input.select()
    }
  })
}

function confirmInlineEdit() {
  const newName = inlineEditName.value.trim()
  isInlineEditing.value = false
  if (!newName || newName === props.sprint.name) return
  emit('inlineRename', props.sprint.id, newName)
}

function cancelInlineEdit() {
  isInlineEditing.value = false
  inlineEditName.value = ''
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.sprint-card {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 16px;
  transition: border-color 0.15s;
}
.sprint-card:hover {
  border-color: var(--tf-accent);
}
.sprint-card.active {
  border-left: 3px solid var(--tf-accent);
}
.sprint-card.sprint-next {
  border-left: 3px solid var(--tf-accent);
}
.sprint-card.completed {
  opacity: 0.7;
}
.sprint-card.archived {
  opacity: 0.5;
}
.sprint-card.sprint-overdue {
  border-left-color: rgb(var(--danger-6));
}
.sprint-card.just-completed {
  animation: just-completed-highlight 3s ease-out;
  position: relative;
}

@keyframes just-completed-highlight {
  0% { box-shadow: 0 0 0 3px rgba(var(--green-6), 0.4); }
  100% { box-shadow: none; }
}

.sprint-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.sprint-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
}

.sprint-status-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
}

.sprint-status-badge.active {
  background: var(--tf-accent-light);
  color: var(--tf-accent);
}
.sprint-status-badge.active.overdue {
  background: var(--tf-danger-bg);
  color: rgb(var(--danger-6));
}
.sprint-status-badge.planned {
  background: var(--color-fill-2);
  color: var(--color-text-3);
}
.sprint-status-badge.next {
  background: var(--tf-accent-light);
  color: var(--tf-accent);
}
.sprint-status-badge.completed {
  background: var(--tf-success-bg);
  color: var(--tf-success);
}
.sprint-status-badge.archived {
  background: var(--color-fill-2);
  color: var(--color-text-4);
}

.sprint-project-badge {
  font-size: 11px;
  padding: 1px 6px;
  background: var(--color-fill-2);
  border-radius: 3px;
  color: var(--color-text-3);
}

.sprint-name-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
}

.sprint-name {
  font-size: 14px;
  font-weight: 500;
  margin: 0;
  color: var(--color-text-1);
}

.sprint-name.active-name {
  font-weight: 600;
}

.sprint-name-edit-icon {
  cursor: pointer;
  color: var(--color-text-4);
  font-size: 12px;
  transition: color 0.15s;
}
.sprint-name-edit-icon:hover {
  color: var(--tf-accent);
}

.sprint-name-input {
  font-size: 14px;
  font-weight: 500;
  border: 1px solid var(--tf-accent);
  border-radius: 4px;
  padding: 2px 8px;
  outline: none;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  width: 200px;
}

.sprint-remaining {
  font-size: 12px;
  color: var(--color-text-3);
  display: flex;
  align-items: center;
  gap: 4px;
}

.sprint-remaining-overdue {
  color: rgb(var(--danger-6));
  font-weight: 500;
}

.sprint-remaining .remaining-icon.overdue,
.sprint-remaining .remaining-icon.warning {
  color: rgb(var(--danger-6));
}
.remaining-icon {
  font-size: 12px;
}

.sprint-dates {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.sprint-dates-missing {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-4);
  font-style: italic;
}

.dates-missing-icon {
  font-size: 12px;
}

/* Status Warning / Hint */
.sprint-status-warning {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--tf-warning-bg);
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-2);
}

.warning-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.sprint-status-hint {
  margin-top: 8px;
  padding: 6px 12px;
  background: var(--color-fill-1);
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-3);
}

/* Sprint Goal */
.sprint-goal-banner {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--tf-accent-subtle);
  border-radius: 6px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-2);
}

.sprint-goal-banner-icon {
  flex-shrink: 0;
}

/* Actions */
.sprint-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.tooltip-wrapper {
  display: inline-block;
}
</style>
