<template>
  <div class="issue-list-layout" :class="'density-' + density">
    <!-- Sort bar (List mode has its own sort control) -->
    <div class="list-sort-bar">
      <span class="sort-label">排序：</span>
      <a-dropdown trigger="click" position="bl">
        <a-button size="mini" type="text" class="sort-button">
          {{ sortFieldLabel }}
          <span class="sort-dir-icon">{{ sortDirection === 'desc' ? '↓' : sortDirection === 'asc' ? '↑' : '' }}</span>
          <icon-down :size="12" />
        </a-button>
        <template #content>
          <a-doption v-for="opt in sortOptions" :key="opt.value" @click="onSortSelect(opt.value)">
            <span :class="{ 'sort-active': sortField === opt.value }">{{ opt.label }}</span>
          </a-doption>
        </template>
      </a-dropdown>

      <!-- Manual order discard button -->
      <div v-if="isManualSorted" class="manual-order-indicator">
        <span class="manual-order-label">
          <icon-drag-dot-vertical :size="12" />
          手动排序{{ isOwnerOrder ? '(全局)' : '(个人)' }}
        </span>
        <a-button size="mini" type="text" class="discard-order-btn" @click="$emit('discard-order')">
          丢弃自定义顺序
        </a-button>
      </div>
    </div>

    <!-- Issue items -->
    <div v-if="loading" class="list-loading">
      <a-spin :size="20" />
      <span>加载中...</span>
    </div>

    <div v-else-if="error" class="list-error">
      <icon-close-circle class="error-icon" />
      <p class="error-title">加载失败</p>
      <p class="error-desc">无法获取工单列表，请检查网络连接或稍后重试</p>
      <a-button type="primary" size="small" @click="$emit('retry')">
        <template #icon><icon-refresh /></template>
        重试
      </a-button>
    </div>

    <EmptyState
      v-else-if="issues.length === 0"
      icon="search"
      title="暂无工单"
      description="尝试调整筛选条件或创建新的工单"
    />

    <div v-else class="list-items" ref="listContainerRef">
      <!-- Manually sorted section -->
      <template v-if="isManualSorted && sortedIssues.length > 0">
        <div class="sorted-section" ref="sortedSectionRef">
          <IssueListItem
            v-for="issue in sortedIssues"
            :key="issue.id"
            v-memo="[issue.id, issue.updatedAt, activeIssueId === issue.id, focusedIssueId === issue.id, selectedIds.has(issue.id), density]"
            :data-id="issue.id"
            :issue="issue"
            :density="density"
            :indent="0"
            :has-children="false"
            :expanded="false"
            :active="activeIssueId === issue.id"
            :focused="focusedIssueId === issue.id"
            :selected="selectedIds.has(issue.id)"
            :show-checkbox="showCheckbox"
            :show-drag-handle="draggable"
            :sprint-options="canEditIssue(issue) ? (sprintOptionsCache[issue.projectId] ?? []) : null"
            :sprint-options-loading="sprintLoadingIds.has(issue.id)"
            :badge-fields="badgeFieldsMap[issue.projectId] || []"
            @click="$emit('item-click', issue)"
            @dblclick="$emit('item-dblclick', issue)"
            @select="toggleSelect(issue)"
            @contextmenu="emit('item-contextmenu', { issue, event: $event })"
            @sprint-edit="emit('sprint-edit', $event)"
            @sprint-select="(iss, sprint) => emit('sprint-select', iss, sprint)"
          />
        </div>

        <!-- Separator between manually sorted and default sorted -->
        <div v-if="unsortedIssues.length > 0" class="manual-order-separator">
          <span class="separator-line"></span>
          <span class="separator-text">以下为默认排序</span>
          <span class="separator-line"></span>
        </div>

        <!-- Unsorted section -->
        <div class="unsorted-section">
          <IssueListItem
            v-for="issue in unsortedIssues"
            :key="issue.id"
            v-memo="[issue.id, issue.updatedAt, activeIssueId === issue.id, focusedIssueId === issue.id, selectedIds.has(issue.id), density]"
            :issue="issue"
            :density="density"
            :indent="0"
            :has-children="false"
            :expanded="false"
            :active="activeIssueId === issue.id"
            :focused="focusedIssueId === issue.id"
            :selected="selectedIds.has(issue.id)"
            :show-checkbox="showCheckbox"
            :show-drag-handle="draggable"
            :sprint-options="canEditIssue(issue) ? (sprintOptionsCache[issue.projectId] ?? []) : null"
            :sprint-options-loading="sprintLoadingIds.has(issue.id)"
            :badge-fields="badgeFieldsMap[issue.projectId] || []"
            @click="$emit('item-click', issue)"
            @dblclick="$emit('item-dblclick', issue)"
            @select="toggleSelect(issue)"
            @contextmenu="emit('item-contextmenu', { issue, event: $event })"
            @sprint-edit="emit('sprint-edit', $event)"
            @sprint-select="(iss, sprint) => emit('sprint-select', iss, sprint)"
          />
        </div>
      </template>

      <!-- Normal mode (no manual sort) -->
      <template v-else>
        <!-- Tree mode: render with indentation -->
        <template v-if="structure === 'tree'">
          <template v-for="node in treeData" :key="node.issue.id">
            <IssueListItem
              :issue="node.issue"
              :data-id="node.issue.id"
              :density="density"
              :indent="node.depth"
              :has-children="(node.issue.childCount || 0) > 0"
              :expanded="expandedIds.has(node.issue.id)"
              :active="activeIssueId === node.issue.id"
              :focused="focusedIssueId === node.issue.id"
              :selected="selectedIds.has(node.issue.id)"
              :show-checkbox="showCheckbox"
              :show-drag-handle="draggable"
              :sprint-options="canEditIssue(node.issue) ? (sprintOptionsCache[node.issue.projectId] ?? []) : null"
              :sprint-options-loading="sprintLoadingIds.has(node.issue.id)"
              :badge-fields="badgeFieldsMap[node.issue.projectId] || []"
              @click="$emit('item-click', node.issue)"
              @dblclick="$emit('item-dblclick', node.issue)"
              @toggle-expand="toggleExpand(node.issue)"
              @select="toggleSelect(node.issue)"
              @contextmenu="emit('item-contextmenu', { issue: node.issue, event: $event })"
              @sprint-edit="emit('sprint-edit', $event)"
              @sprint-select="(iss, sprint) => emit('sprint-select', iss, sprint)"
            />
          </template>
        </template>

        <!-- Flat mode: draggable list -->
        <div v-else ref="flatListRef">
          <IssueListItem
            v-for="issue in issues"
            :key="issue.id"
            :data-id="issue.id"
            :issue="issue"
            :density="density"
            :indent="0"
            :has-children="false"
            :expanded="false"
            :active="activeIssueId === issue.id"
            :focused="focusedIssueId === issue.id"
            :selected="selectedIds.has(issue.id)"
            :show-checkbox="showCheckbox"
            :show-drag-handle="draggable"
            :sprint-options="canEditIssue(issue) ? (sprintOptionsCache[issue.projectId] ?? []) : null"
            :sprint-options-loading="sprintLoadingIds.has(issue.id)"
            :badge-fields="badgeFieldsMap[issue.projectId] || []"
            @click="$emit('item-click', issue)"
            @dblclick="$emit('item-dblclick', issue)"
            @select="toggleSelect(issue)"
            @contextmenu="emit('item-contextmenu', { issue, event: $event })"
            @sprint-edit="emit('sprint-edit', $event)"
            @sprint-select="(iss, sprint) => emit('sprint-select', iss, sprint)"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { IconDown, IconDragDotVertical, IconCloseCircle, IconRefresh } from '@arco-design/web-vue/es/icon'
import Sortable from 'sortablejs'
import type { IssueVO, SprintVO } from '@/api/types'
import { EmptyState } from '@/components/base'
import type { DensityLevel, StructureMode } from '../composables'
import type { SortState } from '../composables/useIssueList'
import IssueListItem from './IssueListItem.vue'
import type { BadgeFieldConfig } from './badgeTypes'

interface IssueWithDesc extends IssueVO {
  description?: string
}

const props = withDefaults(defineProps<{
  issues: IssueWithDesc[]
  density: DensityLevel
  structure: StructureMode
  loading: boolean
  error: boolean
  sortState: SortState
  activeIssueId?: string | null
  focusedIssueId?: string | null
  selectedIds: Set<string>
  showCheckbox?: boolean
  draggable?: boolean
  isManualSorted?: boolean
  isOwnerOrder?: boolean
  sortedIssueIds?: string[]
  /** Sprint 选项缓存（projectId → SprintVO[]），用于列表模式内联编辑 */
  sprintOptionsCache?: Record<string, SprintVO[]>
  /** 当前正在加载 sprint 选项的 issue ID 集合 */
  sprintLoadingIds?: Set<string>
  /** 判断 issue 是否可编辑的函数（用于权限控制） */
  canEditIssue?: (issue: IssueWithDesc) => boolean
  /** 数字徽章字段配置（按 projectId 分组） */
  badgeFieldsMap?: Record<string, BadgeFieldConfig[]>
}>(), {
  showCheckbox: true,
  draggable: false,
  isManualSorted: false,
  isOwnerOrder: false,
  error: false,
  focusedIssueId: null,
  sprintOptionsCache: () => ({}),
  sprintLoadingIds: () => new Set(),
  canEditIssue: () => true,
  badgeFieldsMap: () => ({})
})

const emit = defineEmits<{
  (e: 'item-click', issue: IssueWithDesc): void
  (e: 'item-dblclick', issue: IssueWithDesc): void
  (e: 'item-contextmenu', payload: { issue: IssueWithDesc, event: MouseEvent }): void
  (e: 'sort-change', field: string): void
  (e: 'select', issue: IssueWithDesc): void
  (e: 'order-change', issueIds: string[]): void
  (e: 'discard-order'): void
  (e: 'retry'): void
  /** 列表模式 Sprint 内联编辑：请求加载 sprint 选项 */
  (e: 'sprint-edit', issue: IssueWithDesc): void
  /** 列表模式 Sprint 内联编辑：用户选择了某个 Sprint */
  (e: 'sprint-select', issue: IssueWithDesc, sprint: SprintVO | null): void
}>()

// Refs for Sortable.js
const sortedSectionRef = ref<HTMLElement | null>(null)
const flatListRef = ref<HTMLElement | null>(null)
const listContainerRef = ref<HTMLElement | null>(null)
let sortableInstance: Sortable | null = null

// Compute sorted vs unsorted issues
const sortedIssues = computed<IssueWithDesc[]>(() => {
  if (!props.isManualSorted || !props.sortedIssueIds?.length) return []
  const orderMap = new Map<string, number>()
  props.sortedIssueIds.forEach((id, idx) => orderMap.set(id, idx))
  return props.issues
    .filter(i => orderMap.has(i.id))
    .sort((a, b) => (orderMap.get(a.id) ?? 0) - (orderMap.get(b.id) ?? 0))
})

const unsortedIssues = computed<IssueWithDesc[]>(() => {
  if (!props.isManualSorted || !props.sortedIssueIds?.length) return []
  const orderSet = new Set(props.sortedIssueIds)
  return props.issues.filter(i => !orderSet.has(i.id))
})

// Expanded nodes for tree view
const expandedIds = ref<Set<string>>(new Set())

// Sort options
const sortOptions = [
  { value: 'updatedAt', label: '更新时间' },
  { value: 'createdAt', label: '创建时间' },
  { value: 'priority', label: '优先级' },
  { value: 'title', label: '标题' },
  { value: 'assigneeName', label: '负责人' },
  { value: 'issueKey', label: '编号' },
  { value: 'vote_count', label: '投票数' },
  { value: 'estimatedHours', label: '预估工时' },
  { value: 'spentHours', label: '已用工时' },
  { value: 'remaining', label: '剩余工时' }
]

const sortField = computed(() => props.sortState.field)
const sortDirection = computed(() => props.sortState.direction)
const sortFieldLabel = computed(() => {
  const opt = sortOptions.find(o => o.value === sortField.value)
  return opt?.label || '更新时间'
})

function onSortSelect(field: string) {
  emit('sort-change', field)
}

// Tree data: flatten issues into tree nodes
interface TreeNode {
  issue: IssueWithDesc
  depth: number
}

const treeData = computed<TreeNode[]>(() => {
  if (props.structure !== 'tree') return props.issues.map(i => ({ issue: i, depth: 0 }))
  return props.issues.map(issue => ({ issue, depth: 0 }))
})

function toggleExpand(issue: IssueWithDesc) {
  if (expandedIds.value.has(issue.id)) {
    expandedIds.value.delete(issue.id)
  } else {
    expandedIds.value.add(issue.id)
  }
  expandedIds.value = new Set(expandedIds.value)
}

function toggleSelect(issue: IssueWithDesc) {
  emit('select', issue)
}

// ========== Drag & Drop with Sortable.js ==========

function initSortable() {
  destroySortable()

  if (!props.draggable) return

  nextTick(() => {
    // Determine which container to make sortable
    const container = props.isManualSorted && sortedSectionRef.value
      ? sortedSectionRef.value
      : flatListRef.value

    if (!container) return

    sortableInstance = new Sortable(container, {
      animation: 200,
      handle: '.drag-handle',
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      dragClass: 'sortable-drag',
      onEnd: (evt) => {
        if (evt.oldIndex === undefined || evt.newIndex === undefined) return
        if (evt.oldIndex === evt.newIndex) return

        // Collect the new order from DOM
        const items = container.querySelectorAll('[data-id]')
        const newOrder: string[] = []
        items.forEach(el => {
          const id = (el as HTMLElement).dataset.id
          if (id) newOrder.push(id)
        })

        // If we have unsorted items, append them to the order
        if (props.isManualSorted && unsortedIssues.value.length > 0) {
          // Keep only sorted section order
          emit('order-change', newOrder)
        } else {
          emit('order-change', newOrder)
        }
      }
    })
  })
}

function destroySortable() {
  if (sortableInstance) {
    sortableInstance.destroy()
    sortableInstance = null
  }
}

// Watch for draggable and issues changes to reinit sortable
watch(() => [props.draggable, props.issues, props.isManualSorted], () => {
  if (props.draggable) {
    nextTick(() => initSortable())
  } else {
    destroySortable()
  }
}, { flush: 'post' })

onMounted(() => {
  if (props.draggable) {
    nextTick(() => initSortable())
  }
})

onUnmounted(() => {
  destroySortable()
})
</script>

<style scoped>
.issue-list-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.list-sort-bar {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  border-bottom: 1px solid var(--tf-border-secondary, var(--color-neutral-3));
  gap: 4px;
  flex-wrap: wrap;
}

.sort-label {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.sort-button {
  font-size: 12px;
  color: var(--tf-text-secondary, var(--color-text-2));
}

.sort-button .sort-dir-icon {
  margin-left: 2px;
  font-size: 11px;
}

.sort-active {
  color: var(--tf-accent, var(--color-primary-6));
  font-weight: 500;
}

/* Manual order indicator */
.manual-order-indicator {
  display: flex;
  align-items: center;
  margin-left: auto;
  gap: 8px;
}

.manual-order-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--tf-text-tertiary, var(--color-text-3));
}

.discard-order-btn {
  font-size: 11px;
  color: var(--tf-accent, var(--color-primary-6));
  padding: 0 4px;
}

.discard-order-btn:hover {
  text-decoration: underline;
}

/* Separator between sorted and unsorted */
.manual-order-separator {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  gap: 8px;
}

.separator-line {
  flex: 1;
  height: 1px;
  background: var(--tf-border-secondary, var(--color-neutral-3));
}

.separator-text {
  font-size: 11px;
  color: var(--tf-text-quaternary, var(--color-text-4));
  white-space: nowrap;
}

.list-items {
  flex: 1;
  overflow-y: auto;
}

.list-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  font-size: 13px;
}

.list-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

/* Error state */
.list-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.list-error .error-icon {
  font-size: 32px;
  color: var(--tf-danger);
  margin-bottom: 12px;
}

.list-error .error-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-secondary, var(--color-text-2));
  margin: 0 0 4px;
}

.list-error .error-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  margin: 0 0 16px;
}

/* Sortable.js styles */
.sortable-ghost {
  opacity: 0.4;
  background: var(--tf-bg-hover, var(--color-fill-2));
}

.sortable-chosen {
  background: var(--tf-bg-active, var(--color-fill-3));
}

.sortable-drag {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
</style>
