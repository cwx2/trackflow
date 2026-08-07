<template>
  <div
    class="kanban-card"
    :class="[
      `kanban-card--${cardSize}`,
      colorClass,
      {
        'kanban-card--dragging': isDragging,
        'kanban-card--transitioning': isTransitioning,
        'kanban-card--no-drag': !draggable,
        'kanban-card--selected': isSelected
      }
    ]"
    :style="projectColorStyle"
    role="button"
    tabindex="0"
    :draggable="draggable"
    @dragstart="$emit('dragstart', $event)"
    @dragend="$emit('dragend', $event)"
    @click="$emit('click', $event)"
    @dblclick="$emit('dblclick', $event)"
    @keydown="$emit('keydown', $event)"
  >
    <div class="card-header">
      <!-- 多项目看板时显示项目 Key 标记 -->
      <span
        v-if="showProjectKey && issue.projectKey"
        class="card-project-tag"
        :title="`来自项目 ${issue.projectKey}`"
      >{{ issue.projectKey }}</span>
      <span class="card-key">{{ issue.issueKey }}</span>
      <span
        v-if="isFieldVisible('priority')"
        class="card-priority"
        :class="issue.priority?.toLowerCase()"
        :title="localizePriority(issue.priority)"
      >
        {{ priorityIcon(issue.priority) }}
      </span>
    </div>
    <div class="card-title" :class="`card-title--${cardSize}`">{{ issue.title }}</div>
    <!-- M/L: custom fields -->
    <div v-if="cardSize !== 'S' && hasVisibleCustomFields" class="card-custom-fields">
      <template v-for="detail in visibleCustomFieldDetails" :key="detail.customFieldId">
        <template v-if="detail.isMulti && detail.displayValues">
          <span v-for="(dv, idx) in detail.displayValues" :key="idx" class="card-cf-tag" :style="showCustomFieldColors && detail.colors?.[idx] ? { background: detail.colors[idx], color: '#fff' } : {}">{{ dv }}</span>
        </template>
        <span v-else-if="showCustomFieldColors && detail.color" class="card-cf-tag" :style="{ background: detail.color, color: '#fff' }">{{ detail.displayValue }}</span>
        <span v-else class="card-cf-tag">{{ detail.displayValue }}</span>
      </template>
    </div>
    <!-- Card metadata fields based on card config -->
    <div v-if="cardSize !== 'S' && showMetaFields" class="card-meta-fields">
      <a-tooltip v-if="isFieldVisible('dueDate') && issue.dueDate && dueDateClass" :content="dueDateTooltip" position="top" mini>
        <span class="card-meta-tag" :class="dueDateClass">📅 {{ issue.dueDate.slice(5) }}</span>
      </a-tooltip>
      <span v-else-if="isFieldVisible('dueDate') && issue.dueDate" class="card-meta-tag">📅 {{ issue.dueDate.slice(5) }}</span>
      <span v-if="isFieldVisible('sprint') && issue.sprintId" class="card-meta-tag">🏃 {{ sprintName }}</span>
      <span v-if="isFieldVisible('estimatedHours') && issue.estimatedHours" class="card-meta-tag">⏱ {{ issue.estimatedHours }}h</span>
      <template v-if="isFieldVisible('tags') && issueTags.length > 0">
        <span v-for="tag in visibleTags" :key="tag.id" class="card-tag" :style="tag.color ? { background: tag.color, color: '#fff' } : {}">{{ tag.name }}</span>
      </template>
    </div>
    <div class="card-footer">
      <span v-if="isFieldVisible('type')" class="card-type">
        <template v-if="typeDisplayMode === 'initial'">{{ typeInitial(issue.issueType) }}</template>
        <template v-else>{{ typeLabel(issue.issueType) }}</template>
      </span>
      <span v-else class="card-type-spacer"></span>
      <!-- 已分配：显示头像 -->
      <div class="card-assignee-avatar" v-if="isFieldVisible('assignee') && issue.assigneeName" :title="issue.assigneeName">
        <template v-if="assigneeDisplayMode === 'full_name'">
          <span class="assignee-full-name">{{ issue.assigneeName }}</span>
        </template>
        <template v-else>
          <img
            v-if="issue.assigneeAvatarUrl"
            :src="issue.assigneeAvatarUrl"
            :alt="issue.assigneeName"
            class="avatar-img"
          />
          <span v-else class="avatar-initials">{{ getInitials(issue.assigneeName) }}</span>
        </template>
      </div>
      <!-- 未分配 + 有权限：显示 Set assignee 按钮 -->
      <a-dropdown
        v-else-if="isFieldVisible('assignee') && !issue.assigneeName && canAssign && members.length > 0"
        trigger="click"
        @select="(userId: any) => $emit('assign', userId)"
      >
        <button
          class="set-assignee-btn"
          :title="'分配负责人'"
          @click.stop
        >
          <icon-user class="set-assignee-icon" />
        </button>
        <template #content>
          <a-doption v-for="m in members" :key="m.userId" :value="m.userId">
            {{ m.displayName }}
          </a-doption>
        </template>
      </a-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { IconUser } from '@arco-design/web-vue/es/icon'
import { localizePriority, localizeIssueType } from '@/utils/fieldLabels'
import { getDueDateInfo } from '@/utils/dueDate'
import type { IssueVO, BoardCardVO } from '@/api/types'

type BoardIssue = IssueVO | BoardCardVO

interface CardFieldConfig {
  visibleFields: string[]
  fieldDisplayModes?: Record<string, string>
  showCustomFieldColors?: boolean
  currentEstimationFieldId?: string
}

interface TagInfo {
  id: string
  name: string
  color?: string | null
}

interface MemberInfo {
  userId: string
  displayName: string
}

interface CustomFieldDetail {
  customFieldId: string
  displayValue: string
  displayValues?: string[]
  color?: string
  colors?: string[]
  isMulti?: boolean
}

const props = defineProps<{
  issue: BoardIssue
  cardSize: 'S' | 'M' | 'L' | 'XL'
  cardConfig: CardFieldConfig
  colorClass?: string
  projectColorStyle?: Record<string, string>
  isDragging?: boolean
  isTransitioning?: boolean
  isSelected?: boolean
  draggable?: boolean
  showProjectKey?: boolean
  canAssign?: boolean
  members: MemberInfo[]
  sprintName?: string
  customFieldDetails: CustomFieldDetail[]
  tags: TagInfo[]
}>()

defineEmits<{
  dragstart: [event: DragEvent]
  dragend: [event: DragEvent]
  click: [event: MouseEvent]
  dblclick: [event: MouseEvent]
  keydown: [event: KeyboardEvent]
  assign: [userId: string]
}>()

const showCustomFieldColors = computed(() => props.cardConfig.showCustomFieldColors !== false)

function isFieldVisible(field: string): boolean {
  return props.cardConfig.visibleFields.includes(field)
}

const typeDisplayMode = computed(() => props.cardConfig.fieldDisplayModes?.type || 'initial')
const assigneeDisplayMode = computed(() => props.cardConfig.fieldDisplayModes?.assignee || 'avatar')

const hasVisibleCustomFields = computed(() => props.customFieldDetails.length > 0)
const visibleCustomFieldDetails = computed(() => props.customFieldDetails)

const issueTags = computed(() => props.tags)
const visibleTags = computed(() => props.tags.slice(0, 3))

const showMetaFields = computed(() => {
  return isFieldVisible('dueDate') || isFieldVisible('sprint') || isFieldVisible('estimatedHours') || isFieldVisible('tags')
})

const dueDateClass = computed(() => {
  if (!props.issue.dueDate) return null
  const info = getDueDateInfo(props.issue.dueDate)
  return info?.className || null
})

const dueDateTooltip = computed(() => {
  if (!props.issue.dueDate) return ''
  const info = getDueDateInfo(props.issue.dueDate)
  return info?.tooltip || ''
})

// ─── 类型工具函数 ──────────────────────────────────────

const typeInitials: Record<string, string> = {
  Task: 'T',
  Bug: 'B',
  Feature: 'F',
  Story: 'S',
  Epic: 'E',
  Subtask: '☐'
}

function typeInitial(issueType?: string): string {
  return typeInitials[issueType || ''] || (issueType || '').charAt(0).toUpperCase()
}

function typeLabel(issueType?: string): string {
  return localizeIssueType(issueType || '')
}

// ─── 优先级图标 ──────────────────────────────────────

function priorityIcon(priority?: string): string {
  switch (priority) {
    case '阻塞': return '⛔'
    case '紧急': return '🔴'
    case '高': return '🟠'
    case '普通': return '🔵'
    case '低': return '🟢'
    // 兼容历史英文值
    case 'Critical': return '🔴'
    case 'High': return '🟠'
    case 'Normal': return '🔵'
    case 'Low': return '🟢'
    default: return '⚪'
  }
}

function getInitials(name: string): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return name.substring(0, 2).toUpperCase()
}
</script>
