<template>
  <div
    class="issue-list-item"
    :class="{
      active,
      selected,
      'density-S': density === 'S',
      'density-M': density === 'M',
      'density-L': density === 'L'
    }"
    :style="{ paddingLeft: indent * 24 + 12 + 'px' }"
    :data-id="issue.id"
    @click="$emit('click')"
    @dblclick="$emit('dblclick')"
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
    <span class="item-key">{{ issue.issueKey }}</span>

    <!-- Title -->
    <span class="item-title">{{ issue.title }}</span>

    <!-- Tags placeholder (if any) -->

    <!-- Meta: child progress -->
    <span v-if="issue.childCount && issue.childCount > 0" class="item-meta-badge" :title="`${issue.childClosedCount || 0}/${issue.childCount} 子任务`">
      <icon-layers :size="12" />
      {{ issue.childClosedCount || 0 }}/{{ issue.childCount }}
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
      <span class="item-field" v-if="issue.assigneeName">
        <span class="field-label">负责人:</span>
        <span class="field-value">{{ issue.assigneeName }}</span>
      </span>
      <span class="item-field" v-if="issue.sprintName">
        <span class="field-label">Sprint:</span>
        <span class="field-value">{{ issue.sprintName }}</span>
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
import { computed } from 'vue'
import { IconRight, IconDown, IconLayers, IconDragDotVertical } from '@arco-design/web-vue/es/icon'
import type { IssueVO, CustomFieldValueVO } from '@/api/types'
import type { DensityLevel } from '../composables'
import { localizeStatusName, localizePriority } from '@/utils/fieldLabels'
import TimeProgressIndicator from './TimeProgressIndicator.vue'

interface IssueListItemIssue extends IssueVO {
  description?: string
}

const props = withDefaults(defineProps<{
  issue: IssueListItemIssue
  density: DensityLevel
  indent: number
  hasChildren: boolean
  expanded: boolean
  active: boolean
  selected: boolean
  showCheckbox?: boolean
  showDragHandle?: boolean
}>(), {
  showCheckbox: true,
  showDragHandle: false
})

defineEmits<{
  (e: 'click'): void
  (e: 'dblclick'): void
  (e: 'toggle-expand'): void
  (e: 'select'): void
}>()

// Limit custom fields shown (max 4)
const limitedCustomFieldDetails = computed((): CustomFieldValueVO[] => {
  return (props.issue.customFieldDetails || []).slice(0, 4)
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
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-family: 'JetBrains Mono', monospace;
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

.field-cf-badge {
  color: #fff;
  padding: 0 4px;
  border-radius: 2px;
  font-size: 11px;
}

.item-reporter {
  margin-left: auto;
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
