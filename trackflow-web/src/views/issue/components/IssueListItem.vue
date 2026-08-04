<template>
  <div
    class="issue-list-item"
    :class="{
      active,
      selected,
      focused,
      'density-S': density === 'S',
      'density-M': density === 'M',
      'density-L': density === 'L'
    }"
    :style="{ paddingLeft: indent * 24 + 12 + 'px' }"
    :data-id="issue.id"
    @click="$emit('click')"
    @dblclick="$emit('dblclick')"
    @contextmenu.prevent="$emit('contextmenu', $event)"
  >
    <!-- Drag handle -->
    <span v-if="showDragHandle" class="drag-handle" @mousedown.stop title="拖拽排序">
      <icon-drag-dot-vertical :size="14" />
    </span>

    <!-- Selection checkbox -->
    <label v-if="showCheckbox" class="item-checkbox" @click.stop>
      <input
        type="checkbox"
        :checked="selected"
        @change="$emit('select')"
      />
    </label>

    <!-- Expand toggle (tree mode) -->
    <button
      v-if="hasChildren"
      class="item-expand-btn"
      @click.stop="$emit('toggle-expand')"
    >
      <icon-right v-if="!expanded" :size="12" />
      <icon-down v-else :size="12" />
    </button>
    <span v-else-if="indent > 0" class="item-expand-spacer"></span>

    <!-- Priority icon -->
    <span class="item-priority" :class="'priority-' + (issue.priority || 'Normal').toLowerCase()" :title="localizePriority(issue.priority)">
      <span class="priority-dot"></span>
    </span>

    <!-- Issue key -->
    <router-link :to="`/issues/${issue.issueKey}`" class="item-key" @click.stop>{{ issue.issueKey }}</router-link>

    <!-- Title -->
    <span class="item-title">{{ issue.title }}</span>

    <!-- Tags (colored badges, max 3 shown) -->
    <template v-if="issue.tags && issue.tags.length > 0">
      <span
        v-for="tag in visibleTags"
        :key="tag.id"
        class="item-tag-badge"
        :style="{ background: tag.color || '#6b7280' }"
        :title="tag.name"
      >{{ tag.name }}</span>
      <span v-if="overflowTagCount > 0" class="item-tag-overflow" :title="overflowTagNames">+{{ overflowTagCount }}</span>
    </template>

    <!-- Meta: child progress -->
    <span v-if="issue.childCount && issue.childCount > 0" class="item-meta-badge" :title="`${issue.childClosedCount || 0}/${issue.childCount} 子任务`">
      <icon-layers :size="12" />
      {{ issue.childClosedCount || 0 }}/{{ issue.childCount }}
    </span>

    <!-- Vote count badge (only shown when voteCount > 0) -->
    <span v-if="issue.voteCount && issue.voteCount > 0" class="item-meta-badge item-vote-badge" :title="`${issue.voteCount} 票`">
      <icon-thumb-up :size="12" />
      {{ issue.voteCount }}
    </span>

    <!-- Time progress indicator (YouTrack Estimation Progress) -->
    <TimeProgressIndicator
      v-if="effectiveEstimated > 0"
      :spent="effectiveSpent"
      :estimated="effectiveEstimated"
      :size="14"
    />

    <!-- Right side: updated time -->
    <span class="item-time">{{ formatTime(issue.updatedAt) }}</span>

    <!-- Status badge -->
    <span class="item-status" :style="{ background: issue.statusColor || '#666' }">
      {{ localizeStatusName(issue.statusName) }}
    </span>

    <!-- Row 2: M/L density - custom fields + reporter -->
    <div v-if="density !== 'S'" class="item-row2">
      <span class="item-field item-assignee-field" :class="{ unassigned: !issue.assigneeName }">
        <span class="field-label">负责人:</span>
        <span class="field-value">{{ issue.assigneeName || '未分配' }}</span>
      </span>
      <!-- Sprint: inline-editable when sprintOptions is provided (non-null array) -->
      <span
        v-if="issue.sprintName || sprintOptions !== null"
        class="item-field item-sprint-field"
        :class="{ 'sprint-editable': sprintOptions !== null, 'sprint-completed': issue.sprintStatus === 'completed' }"
        @click.stop
      >
        <span class="field-label">Sprint:</span>
        <a-trigger
          v-if="sprintOptions !== null"
          v-model:popup-visible="sprintDropdownVisible"
          trigger="click"
          position="bl"
          :popup-offset="4"
          @popup-visible-change="onSprintDropdownChange"
        >
          <span
            class="field-value sprint-editable-value"
            @click="handleSprintClick"
          >
            {{ issue.sprintName || '—' }}
            <icon-loading v-if="sprintOptionsLoading" :size="10" class="sprint-spinner" />
            <icon-down v-else :size="10" class="sprint-arrow" />
          </span>
          <template #content>
            <div class="sprint-inline-dropdown" @click.stop>
              <div v-if="sprintOptionsLoading" class="sprint-dropdown-loading">
                <a-spin :size="14" />
              </div>
              <template v-else>
                <div class="sprint-dropdown-item" @click="selectSprint(null)">
                  <span class="sprint-no-sprint">无 Sprint</span>
                </div>
                <template v-for="group in sprintGroups" :key="group.label">
                  <div class="sprint-dropdown-group-label">{{ group.label }}</div>
                  <div
                    v-for="s in group.items"
                    :key="s.id"
                    class="sprint-dropdown-item"
                    :class="{ 'sprint-item-active': s.id === issue.sprintId }"
                    @click="selectSprint(s)"
                  >
                    {{ s.name }}
                  </div>
                </template>
                <div v-if="sprintGroups.length === 0 && !sprintOptionsLoading" class="sprint-dropdown-empty">
                  暂无可用 Sprint
                </div>
              </template>
            </div>
          </template>
        </a-trigger>
        <span v-else class="field-value">{{ issue.sprintName }}</span>
      </span>
      <template v-if="issue.customFieldDetails && issue.customFieldDetails.length > 0">
        <span
          v-for="detail in limitedCustomFieldDetails"
          :key="detail.customFieldId"
          class="item-field"
        >
          <template v-if="detail.isMulti && detail.displayValues">
            <span
              v-for="(dv, idx) in detail.displayValues"
              :key="idx"
              class="field-cf-badge"
              :style="detail.colors?.[idx] ? { background: detail.colors[idx] } : {}"
            >{{ dv }}</span>
          </template>
          <span
            v-else-if="detail.color"
            class="field-cf-badge"
            :style="{ background: detail.color }"
          >{{ detail.displayValue }}</span>
          <span v-else class="field-value">{{ detail.displayValue }}</span>
        </span>
      </template>
      <template v-else-if="issue.customFieldValues">
        <span
          v-for="(val, key) in limitedCustomFields"
          :key="key"
          class="item-field"
        >
          <span
            v-if="issue.customFieldColors?.[key]"
            class="field-cf-badge"
            :style="{ background: issue.customFieldColors[key] }"
          >{{ val }}</span>
          <span v-else class="field-value">{{ val }}</span>
        </span>
      </template>
      <span class="item-field item-reporter" v-if="issue.reporterName">
        <span class="field-label">报告人:</span>
        <span class="field-value">{{ issue.reporterName }}</span>
      </span>
    </div>

    <!-- Row 3: L density - description preview -->
    <div v-if="density === 'L' && issue.description" class="item-row3">
      <span class="item-description">{{ truncateDescription(issue.description) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { IconRight, IconDown, IconLayers, IconDragDotVertical, IconThumbUp, IconLoading } from '@arco-design/web-vue/es/icon'
import type { IssueVO, CustomFieldValueVO, SprintVO } from '@/api/types'
import type { DensityLevel } from '../composables'
import { localizeStatusName, localizePriority } from '@/utils/fieldLabels'
import TimeProgressIndicator from './TimeProgressIndicator.vue'

interface IssueListItemIssue extends IssueVO {
  description?: string
}

interface SprintGroup {
  label: string
  items: SprintVO[]
}

const props = withDefaults(defineProps<{
  issue: IssueListItemIssue
  density: DensityLevel
  indent: number
  hasChildren: boolean
  expanded: boolean
  active: boolean
  selected: boolean
  focused?: boolean
  showCheckbox?: boolean
  showDragHandle?: boolean
  /** Sprint 选项列表（传入时 Sprint 字段变为可编辑） */
  sprintOptions?: SprintVO[] | null
  /** Sprint 选项是否正在加载 */
  sprintOptionsLoading?: boolean
}>(), {
  showCheckbox: true,
  showDragHandle: false,
  focused: false,
  sprintOptions: null,
  sprintOptionsLoading: false
})

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'dblclick'): void
  (e: 'toggle-expand'): void
  (e: 'select'): void
  (e: 'contextmenu', event: MouseEvent): void
  /** 用户点击 Sprint 字段，触发加载 sprint 选项 */
  (e: 'sprint-edit', issue: IssueListItemIssue): void
  /** 用户选择了某个 Sprint */
  (e: 'sprint-select', issue: IssueListItemIssue, sprint: SprintVO | null): void
}>()

// Sprint 下拉显示状态
const sprintDropdownVisible = ref(false)

// Sprint 分组（只显示 active/planned）
const sprintGroups = computed<SprintGroup[]>(() => {
  const options = props.sprintOptions || []
  const active = options.filter(s => s.status?.toLowerCase() === 'active')
  const planned = options.filter(s => s.status?.toLowerCase() === 'planned')
  const groups: SprintGroup[] = []
  if (active.length) groups.push({ label: '进行中', items: active })
  if (planned.length) groups.push({ label: '计划中', items: planned })
  return groups
})

function onSprintDropdownChange(visible: boolean) {
  if (!visible) {
    sprintDropdownVisible.value = false
  }
}

function selectSprint(sprint: SprintVO | null) {
  sprintDropdownVisible.value = false
  emit('sprint-select', props.issue, sprint)
}

/**
 * 用户点击 Sprint 字段时：
 * 1. 先触发 sprint-edit（让父组件加载 sprint 选项）
 * 2. 打开下拉（通过 a-trigger 的 popup-visible 控制）
 */
function handleSprintClick() {
  emit('sprint-edit', props.issue)
  // a-trigger 会自动在 click 触发时打开下拉，无需手动设置
}

// Limit custom fields shown (max 4)
const limitedCustomFieldDetails = computed((): CustomFieldValueVO[] => {
  return (props.issue.customFieldDetails || []).slice(0, 4)
})

// Tags: max 3 visible, rest as +N
const MAX_VISIBLE_TAGS = 3
const visibleTags = computed(() => {
  return (props.issue.tags || []).slice(0, MAX_VISIBLE_TAGS)
})
const overflowTagCount = computed(() => {
  const total = (props.issue.tags || []).length
  return total > MAX_VISIBLE_TAGS ? total - MAX_VISIBLE_TAGS : 0
})
const overflowTagNames = computed(() => {
  return (props.issue.tags || []).slice(MAX_VISIBLE_TAGS).map(t => t.name).join(', ')
})

// Time progress: use derived values for parent issues, direct values for leaf issues
const effectiveEstimated = computed(() => {
  if (props.issue.derivedEstimatedHours != null && props.issue.derivedEstimatedHours > 0) {
    return props.issue.derivedEstimatedHours
  }
  return props.issue.estimatedHours || 0
})

const effectiveSpent = computed(() => {
  if (props.issue.derivedSpentHours != null) {
    return props.issue.derivedSpentHours
  }
  return props.issue.spentHours || 0
})

const limitedCustomFields = computed(() => {
  const cfv = props.issue.customFieldValues
  if (!cfv) return {}
  const entries = Object.entries(cfv).slice(0, 4)
  return Object.fromEntries(entries)
})

function formatTime(dt: string) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return d.toLocaleDateString('zh-CN')
}

function truncateDescription(desc?: string): string {
  if (!desc) return ''
  // Strip markdown/html and limit to ~120 chars
  const plain = desc.replace(/<[^>]+>/g, '').replace(/[#*_~`>]/g, '').trim()
  const lines = plain.split('\n').filter(l => l.trim()).slice(0, 3)
  const text = lines.join(' ')
  return text.length > 120 ? text.slice(0, 120) + '...' : text
}
</script>

<style scoped>
.issue-list-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--tf-border-secondary, var(--color-neutral-2));
  cursor: pointer;
  transition: background 100ms;
  position: relative;
}

.issue-list-item:hover {
  background: var(--tf-bg-hover, var(--color-fill-1));
}

.issue-list-item.active {
  background: var(--tf-bg-active, var(--color-primary-light-1));
}

.issue-list-item.selected {
  background: var(--tf-bg-active, var(--color-primary-light-1));
}

/* Keyboard focus (J/K navigation) — distinct from active/selected */
.issue-list-item.focused {
  outline: 2px solid var(--tf-accent, #58a6ff);
  outline-offset: -2px;
  background: var(--tf-bg-hover, var(--color-fill-1));
}

/* Density sizes */
.issue-list-item.density-S {
  padding-top: 6px;
  padding-bottom: 6px;
  min-height: 32px;
}

.issue-list-item.density-M {
  padding-top: 8px;
  padding-bottom: 8px;
  min-height: 48px;
}

.issue-list-item.density-L {
  padding-top: 10px;
  padding-bottom: 10px;
  min-height: 64px;
}

/* Checkbox */
.item-checkbox {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.item-checkbox input {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--tf-accent, var(--color-primary-6));
}

/* Drag handle */
.drag-handle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  cursor: grab;
  color: var(--tf-text-quaternary, var(--color-text-4));
  border-radius: 3px;
  opacity: 0;
  transition: opacity 100ms, color 100ms;
}

.issue-list-item:hover .drag-handle {
  opacity: 1;
}

.drag-handle:hover {
  color: var(--tf-text-secondary, var(--color-text-2));
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.drag-handle:active {
  cursor: grabbing;
}

/* Expand button */
.item-expand-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  color: var(--tf-text-tertiary, var(--color-text-3));
  border-radius: 3px;
}

.item-expand-btn:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
  color: var(--tf-text-primary, var(--color-text-1));
}

.item-expand-spacer {
  width: 18px;
  flex-shrink: 0;
}

/* Priority dot */
.item-priority {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.item-priority .priority-dot {
  width: 8px;
  height: 8px;
  min-width: 8px;
  min-height: 8px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--tf-text-tertiary);
}

.item-priority.priority-critical .priority-dot { background: var(--tf-danger, #e53e3e); }
.item-priority.priority-high .priority-dot { background: var(--tf-warning, #ed8936); }
.item-priority.priority-normal .priority-dot { background: var(--tf-accent, #4299e1); }
.item-priority.priority-low .priority-dot { background: var(--tf-text-tertiary, #a0aec0); }

/* Issue key */
.item-key {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--tf-accent, var(--color-text-3));
  font-family: 'JetBrains Mono', monospace;
  text-decoration: none;
  cursor: pointer;
}
.item-key:hover {
  text-decoration: underline;
}

/* Title */
.item-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--tf-text-primary, var(--color-text-1));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Meta badge (child progress) */
.item-meta-badge {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  padding: 1px 5px;
  background: var(--tf-bg-hover, var(--color-fill-1));
  border-radius: 3px;
}

/* Tag badges */
.item-tag-badge {
  flex-shrink: 0;
  display: inline-block;
  max-width: 72px;
  font-size: 11px;
  color: #fff;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.item-tag-overflow {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  padding: 1px 3px;
  font-weight: 500;
}

/* Time */
.item-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--color-text-4));
}

/* Status badge */
.item-status {
  flex-shrink: 0;
  font-size: 11px;
  color: #fff;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Row 2: custom fields line */
.item-row2 {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 46px;
  margin-top: 2px;
}

.item-field {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
}

.field-label {
  color: var(--tf-text-quaternary, var(--color-text-4));
}

.field-value {
  color: var(--tf-text-tertiary, var(--color-text-3));
}

/* Unassigned assignee highlight */
.item-assignee-field.unassigned .field-value {
  color: rgb(var(--warning-6));
  font-weight: 500;
  font-style: italic;
}

.field-cf-badge {
  color: #fff;
  padding: 0 4px;
  border-radius: 2px;
  font-size: 11px;
}

.item-reporter {
  margin-left: auto;
}

/* Sprint 内联编辑 */
.item-sprint-field {
  cursor: default;
}

.item-sprint-field.sprint-completed .field-value {
  color: var(--tf-text-tertiary, var(--color-text-3));
  text-decoration: line-through;
  opacity: 0.7;
}

.item-sprint-field.sprint-editable {
  cursor: pointer;
}

.sprint-editable-value {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border-radius: 3px;
  padding: 0 3px;
  transition: background 100ms;
}

.item-sprint-field.sprint-editable:hover .sprint-editable-value {
  background: var(--tf-bg-hover, var(--color-fill-2));
  color: var(--tf-text-secondary, var(--color-text-2));
}

.sprint-arrow {
  color: var(--tf-text-quaternary, var(--color-text-4));
  opacity: 0;
  transition: opacity 100ms;
  flex-shrink: 0;
}

.item-sprint-field.sprint-editable:hover .sprint-arrow {
  opacity: 1;
}

.sprint-spinner {
  color: var(--tf-accent, var(--color-primary-6));
  flex-shrink: 0;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Sprint 下拉菜单 */
.sprint-inline-dropdown {
  background: var(--tf-bg-elevated, var(--color-bg-1));
  border: 1px solid var(--tf-border, var(--color-neutral-3));
  border-radius: 6px;
  padding: 4px;
  min-width: 160px;
  max-height: 240px;
  overflow-y: auto;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.sprint-dropdown-loading {
  display: flex;
  justify-content: center;
  padding: 12px;
}

.sprint-dropdown-item {
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--tf-text-primary, var(--color-text-1));
  transition: background 100ms;
}

.sprint-dropdown-item:hover {
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.sprint-dropdown-item.sprint-item-active {
  color: var(--tf-accent, var(--color-primary-6));
  font-weight: 500;
}

.sprint-no-sprint {
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.sprint-dropdown-group-label {
  padding: 4px 8px 2px;
  font-size: 10px;
  color: var(--tf-text-quaternary, var(--color-text-4));
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.sprint-dropdown-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

/* Row 3: description preview */
.item-row3 {
  width: 100%;
  padding-left: 46px;
  margin-top: 2px;
}

.item-description {
  font-size: 12px;
  color: var(--tf-text-quaternary, var(--color-text-4));
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
