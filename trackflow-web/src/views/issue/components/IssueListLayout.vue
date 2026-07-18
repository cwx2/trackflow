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
    </div>

    <!-- Issue items -->
    <div v-if="loading" class="list-loading">
      <a-spin :size="20" />
      <span>加载中...</span>
    </div>

    <div v-else-if="issues.length === 0" class="list-empty">
      <icon-search class="empty-icon" />
      <p class="empty-title">暂无工单</p>
      <p class="empty-desc">尝试调整筛选条件或创建新的工单</p>
    </div>

    <div v-else class="list-items">
      <!-- Tree mode: render with indentation -->
      <template v-if="structure === 'tree'">
        <template v-for="node in treeData" :key="node.issue.id">
          <IssueListItem
            :issue="node.issue"
            :density="density"
            :indent="node.depth"
            :has-children="(node.issue.childCount || 0) > 0"
            :expanded="expandedIds.has(node.issue.id)"
            :active="activeIssueId === node.issue.id"
            :selected="selectedIds.has(node.issue.id)"
            :show-checkbox="showCheckbox"
            @click="$emit('item-click', node.issue)"
            @dblclick="$emit('item-dblclick', node.issue)"
            @toggle-expand="toggleExpand(node.issue)"
            @select="toggleSelect(node.issue)"
          />
        </template>
      </template>

      <!-- Flat mode: simple list -->
      <template v-else>
        <IssueListItem
          v-for="issue in issues"
          :key="issue.id"
          :issue="issue"
          :density="density"
          :indent="0"
          :has-children="false"
          :expanded="false"
          :active="activeIssueId === issue.id"
          :selected="selectedIds.has(issue.id)"
          :show-checkbox="showCheckbox"
          @click="$emit('item-click', issue)"
          @dblclick="$emit('item-dblclick', issue)"
          @select="toggleSelect(issue)"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { IconDown, IconSearch } from '@arco-design/web-vue/es/icon'
import type { IssueVO } from '@/api/types'
import type { DensityLevel, StructureMode } from '../composables'
import type { SortState } from '../composables/useIssueList'
import IssueListItem from './IssueListItem.vue'

interface IssueWithDesc extends IssueVO {
  description?: string
}

const props = withDefaults(defineProps<{
  issues: IssueWithDesc[]
  density: DensityLevel
  structure: StructureMode
  loading: boolean
  sortState: SortState
  activeIssueId?: string | null
  selectedIds: Set<string>
  showCheckbox?: boolean
}>(), {
  showCheckbox: true
})

const emit = defineEmits<{
  (e: 'item-click', issue: IssueWithDesc): void
  (e: 'item-dblclick', issue: IssueWithDesc): void
  (e: 'sort-change', field: string): void
  (e: 'select', issue: IssueWithDesc): void
}>()

// Expanded nodes for tree view
const expandedIds = ref<Set<string>>(new Set())

// Sort options
const sortOptions = [
  { value: 'updatedAt', label: '更新时间' },
  { value: 'createdAt', label: '创建时间' },
  { value: 'priority', label: '优先级' },
  { value: 'title', label: '标题' },
  { value: 'assigneeName', label: '负责人' },
  { value: 'issueKey', label: '编号' }
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

// Tree data: flatten issues into tree nodes (only shows current page issues)
interface TreeNode {
  issue: IssueWithDesc
  depth: number
}

const treeData = computed<TreeNode[]>(() => {
  if (props.structure !== 'tree') return props.issues.map(i => ({ issue: i, depth: 0 }))
  // In tree mode, all issues are at depth 0 (parent resolution requires separate API)
  // For now, just show them flat; expanded children will be loaded separately
  return props.issues.map(issue => ({ issue, depth: 0 }))
})

function toggleExpand(issue: IssueWithDesc) {
  if (expandedIds.value.has(issue.id)) {
    expandedIds.value.delete(issue.id)
  } else {
    expandedIds.value.add(issue.id)
  }
  expandedIds.value = new Set(expandedIds.value) // trigger reactivity
}

function toggleSelect(issue: IssueWithDesc) {
  emit('select', issue)
}
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

.list-empty .empty-icon {
  font-size: 32px;
  color: var(--tf-text-quaternary, var(--color-text-4));
  margin-bottom: 12px;
}

.list-empty .empty-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--tf-text-secondary, var(--color-text-2));
  margin: 0 0 4px;
}

.list-empty .empty-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary, var(--color-text-3));
  margin: 0;
}
</style>
