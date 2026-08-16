<template>
  <!-- ===== FilterBar：搜索/筛选模式切换 + Saved Query chip + 筛选编辑器
       - 搜索模式：QueryInput 文本搜索
       - 筛选模式：IssueFilterEditor chip 筛选（字段从 API 动态加载） ===== -->
  <div class="filter-bar-container">
    <!-- Mode toggle -->
    <div class="mode-toggle">
      <button
        class="mode-btn"
        :class="{ active: mode === 'filter' }"
        title="切换到筛选模式"
        @click="mode = 'filter'"
      >
        <icon-filter />
      </button>
      <button
        class="mode-btn"
        :class="{ active: mode === 'search' }"
        title="切换到搜索模式"
        @click="mode = 'search'"
      >
        <icon-search />
      </button>
    </div>

    <!-- ===== 搜索模式 ===== -->
    <div v-if="mode === 'search'" class="search-mode">
      <!-- Saved Query chip -->
      <a-tooltip v-if="activeQueryName && !isOwnedQuery" :content="readonlyFilterTooltip" position="bottom" mini>
        <div class="saved-query-chip">
          <span class="sq-chip-icon">🔍</span>
          <span class="sq-chip-name">{{ activeQueryName }}</span>
          <span class="sq-chip-close" @click.stop="handleClearQuery" title="清除查询">✕</span>
        </div>
      </a-tooltip>
      <a-tooltip v-else-if="activeQueryName && isOwnedQuery" :content="ownedFilterTooltip" position="bottom" mini>
        <div class="saved-query-chip clickable">
          <span class="sq-chip-icon">🔍</span>
          <span class="sq-chip-name" @click="handleChipClick" title="点击编辑查询">{{ activeQueryName }}</span>
          <span class="sq-chip-close" @click.stop="handleClearQuery" title="清除查询">✕</span>
        </div>
      </a-tooltip>
      <QueryInput
        v-model="searchKeyword"
        :placeholder="activeQueryName ? '搜索工单...' : '输入搜索请求 (如 状态: 未关闭  负责人: 我)'"
        :status-list="statusList"
        :project-list="projectList"
        :project-id="projectId"
        @submit="emitSearch"
      />
    </div>

    <!-- ===== 筛选模式 ===== -->
    <div v-else class="filter-mode">
      <div class="filter-mode-content">
        <!-- Saved Query chip in filter mode -->
        <a-tooltip v-if="activeQueryName && !isOwnedQuery" :content="readonlyFilterTooltip" position="bottom" mini>
          <div class="saved-query-chip filter-mode-chip">
            <span class="sq-chip-icon">🔍</span>
            <span class="sq-chip-name">{{ activeQueryName }}</span>
            <span class="sq-chip-close" @click.stop="handleClearQuery" title="清除查询">✕</span>
          </div>
        </a-tooltip>
        <a-tooltip v-else-if="activeQueryName && isOwnedQuery" :content="ownedFilterTooltip" position="bottom" mini>
          <div class="saved-query-chip filter-mode-chip clickable">
            <span class="sq-chip-icon">🔍</span>
            <span class="sq-chip-name" @click="handleChipClick" title="点击编辑查询">{{ activeQueryName }}</span>
            <span class="sq-chip-close" @click.stop="handleClearQuery" title="清除查询">✕</span>
          </div>
        </a-tooltip>

        <!-- IssueFilterEditor -->
        <IssueFilterEditor
          :project-id="projectId"
          :model-value="filterConditions"
          :status-list="statusList"
          :project-list="projectList"
          @update:model-value="onFilterChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * FilterBar — 搜索/筛选模式切换容器
 *
 * 职责：
 * - 管理搜索模式（QueryInput）和筛选模式（IssueFilterEditor）的切换
 * - 维护 Saved Query chip 的展示和交互
 * - 向父组件发送统一的 filter/search 事件
 *
 * 对外接口：
 * - Props：projectId, statusList, projectList, activeQueryName, queryFilters 等
 * - Emits：search（文本搜索）, filter（FilterCondition[] 筛选条件）, mode-change, clear-query, chip-click
 * - defineExpose：clearAll, setFilters, setSearchKeyword
 */
import { ref, computed, watch, nextTick } from 'vue'
import { IconFilter, IconSearch } from '@arco-design/web-vue/es/icon'
import type { IssueStatusVO, ProjectVO } from '@/api/types'
import { IssueFilterEditor } from '@/components/base'
import type { FilterCondition } from '@/components/base'
import QueryInput from './QueryInput.vue'
import { parseSearchQuery } from '../utils/parseSearchQuery'
import type { FieldValueContext } from '../utils/parseSearchQuery'
import { useAuthStore } from '@/stores/auth'

// ===== Types =====

export interface InitialFilter {
  fieldKey: string
  operator: string
  values: string[]
  valueLabels?: string[]
}

// ===== Props & Emits =====

const props = defineProps<{
  projectId?: string | null
  statusList: IssueStatusVO[]
  projectList: ProjectVO[]
  initialFilters?: InitialFilter[]
  activeQueryName?: string | null
  isOwnedQuery?: boolean
  readonlyFilterLabels?: string[]
  /** Saved Query 的筛选条件（已解析为 InitialFilter 格式），用于切换到筛选模式时预填 chips */
  queryFilters?: InitialFilter[]
}>()

const emit = defineEmits<{
  (e: 'search', keyword: string): void
  (e: 'filter', filters: FilterCondition[]): void
  (e: 'mode-change', mode: 'search' | 'filter'): void
  (e: 'clear-query'): void
  (e: 'chip-click'): void
}>()

// ===== State =====

const mode = ref<'search' | 'filter'>('search')
const searchKeyword = ref('')
const filterConditions = ref<FilterCondition[]>([])
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null
let suppressEmit = false
const authStore = useAuthStore()

// ===== Mode change =====

watch(mode, (newMode) => {
  emit('mode-change', newMode)
  if (suppressEmit) return
  if (newMode === 'search') {
    // 切换到搜索模式
    if (props.activeQueryName && filterConditions.value.length > 0) {
      filterConditions.value = []
    }
    emitSearch()
  } else {
    // 切换到筛选模式
    searchKeyword.value = ''
    if (props.activeQueryName && props.queryFilters && props.queryFilters.length > 0) {
      // 有 Saved Query 时预填条件，不触发 emit
      filterConditions.value = initialFiltersToConditions(props.queryFilters)
    } else {
      emitFilters()
    }
  }
})

// ===== Search Mode =====

watch(searchKeyword, () => {
  if (mode.value !== 'search') return
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    emitSearch()
  }, 300)
})

function emitSearch() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  const raw = searchKeyword.value.trim()
  // 构建字段值上下文：将 statusList 等 Props 转为 label→code 映射供解析器使用
  const fieldContext: FieldValueContext = {
    statusOptions: props.statusList.map(s => ({
      label: s.displayName || s.name,
      code: s.code,
      displayName: s.displayName,
    })),
    currentUserId: authStore.user?.userId || authStore.user?.id || undefined,
  }
  // 解析结构化查询：如果包含已知字段名（如"状态: 未关闭"），
  // 路由到 filter 通道走 QueryExecutor，确保下拉建议与列表结果一致（REQ-808）
  const parsed = parseSearchQuery(raw, fieldContext)
  if (parsed.hasStructuredFields) {
    emit('filter', parsed.filters)
  } else {
    // 纯文本关键词：直接走 keyword 搜索
    // 注意：parseSearchQuery 对纯文本也会返回 [{field:'keyword',...}]，
    // 但为了兼容性和性能，纯文本仍走原有的 keyword 路径
    emit('search', raw)
  }
}

// ===== Filter Mode =====

function onFilterChange(conditions: FilterCondition[]) {
  filterConditions.value = conditions
  emitFilters()
}

function emitFilters() {
  emit('filter', filterConditions.value)
}

// ===== Saved Query chip =====

function handleClearQuery() {
  emit('clear-query')
}

function handleChipClick() {
  emit('chip-click')
}

const readonlyFilterTooltip = computed(() => {
  if (!props.readonlyFilterLabels || props.readonlyFilterLabels.length === 0) {
    return '无筛选条件'
  }
  return props.readonlyFilterLabels.join('　')
})

const ownedFilterTooltip = computed(() => {
  if (!props.readonlyFilterLabels || props.readonlyFilterLabels.length === 0) {
    return '无筛选条件 · 点击编辑'
  }
  return props.readonlyFilterLabels.join('　') + '　· 点击编辑'
})

// ===== 外部接入 =====

/** 将旧的 InitialFilter[] 格式转换为 FilterCondition[] */
function initialFiltersToConditions(filters: InitialFilter[]): FilterCondition[] {
  return filters.map(f => {
    // 映射旧的 fieldKey 到新的 field 名称
    const fieldMap: Record<string, string> = {
      status: 'status',
      priority: 'cf.1000000000000000001',
      issueType: 'cf.1000000000000000002',
      dueDate: 'cf.1000000000000000003',
    }
    const field = fieldMap[f.fieldKey] || f.fieldKey
    // 映射旧的操作符到新的
    const opMap: Record<string, string> = {
      is: 'in',
      is_not: 'not_in',
      any_of: 'in',
      none_of: 'not_in',
      after: 'gte',
      before: 'lte',
      today: 'relative',
      yesterday: 'relative',
      this_week: 'relative',
      last_week: 'relative',
      last_7_days: 'relative',
      this_month: 'relative',
      last_30_days: 'relative',
      overdue: 'relative',
      has: 'is_not_empty',
      has_not: 'is_empty',
    }
    const operator = opMap[f.operator] || f.operator
    // 对日期相对操作符，值为操作符名
    const relativeOps = new Set(['today', 'yesterday', 'this_week', 'last_week', 'last_7_days', 'this_month', 'last_30_days', 'overdue'])
    let values = f.values
    if (relativeOps.has(f.operator)) {
      values = [f.operator]
    }
    const cond: FilterCondition = { field, operator }
    if (values.length > 0) cond.value = values
    // 保留人类可读的值标签，供 chip 展示用
    if (f.valueLabels && f.valueLabels.length > 0) {
      cond.valueLabels = f.valueLabels
    }
    return cond
  })
}

// Initialize with external filters
watch(() => props.initialFilters, (filters) => {
  if (filters && filters.length > 0) {
    applyInitialFilters(filters)
  }
}, { immediate: true })

function applyInitialFilters(filters: InitialFilter[]) {
  suppressEmit = true
  mode.value = 'filter'
  filterConditions.value = initialFiltersToConditions(filters)
  nextTick(() => { suppressEmit = false })
}

// ===== Exposed Methods =====

function clearAll() {
  searchKeyword.value = ''
  filterConditions.value = []
  suppressEmit = true
  mode.value = 'search'
  nextTick(() => { suppressEmit = false })
}

function setFilters(filters: InitialFilter[]) {
  if (filters.length > 0) {
    applyInitialFilters(filters)
  } else {
    filterConditions.value = []
    suppressEmit = true
    mode.value = 'search'
    nextTick(() => { suppressEmit = false })
  }
}

function setSearchKeyword(keyword: string) {
  suppressEmit = true
  mode.value = 'search'
  searchKeyword.value = keyword
  nextTick(() => {
    suppressEmit = false
    emit('search', keyword)
  })
}

defineExpose({ clearAll, setFilters, setSearchKeyword })
</script>

<style scoped>
.filter-bar-container {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  flex: 1;
  min-width: 0;
}

/* Mode toggle */
.mode-toggle {
  display: flex;
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-md);
  overflow: hidden;
  flex-shrink: 0;
}

.mode-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.mode-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.mode-btn.active {
  background: var(--tf-accent-bg);
  color: var(--tf-accent);
}

.mode-btn + .mode-btn {
  border-left: 1px solid var(--tf-border);
}

/* Search mode */
.search-mode {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
}

/* Saved Query chip */
.saved-query-chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 4px 0 8px;
  background: var(--tf-accent-bg);
  border: 1px solid var(--tf-accent);
  border-radius: var(--tf-radius-md);
  font-size: 12px;
  gap: 4px;
  flex-shrink: 0;
  transition: border-color 0.15s;
}

.saved-query-chip.clickable .sq-chip-name {
  cursor: pointer;
}

.saved-query-chip.clickable .sq-chip-name:hover {
  text-decoration: underline;
  opacity: 0.85;
}

.saved-query-chip.filter-mode-chip {
  margin-right: 2px;
}

.sq-chip-icon {
  font-size: 11px;
  flex-shrink: 0;
}

.sq-chip-name {
  color: var(--tf-accent);
  font-weight: 500;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sq-chip-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 10px;
  color: var(--tf-accent);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.sq-chip-close:hover {
  background: var(--tf-accent);
  color: var(--tf-text-on-accent);
}

/* Filter mode */
.filter-mode {
  flex: 1;
  min-width: 0;
}

.filter-mode-content {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
