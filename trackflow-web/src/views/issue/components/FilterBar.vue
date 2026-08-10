<template>
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

    <!-- Search mode -->
    <div v-if="mode === 'search'" class="search-mode">
      <!-- Saved Query chip (displayed when a saved query is active) -->
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

    <!-- Filter mode -->
    <div v-else class="filter-mode">
      <div class="filter-chips">
        <!-- Saved Query chip (displayed when a saved query is active in filter mode) -->
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

        <!-- Active filter chips -->
        <div
          v-for="(chip, index) in activeFilters"
          :key="chip.fieldKey"
          class="filter-chip"
        >
          <span class="chip-field" @click="openFieldSelector(index)">{{ chip.fieldLabel }}</span>
          <span class="chip-operator" @click="openOperatorSelector(index)">{{ chip.operatorLabel }}</span>
          <span class="chip-value" @click="openValueSelector(index)">{{ chip.valueLabel || '选择...' }}</span>
          <span class="chip-remove" @click="removeFilter(index)">✕</span>
        </div>

        <!-- Add filter button / inline input -->
        <div class="add-filter-area">
          <div v-if="showFieldInput" class="field-input-wrapper" ref="fieldInputWrapperRef">
            <input
              ref="fieldInputRef"
              v-model="fieldSearchText"
              class="field-input"
              placeholder="输入属性名..."
              @keydown.escape="cancelFieldInput"
              @keydown.enter="selectFirstFieldSuggestion"
              @keydown.down.prevent="moveFieldSuggestion(1)"
              @keydown.up.prevent="moveFieldSuggestion(-1)"
            />
            <!-- Field suggestions dropdown -->
            <div v-if="filteredFieldSuggestions.length > 0" class="suggestions-dropdown">
              <div
                v-for="(field, i) in filteredFieldSuggestions"
                :key="field.key"
                class="suggestion-item"
                :class="{ active: fieldSuggestionIndex === i }"
                @click="selectField(field)"
                @mouseenter="fieldSuggestionIndex = i"
              >
                <span class="suggestion-icon">{{ field.icon }}</span>
                <span class="suggestion-label">{{ field.label }}</span>
              </div>
            </div>
          </div>
          <button v-else class="add-filter-btn" @click="startAddFilter">
            <icon-plus :size="12" />
            <span>添加筛选</span>
          </button>
        </div>
      </div>

      <!-- Operator selector popup -->
      <Teleport to="body">
        <div v-if="showOperatorPopup" class="popup-overlay">
          <div class="popup-panel" :style="popupPosition" ref="operatorPopupRef">
            <div class="popup-title">选择操作符</div>
            <div
              v-for="op in currentFieldOperators"
              :key="op.key"
              class="popup-item"
              :class="{ active: editingChip && activeFilters[editingChip.index]?.operator === op.key }"
              @click="selectOperator(op)"
            >
              {{ op.label }}
            </div>
          </div>
        </div>
      </Teleport>

      <!-- Value selector popup -->
      <Teleport to="body">
        <div v-if="showValuePopup" class="popup-overlay">
          <div class="popup-panel value-popup" :style="popupPosition" ref="valuePopupRef">
            <!-- Date/text input mode (for date fields and parent ID) -->
            <template v-if="editingFieldType === 'date' || (editingFieldType === 'text' && editingFieldKey === 'parent')">
              <div class="text-input-popup">
                <div class="text-input-label">
                  {{ editingFieldKey === 'parent' ? '输入父工单 ID' : (editingOperator === 'between' ? '起始日期 (yyyy-MM-dd)' : '日期 (yyyy-MM-dd)') }}
                </div>
                <input
                  ref="valueSearchRef"
                  v-model="dateInputValue"
                  class="popup-search-input"
                  :placeholder="editingFieldKey === 'parent' ? '工单 ID...' : 'yyyy-MM-dd'"
                  @keydown.escape="closeAllPopups"
                  @keydown.enter="confirmTextInput"
                />
                <div v-if="editingOperator === 'between'" class="text-input-label" style="margin-top: 8px">
                  结束日期 (yyyy-MM-dd)
                </div>
                <input
                  v-if="editingOperator === 'between'"
                  v-model="dateInputValue2"
                  class="popup-search-input"
                  placeholder="yyyy-MM-dd"
                  @keydown.escape="closeAllPopups"
                  @keydown.enter="confirmTextInput"
                />
                <div class="popup-footer">
                  <button class="popup-confirm-btn" @click="confirmTextInput">确定</button>
                </div>
              </div>
            </template>
            <!-- Dropdown mode (for enum/user fields) -->
            <template v-else>
              <div class="popup-search">
                <input
                  ref="valueSearchRef"
                  v-model="valueSearchText"
                  class="popup-search-input"
                  placeholder="搜索值..."
                  @keydown.escape="closeAllPopups"
                  @keydown.enter="selectFirstValueSuggestion"
                  @keydown.down.prevent="moveValueSuggestion(1)"
                  @keydown.up.prevent="moveValueSuggestion(-1)"
                />
              </div>
              <div v-if="valueOptionsLoading" class="popup-loading">
                <span class="loading-spinner"></span>
              </div>
              <div v-else class="popup-options">
                <div
                  v-for="(opt, i) in filteredValueOptions"
                  :key="opt.id"
                  class="popup-item"
                  :class="{
                    active: valueSuggestionIndex === i,
                    selected: isValueSelected(opt.id),
                    special: opt.isSpecial
                  }"
                  @click="toggleValue(opt)"
                  @mouseenter="valueSuggestionIndex = i"
                >
                  <span v-if="isMultiSelect" class="check-icon">{{ isValueSelected(opt.id) ? '☑' : '☐' }}</span>
                  <span v-if="opt.isSpecial" class="special-icon">👤</span>
                  <span v-else-if="opt.color" class="value-dot" :style="{ background: opt.color }"></span>
                  <span class="value-label">{{ opt.label }}</span>
                </div>
                <div v-if="filteredValueOptions.length === 0" class="popup-empty">无匹配选项</div>
              </div>
              <div v-if="isMultiSelect && tempSelectedValues.length > 0" class="popup-footer">
                <button class="popup-confirm-btn" @click="confirmMultiSelect">确定</button>
              </div>
            </template>
          </div>
        </div>
      </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { IconFilter, IconSearch, IconPlus } from '@arco-design/web-vue/es/icon'
import { projectApi, sprintApi, tagApi } from '@/api'
import type { IssueStatusVO, IssueTagVO, ProjectVO, SprintVO } from '@/api/types'
import { localizeStatusName, issueTypeLabelMap } from '@/utils/fieldLabels'
import { PRIORITY_COLORS } from '@/utils/issueColors'
import { loadIssueTypeOptions } from '../composables/useIssueTypeOptions'
import QueryInput from './QueryInput.vue'

// ==================== Types ====================

interface FilterField {
  key: string
  label: string
  icon: string
  type: 'enum' | 'user' | 'date' | 'text'
  operators: OperatorDef[]
}

interface OperatorDef {
  key: string
  label: string
  multi?: boolean  // supports multiple values
}

interface ValueOption {
  id: string
  label: string
  color?: string
  isSpecial?: boolean // For special options like "Me (current user)"
}

interface FilterChip {
  fieldKey: string
  fieldLabel: string
  operator: string
  operatorLabel: string
  values: string[]       // selected value IDs
  valueLabel: string     // display text
}

// ==================== Props & Emits ====================

export interface InitialFilter {
  fieldKey: string
  operator: string
  values: string[]
  valueLabels?: string[]
}

const props = defineProps<{
  projectId?: string | null
  statusList: IssueStatusVO[]
  projectList: ProjectVO[]
  initialFilters?: InitialFilter[]
  activeQueryName?: string | null
  isOwnedQuery?: boolean
  readonlyFilterLabels?: string[]
}>()

const emit = defineEmits<{
  (e: 'search', keyword: string): void
  (e: 'filter', filters: Record<string, any>): void
  (e: 'mode-change', mode: 'search' | 'filter'): void
  (e: 'clear-query'): void
  (e: 'chip-click'): void
}>()

// ==================== Field Definitions ====================

const OPERATORS_ENUM: OperatorDef[] = [
  { key: 'is', label: '是' },
  { key: 'is_not', label: '不是' },
  { key: 'any_of', label: '属于', multi: true },
  { key: 'none_of', label: '不属于', multi: true },
]

const OPERATORS_USER: OperatorDef[] = [
  { key: 'is', label: '是' },
  { key: 'is_not', label: '不是' },
  { key: 'any_of', label: '属于', multi: true },
  { key: 'none_of', label: '不属于', multi: true },
]

const OPERATORS_DATE: OperatorDef[] = [
  { key: 'today', label: '今天' },
  { key: 'yesterday', label: '昨天' },
  { key: 'this_week', label: '本周' },
  { key: 'last_week', label: '上周' },
  { key: 'last_7_days', label: '最近 7 天' },
  { key: 'this_month', label: '本月' },
  { key: 'last_30_days', label: '最近 30 天' },
  { key: 'after', label: '晚于' },
  { key: 'before', label: '早于' },
  { key: 'between', label: '在范围内' },
]

/**
 * 截止日期特殊操作符：包含快捷选项（逾期/今天/本周）+ 日期范围
 */
const OPERATORS_DUE_DATE: OperatorDef[] = [
  { key: 'overdue', label: '已逾期' },      // due_date < today AND not closed
  { key: 'today', label: '今天到期' },       // due_date = today
  { key: 'this_week', label: '本周到期' },   // due_date in [today, today+7]
  { key: 'after', label: '晚于' },
  { key: 'before', label: '早于' },
  { key: 'between', label: '在范围内' },
]

const OPERATORS_PARENT: OperatorDef[] = [
  { key: 'is', label: '是' },
  { key: 'has', label: '有父工单' },
  { key: 'has_not', label: '无父工单' },
]

const FILTER_FIELDS: FilterField[] = [
  { key: 'project', label: '项目', icon: '📁', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'status', label: '状态', icon: '🔵', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'priority', label: '优先级', icon: '🔴', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'assignee', label: '负责人', icon: '👤', type: 'user', operators: OPERATORS_USER },
  { key: 'issueType', label: '类型', icon: '📋', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'sprint', label: 'Sprint', icon: '🏃', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'tag', label: '标签', icon: '🏷️', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'parent', label: '父工单', icon: '🔗', type: 'text', operators: OPERATORS_PARENT },
  { key: 'dueDate', label: '截止日期', icon: '⏰', type: 'date', operators: OPERATORS_DUE_DATE },
  { key: 'createdAt', label: '创建时间', icon: '📅', type: 'date', operators: OPERATORS_DATE },
  { key: 'updatedAt', label: '更新时间', icon: '📅', type: 'date', operators: OPERATORS_DATE },
]

// ==================== State ====================

const mode = ref<'search' | 'filter'>('search')
const searchKeyword = ref('')
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// Filter chips
const activeFilters = ref<FilterChip[]>([])

// Add filter
const showFieldInput = ref(false)
const fieldSearchText = ref('')
const fieldInputRef = ref<HTMLInputElement | null>(null)
const fieldInputWrapperRef = ref<HTMLElement | null>(null)
const fieldSuggestionIndex = ref(0)

// Editing state
const editingChip = ref<{ index: number; part: 'field' | 'operator' | 'value' } | null>(null)

// Popups
const showOperatorPopup = ref(false)
const showValuePopup = ref(false)
const popupPosition = ref<{ top: string; left: string }>({ top: '0px', left: '0px' })
const valueSearchText = ref('')
const valueSearchRef = ref<HTMLInputElement | null>(null)
const valueSuggestionIndex = ref(0)
const valueOptionsLoading = ref(false)
const valueOptions = ref<ValueOption[]>([])
const tempSelectedValues = ref<string[]>([])
const operatorPopupRef = ref<HTMLElement | null>(null)
const valuePopupRef = ref<HTMLElement | null>(null)

// Date/text input state
const dateInputValue = ref('')
const dateInputValue2 = ref('')  // For "between" operator (end date)

// Computed: which field type is currently being edited
const editingFieldType = computed(() => {
  if (!editingChip.value) return null
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return null
  const field = FILTER_FIELDS.find(f => f.key === chip.fieldKey)
  return field?.type || null
})

const editingFieldKey = computed(() => {
  if (!editingChip.value) return null
  const chip = activeFilters.value[editingChip.value.index]
  return chip?.fieldKey || null
})

const editingOperator = computed(() => {
  if (!editingChip.value) return null
  const chip = activeFilters.value[editingChip.value.index]
  return chip?.operator || null
})

// ==================== Computed ====================

const filteredFieldSuggestions = computed(() => {
  const usedKeys = new Set(activeFilters.value.map(f => f.fieldKey))
  const available = FILTER_FIELDS.filter(f => !usedKeys.has(f.key))
  if (!fieldSearchText.value.trim()) return available
  const kw = fieldSearchText.value.toLowerCase()
  return available.filter(f =>
    f.label.toLowerCase().includes(kw) || f.key.toLowerCase().includes(kw)
  )
})

const currentFieldOperators = computed(() => {
  if (!editingChip.value) return []
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return []
  const field = FILTER_FIELDS.find(f => f.key === chip.fieldKey)
  return field?.operators || []
})

const isMultiSelect = computed(() => {
  if (!editingChip.value) return false
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return false
  return chip.operator === 'any_of' || chip.operator === 'none_of'
})

const filteredValueOptions = computed(() => {
  if (!valueSearchText.value.trim()) return valueOptions.value
  const kw = valueSearchText.value.toLowerCase()
  return valueOptions.value.filter(o => o.label.toLowerCase().includes(kw))
})

// ==================== Mode change ====================

watch(mode, (newMode) => {
  emit('mode-change', newMode)
  if (suppressEmit) return
  if (newMode === 'search') {
    // Clear filters, apply search
    emitSearch()
  } else {
    // Clear search, apply filters
    searchKeyword.value = ''
    emitFilters()
  }
})

// ==================== Search Mode ====================

watch(searchKeyword, () => {
  if (mode.value !== 'search') return
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    emitSearch()
  }, 300)
})

function emitSearch() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  emit('search', searchKeyword.value.trim())
}


function handleClearQuery() {
  emit('clear-query')
}

// ==================== Saved Query chip click ====================

function handleChipClick() {
  // Only owned queries are clickable — emit chip-click so parent opens edit dialog
  emit('chip-click')
}

// Tooltip content for non-owned queries (shows filter conditions on hover)
const readonlyFilterTooltip = computed(() => {
  if (!props.readonlyFilterLabels || props.readonlyFilterLabels.length === 0) {
    return '无筛选条件'
  }
  return props.readonlyFilterLabels.join('　')
})

// Tooltip content for owned queries (shows filter conditions + edit hint)
const ownedFilterTooltip = computed(() => {
  if (!props.readonlyFilterLabels || props.readonlyFilterLabels.length === 0) {
    return '无筛选条件 · 点击编辑'
  }
  return props.readonlyFilterLabels.join('　') + '　· 点击编辑'
})

// ==================== Filter Mode - Add Filter ====================

function startAddFilter() {
  showFieldInput.value = true
  fieldSearchText.value = ''
  fieldSuggestionIndex.value = 0
  nextTick(() => fieldInputRef.value?.focus())
}

function cancelFieldInput() {
  showFieldInput.value = false
  fieldSearchText.value = ''
}

function moveFieldSuggestion(delta: number) {
  const len = filteredFieldSuggestions.value.length
  if (len === 0) return
  fieldSuggestionIndex.value = (fieldSuggestionIndex.value + delta + len) % len
}

function selectFirstFieldSuggestion() {
  if (filteredFieldSuggestions.value.length > 0) {
    selectField(filteredFieldSuggestions.value[fieldSuggestionIndex.value])
  }
}

function selectField(field: FilterField) {
  const defaultOp = field.operators[0]
  const newChip: FilterChip = {
    fieldKey: field.key,
    fieldLabel: field.label,
    operator: defaultOp.key,
    operatorLabel: defaultOp.label,
    values: [],
    valueLabel: ''
  }

  // For date fields with relative shortcut as default, set initial values/label immediately
  if ((field.key === 'createdAt' || field.key === 'updatedAt') && RELATIVE_DATE_SHORTCUTS.has(defaultOp.key)) {
    const labelMap: Record<string, string> = {
      today: '今天', yesterday: '昨天', this_week: '本周',
      last_week: '上周', last_7_days: '最近 7 天', this_month: '本月', last_30_days: '最近 30 天'
    }
    newChip.values = ['_' + defaultOp.key]
    newChip.valueLabel = labelMap[defaultOp.key] || defaultOp.key
  }

  activeFilters.value.push(newChip)
  showFieldInput.value = false
  fieldSearchText.value = ''

  // Automatically open value selector for the new chip
  nextTick(() => {
    openValueSelector(activeFilters.value.length - 1)
  })
}

// ==================== Filter Mode - Operator ====================

function openOperatorSelector(index: number) {
  editingChip.value = { index, part: 'operator' }
  const chipEl = document.querySelectorAll('.filter-chip')[index]
  if (chipEl) {
    const rect = chipEl.getBoundingClientRect()
    popupPosition.value = {
      top: `${rect.bottom + 4}px`,
      left: `${rect.left}px`
    }
  }
  showOperatorPopup.value = true
  showValuePopup.value = false
}

function selectOperator(op: OperatorDef) {
  if (!editingChip.value) return
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return

  const wasMulti = chip.operator === 'any_of' || chip.operator === 'none_of'
  const nowMulti = op.multi || false

  chip.operator = op.key
  chip.operatorLabel = op.label

  // If switching from multi to single, keep only first value
  if (wasMulti && !nowMulti && chip.values.length > 1) {
    chip.values = [chip.values[0]]
    updateChipValueLabel(chip)
  }

  // For parent field with 'has' / 'has_not' operators, apply immediately (no value needed)
  if (chip.fieldKey === 'parent' && (op.key === 'has' || op.key === 'has_not')) {
    chip.values = ['_']
    chip.valueLabel = op.key === 'has' ? '是' : '否'
  }

  // For dueDate field shortcuts (overdue/today/this_week), apply immediately (no value needed)
  if (chip.fieldKey === 'dueDate') {
    if (op.key === 'overdue') {
      chip.values = ['_overdue']
      chip.valueLabel = '已逾期'
    } else if (op.key === 'today') {
      chip.values = ['_today']
      chip.valueLabel = '今天'
    } else if (op.key === 'this_week') {
      chip.values = ['_this_week']
      chip.valueLabel = '本周'
    }
  }

  // For createdAt/updatedAt relative shortcuts, apply immediately (no value needed)
  if ((chip.fieldKey === 'createdAt' || chip.fieldKey === 'updatedAt') && RELATIVE_DATE_SHORTCUTS.has(op.key)) {
    const labelMap: Record<string, string> = {
      today: '今天', yesterday: '昨天', this_week: '本周',
      last_week: '上周', last_7_days: '最近 7 天', this_month: '本月', last_30_days: '最近 30 天'
    }
    chip.values = ['_' + op.key]
    chip.valueLabel = labelMap[op.key] || op.key
  }

  showOperatorPopup.value = false
  emitFilters()
}

// ==================== Filter Mode - Value ====================

function openFieldSelector(index: number) {
  // For now, clicking field name opens operator (field is fixed after creation)
  openOperatorSelector(index)
}

async function openValueSelector(index: number) {
  editingChip.value = { index, part: 'value' }
  const chip = activeFilters.value[index]
  if (!chip) return

  // Position popup
  const chipEls = document.querySelectorAll('.filter-chip')
  const chipEl = chipEls[index]
  if (chipEl) {
    const rect = chipEl.getBoundingClientRect()
    popupPosition.value = {
      top: `${rect.bottom + 4}px`,
      left: `${rect.left}px`
    }
  }

  valueSearchText.value = ''
  valueSuggestionIndex.value = 0
  tempSelectedValues.value = [...chip.values]

  // Initialize date/text input values from existing chip values
  const field = FILTER_FIELDS.find(f => f.key === chip.fieldKey)
  if (field?.type === 'date' || (field?.type === 'text' && chip.fieldKey === 'parent')) {
    // For parent with 'has'/'has_not', don't open value popup
    if (chip.fieldKey === 'parent' && (chip.operator === 'has' || chip.operator === 'has_not')) {
      return
    }
    // For dueDate with shortcut operators (overdue/today/this_week), don't open value popup
    if (chip.fieldKey === 'dueDate' && ['overdue', 'today', 'this_week'].includes(chip.operator)) {
      return
    }
    // For createdAt/updatedAt with relative shortcut operators, don't open value popup
    if ((chip.fieldKey === 'createdAt' || chip.fieldKey === 'updatedAt') && RELATIVE_DATE_SHORTCUTS.has(chip.operator)) {
      return
    }
    dateInputValue.value = chip.values[0] && !chip.values[0].startsWith('_') ? chip.values[0] : ''
    dateInputValue2.value = chip.values[1] || ''
  }

  showValuePopup.value = true
  showOperatorPopup.value = false

  // Load options
  await loadValueOptions(chip.fieldKey)

  nextTick(() => valueSearchRef.value?.focus())
}

async function loadValueOptions(fieldKey: string) {
  valueOptionsLoading.value = true
  valueOptions.value = []

  try {
    switch (fieldKey) {
      case 'status':
        valueOptions.value = props.statusList.map(s => ({
          id: s.id,
          label: localizeStatusName(s.name),
          color: s.color
        }))
        break

      case 'priority':
        valueOptions.value = [
          { id: '阻塞', label: '阻塞', color: PRIORITY_COLORS['阻塞'] },
          { id: '紧急', label: '紧急', color: PRIORITY_COLORS['紧急'] },
          { id: '高', label: '高', color: PRIORITY_COLORS['高'] },
          { id: '普通', label: '普通', color: PRIORITY_COLORS['普通'] },
          { id: '低', label: '低', color: PRIORITY_COLORS['低'] },
        ]
        break

      case 'issueType': {
        // 从自定义字段系统动态加载工单类型选项
        // 优先使用当前项目ID；如果全部项目模式，尝试从已选的项目筛选条件获取项目ID
        const typeProjectFilter = activeFilters.value.find(f => f.fieldKey === 'project')
        const typePid = props.projectId || (typeProjectFilter?.values[0] || '')
        if (typePid) {
          const typeOpts = await loadIssueTypeOptions(typePid)
          // 防止异步竞态：确认当前编辑的仍是 issueType 字段
          if (editingChip.value && activeFilters.value[editingChip.value.index]?.fieldKey === 'issueType') {
            valueOptions.value = typeOpts.map(o => ({ id: o.value, label: o.label, color: o.color || undefined }))
          }
        } else {
          // 全部项目模式且无项目筛选条件时，回退到静态映射
          // 使用中文 label 作为 id，因为 DB 中 issue.issue_type 存储中文值
          valueOptions.value = Object.values(issueTypeLabelMap).map(label => ({ id: label, label }))
        }
        break
      }

      case 'project':
        valueOptions.value = props.projectList.map(p => ({
          id: p.id,
          label: `${p.key} - ${p.name}`
        }))
        break

      case 'assignee': {
        // Special options at the top (with visual distinction)
        const meOption: ValueOption = { id: 'me', label: '我（当前用户）', isSpecial: true }
        const unassignedOption: ValueOption = { id: 'none', label: '未分配', isSpecial: true }
        
        // Load project members (uses project:view permission, accessible to all project members)
        const assigneeProjectFilter = activeFilters.value.find(f => f.fieldKey === 'project')
        const assigneePid = assigneeProjectFilter?.values[0] || props.projectId
        if (assigneePid) {
          const res = await projectApi.listMembers(assigneePid, { _silent403: true })
          const members = res.data || []
          const memberOptions = members.map((m: any) => ({
            id: m.userId,
            label: m.displayName || m.username
          }))
          valueOptions.value = [meOption, unassignedOption, ...memberOptions]
        } else {
          // All projects mode — aggregate members from visible projects (deduplicated)
          const allMembers: ValueOption[] = []
          const seen = new Set<string>()
          const results = await Promise.allSettled(
            props.projectList.slice(0, 10).map(p => projectApi.listMembers(p.id, { _silent403: true }))
          )
          for (const result of results) {
            if (result.status === 'fulfilled' && result.value.data) {
              for (const m of result.value.data) {
                if (!seen.has(m.userId)) {
                  seen.add(m.userId)
                  allMembers.push({ id: m.userId, label: m.displayName || m.username })
                }
              }
            }
          }
          valueOptions.value = [meOption, unassignedOption, ...allMembers]
        }
        break
      }

      case 'sprint': {
        // Load sprints - if project is selected, load from that project
        const projectFilter = activeFilters.value.find(f => f.fieldKey === 'project')
        const pid = projectFilter?.values[0] || props.projectId
        if (pid) {
          const res = await sprintApi.listByProject(pid, { _silent403: true })
          const sprints = res.data?.list || []
          valueOptions.value = sprints.map((s: SprintVO) => ({
            id: s.id,
            label: `${s.name}${s.status === 'Active' ? ' (进行中)' : s.status === 'Completed' ? ' (已完成)' : ''}`
          }))
        } else {
          // Load from all projects
          const allSprints: ValueOption[] = []
          for (const p of props.projectList.slice(0, 5)) {
            try {
              const res = await sprintApi.listByProject(p.id, { _silent403: true })
              const sprints = res.data?.list || []
              sprints.forEach((s: SprintVO) => {
                allSprints.push({ id: s.id, label: `${p.key} / ${s.name}` })
              })
            } catch (e) {
              console.error(`[FilterBar] 加载项目 ${p.key} 的 Sprint 列表失败:`, e)
            }
          }
          valueOptions.value = allSprints
        }
        break
      }

      case 'tag': {
        // Load tags from the selected project (or all projects)
        const tagProjectFilter = activeFilters.value.find(f => f.fieldKey === 'project')
        const tagPid = tagProjectFilter?.values[0] || props.projectId
        if (tagPid) {
          const res = await tagApi.listProjectTags(tagPid, { _silent403: true })
          const tags = res.data || []
          valueOptions.value = tags.map((t: IssueTagVO) => ({
            id: t.id,
            label: t.name,
            color: t.color
          }))
        } else {
          // Aggregate tags from visible projects (deduplicated by name)
          const allTags: ValueOption[] = []
          const seen = new Set<string>()
          for (const p of props.projectList.slice(0, 10)) {
            try {
              const res = await tagApi.listProjectTags(p.id, { _silent403: true })
              const tags = res.data || []
              tags.forEach((t: IssueTagVO) => {
                if (!seen.has(t.id)) {
                  seen.add(t.id)
                  allTags.push({ id: t.id, label: `${t.name}`, color: t.color })
                }
              })
            } catch (e) {
              console.error(`[FilterBar] 加载项目标签失败:`, e)
            }
          }
          valueOptions.value = allTags
        }
        break
      }

      case 'parent':
      case 'createdAt':
      case 'updatedAt':
      case 'dueDate':
        // These use text/date input — no dropdown options needed
        valueOptions.value = []
        valueOptionsLoading.value = false
        return
    }
  } catch {
    valueOptions.value = []
  } finally {
    valueOptionsLoading.value = false
  }
}

function isValueSelected(id: string): boolean {
  return tempSelectedValues.value.includes(id)
}

function toggleValue(opt: ValueOption) {
  if (isMultiSelect.value) {
    // Multi-select: toggle in temp list
    const idx = tempSelectedValues.value.indexOf(opt.id)
    if (idx >= 0) {
      tempSelectedValues.value.splice(idx, 1)
    } else {
      tempSelectedValues.value.push(opt.id)
    }
  } else {
    // Single select: apply immediately
    if (!editingChip.value) return
    const chip = activeFilters.value[editingChip.value.index]
    if (!chip) return
    chip.values = [opt.id]
    updateChipValueLabel(chip)
    showValuePopup.value = false
    emitFilters()
  }
}

function confirmMultiSelect() {
  if (!editingChip.value) return
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return
  chip.values = [...tempSelectedValues.value]
  updateChipValueLabel(chip)
  showValuePopup.value = false
  emitFilters()
}

function confirmTextInput() {
  if (!editingChip.value) return
  const chip = activeFilters.value[editingChip.value.index]
  if (!chip) return

  if (chip.fieldKey === 'parent') {
    // Parent: the operator 'has' / 'has_not' don't need a value
    if (chip.operator === 'is') {
      const val = dateInputValue.value.trim()
      if (!val) return
      chip.values = [val]
      chip.valueLabel = val
    } else {
      // 'has' or 'has_not' — no value needed, set a display label
      chip.values = ['_']  // placeholder to indicate filter is active
      chip.valueLabel = chip.operator === 'has' ? '是' : '否'
    }
  } else {
    // Date field
    const val = dateInputValue.value.trim()
    if (!val) return
    if (chip.operator === 'between') {
      const val2 = dateInputValue2.value.trim()
      if (!val2) return
      chip.values = [val, val2]
      chip.valueLabel = `${val} ~ ${val2}`
    } else {
      chip.values = [val]
      chip.valueLabel = val
    }
  }

  showValuePopup.value = false
  dateInputValue.value = ''
  dateInputValue2.value = ''
  emitFilters()
}

function selectFirstValueSuggestion() {
  if (filteredValueOptions.value.length > 0) {
    toggleValue(filteredValueOptions.value[valueSuggestionIndex.value])
    if (!isMultiSelect.value) return
  }
}

function moveValueSuggestion(delta: number) {
  const len = filteredValueOptions.value.length
  if (len === 0) return
  valueSuggestionIndex.value = (valueSuggestionIndex.value + delta + len) % len
}

function updateChipValueLabel(chip: FilterChip) {
  if (chip.values.length === 0) {
    chip.valueLabel = ''
    return
  }
  const labels = chip.values.map(id => {
    // Special cases for brief chip display
    if (id === 'me') return '我'
    if (id === 'none') return '未分配'
    const opt = valueOptions.value.find(o => o.id === id)
    return opt?.label || id
  })
  if (labels.length <= 2) {
    chip.valueLabel = labels.join(', ')
  } else {
    chip.valueLabel = `${labels[0]}, ${labels[1]} 等${labels.length}项`
  }
}

// ==================== Filter Mode - Remove ====================

function removeFilter(index: number) {
  activeFilters.value.splice(index, 1)
  emitFilters()
}

// ==================== Relative Date Helpers ====================

/** Relative time shortcut operators that don't need user input */
const RELATIVE_DATE_SHORTCUTS = new Set(['today', 'yesterday', 'this_week', 'last_week', 'last_7_days', 'this_month', 'last_30_days'])

/**
 * Compute [after, before] date range for relative shortcuts.
 * Returns null if operator is not a relative shortcut.
 */
function computeRelativeDateRange(operator: string): [string, string] | null {
  const today = new Date()
  const fmt = (d: Date) => d.toISOString().split('T')[0]

  switch (operator) {
    case 'today':
      return [fmt(today), fmt(today)]
    case 'yesterday': {
      const d = new Date(today)
      d.setDate(d.getDate() - 1)
      return [fmt(d), fmt(d)]
    }
    case 'this_week': {
      // Monday of this week
      const day = today.getDay() || 7 // Sunday = 7
      const monday = new Date(today)
      monday.setDate(today.getDate() - day + 1)
      const sunday = new Date(monday)
      sunday.setDate(monday.getDate() + 6)
      return [fmt(monday), fmt(sunday)]
    }
    case 'last_week': {
      const day = today.getDay() || 7
      const thisMonday = new Date(today)
      thisMonday.setDate(today.getDate() - day + 1)
      const lastMonday = new Date(thisMonday)
      lastMonday.setDate(thisMonday.getDate() - 7)
      const lastSunday = new Date(lastMonday)
      lastSunday.setDate(lastMonday.getDate() + 6)
      return [fmt(lastMonday), fmt(lastSunday)]
    }
    case 'last_7_days': {
      const start = new Date(today)
      start.setDate(today.getDate() - 6)
      return [fmt(start), fmt(today)]
    }
    case 'this_month': {
      const start = new Date(today.getFullYear(), today.getMonth(), 1)
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 0)
      return [fmt(start), fmt(end)]
    }
    case 'last_30_days': {
      const start = new Date(today)
      start.setDate(today.getDate() - 29)
      return [fmt(start), fmt(today)]
    }
    default:
      return null
  }
}

// ==================== Emit Filters ====================

function emitFilters() {
  const filters: Record<string, any> = {}

  for (const chip of activeFilters.value) {
    if (chip.values.length === 0) continue

    const isNegative = chip.operator === 'is_not' || chip.operator === 'none_of'
    const fieldKey = chip.fieldKey

    // Map to backend params
    switch (fieldKey) {
      case 'status':
        if (!isNegative) filters.statusId = chip.values.join(',')
        else filters.statusIdNot = chip.values.join(',')
        break
      case 'priority':
        if (!isNegative) filters.priority = chip.values.join(',')
        else filters.priorityNot = chip.values.join(',')
        break
      case 'issueType':
        if (!isNegative) filters.issueType = chip.values.join(',')
        else filters.issueTypeNot = chip.values.join(',')
        break
      case 'project':
        if (!isNegative) filters.projectId = chip.values[0]
        break
      case 'assignee':
        if (!isNegative) {
          if (chip.values.includes('me')) filters.assignedToMe = 'true'
          else if (chip.values.includes('none')) filters.assigneeId = 'none'
          else filters.assigneeId = chip.values.join(',')
        }
        else filters.assigneeIdNot = chip.values.join(',')
        break
      case 'sprint':
        if (!isNegative) filters.sprintId = chip.values.join(',')
        else filters.sprintIdNot = chip.values.join(',')
        break
      case 'reporter':
        if (chip.values.includes('me')) filters.reportedByMe = 'true'
        else if (!isNegative) filters.reporterId = chip.values.join(',')
        break
      case 'tag':
        if (!isNegative) filters.tagId = chip.values.join(',')
        break
      case 'parent':
        if (chip.operator === 'is' && chip.values[0]) {
          filters.parentId = chip.values[0]
        } else if (chip.operator === 'has') {
          filters.hasParent = 'true'
        } else if (chip.operator === 'has_not') {
          filters.hasParent = 'false'
        }
        break
      case 'dueDate':
        // Shortcut operators
        if (chip.operator === 'overdue') {
          filters.overdue = 'true'
        } else if (chip.operator === 'today') {
          // Today: dueAfter = today, dueBefore = today
          const today = new Date().toISOString().split('T')[0]
          filters.dueAfter = today
          filters.dueBefore = today
        } else if (chip.operator === 'this_week') {
          // This week: dueAfter = today, dueBefore = today + 7 days
          const today = new Date()
          const nextWeek = new Date(today.getTime() + 7 * 24 * 60 * 60 * 1000)
          filters.dueAfter = today.toISOString().split('T')[0]
          filters.dueBefore = nextWeek.toISOString().split('T')[0]
        } else if (chip.operator === 'after' && chip.values[0]) {
          filters.dueAfter = chip.values[0]
        } else if (chip.operator === 'before' && chip.values[0]) {
          filters.dueBefore = chip.values[0]
        } else if (chip.operator === 'between' && chip.values[0] && chip.values[1]) {
          filters.dueAfter = chip.values[0]
          filters.dueBefore = chip.values[1]
        }
        break
      case 'createdAt': {
        const range = computeRelativeDateRange(chip.operator)
        if (range) {
          filters.createdAfter = range[0]
          filters.createdBefore = range[1]
        } else if (chip.operator === 'after' && chip.values[0]) {
          filters.createdAfter = chip.values[0]
        } else if (chip.operator === 'before' && chip.values[0]) {
          filters.createdBefore = chip.values[0]
        } else if (chip.operator === 'between' && chip.values[0] && chip.values[1]) {
          filters.createdAfter = chip.values[0]
          filters.createdBefore = chip.values[1]
        }
        break
      }
      case 'updatedAt': {
        const range = computeRelativeDateRange(chip.operator)
        if (range) {
          filters.updatedAfter = range[0]
          filters.updatedBefore = range[1]
        } else if (chip.operator === 'after' && chip.values[0]) {
          filters.updatedAfter = chip.values[0]
        } else if (chip.operator === 'before' && chip.values[0]) {
          filters.updatedBefore = chip.values[0]
        } else if (chip.operator === 'between' && chip.values[0] && chip.values[1]) {
          filters.updatedAfter = chip.values[0]
          filters.updatedBefore = chip.values[1]
        }
        break
      }
    }
  }

  emit('filter', filters)
}

// ==================== Popup management ====================

function closeAllPopups() {
  showOperatorPopup.value = false
  showValuePopup.value = false
  editingChip.value = null
}

// Click outside field input and popups
function handleClickOutside(e: MouseEvent) {
  const target = e.target as Node
  if (showFieldInput.value && fieldInputWrapperRef.value && !fieldInputWrapperRef.value.contains(target)) {
    cancelFieldInput()
  }
  // Close popups when clicking outside them
  if (showOperatorPopup.value && operatorPopupRef.value && !operatorPopupRef.value.contains(target)) {
    showOperatorPopup.value = false
  }
  if (showValuePopup.value && valuePopupRef.value && !valuePopupRef.value.contains(target)) {
    showValuePopup.value = false
    // If multi-select was in progress, apply the temp values
    if (isMultiSelect.value && editingChip.value) {
      const chip = activeFilters.value[editingChip.value.index]
      if (chip && tempSelectedValues.value.length > 0) {
        chip.values = [...tempSelectedValues.value]
        updateChipValueLabel(chip)
        emitFilters()
      }
    }
  }
}

onMounted(() => document.addEventListener('mousedown', handleClickOutside))
onUnmounted(() => document.removeEventListener('mousedown', handleClickOutside))

// Initialize with external filters (from dashboard cards, etc.)
watch(() => props.initialFilters, (filters) => {
  if (filters && filters.length > 0) {
    applyInitialFilters(filters)
  }
}, { immediate: true })

// Reset state when the active query changes
watch(() => props.activeQueryName, () => {
  // No state to reset — tooltip is declarative
})

/** 标记：正在应用外部过滤条件，阻止 mode watch 触发 emitFilters（避免冗余请求） */
let suppressEmit = false

function applyInitialFilters(filters: InitialFilter[]) {
  suppressEmit = true
  mode.value = 'filter'
  activeFilters.value = filters.map(f => {
    const field = FILTER_FIELDS.find(ff => ff.key === f.fieldKey)
    const op = field?.operators.find(o => o.key === f.operator)
    return {
      fieldKey: f.fieldKey,
      fieldLabel: field?.label || f.fieldKey,
      operator: f.operator,
      operatorLabel: op?.label || f.operator,
      values: f.values,
      valueLabel: f.valueLabels?.join(', ') || f.values.join(', ')
    }
  })
  // nextTick 后恢复 emit 能力，确保 mode watch 已执行完毕
  nextTick(() => { suppressEmit = false })
}

/**
 * 清空所有搜索/筛选状态（供外部组件调用）
 */
/**
 * 清空所有搜索/筛选状态（供外部组件调用）
 */
function clearAll() {
  searchKeyword.value = ''
  activeFilters.value = []
  // Reset to search mode
  suppressEmit = true
  mode.value = 'search'
  nextTick(() => { suppressEmit = false })
}

/**
 * 程序化设置过滤条件（供 Saved Query 回填使用）
 * 切换到 filter 模式并展示 chips，不触发 @filter 事件
 */
function setFilters(filters: InitialFilter[]) {
  if (filters.length > 0) {
    applyInitialFilters(filters)
  } else {
    // No filters — clear and stay in search mode
    activeFilters.value = []
    suppressEmit = true
    mode.value = 'search'
    nextTick(() => { suppressEmit = false })
  }
}

/**
 * 程序化设置搜索关键字（供 URL 参数初始化使用）
 * 设置后会触发 @search 事件
 */
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
  border-bottom: 1px solid var(--tf-border);
  flex-shrink: 0;
  min-height: 44px;
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
  flex-wrap: wrap;
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

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: var(--tf-text-primary);
}

.search-input::placeholder {
  color: var(--tf-text-tertiary);
}

.clear-btn {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: color 0.15s, background 0.15s;
}

.clear-btn:hover {
  color: var(--tf-text-primary);
  background: var(--tf-bg-hover);
}

/* Filter mode */
.filter-mode {
  flex: 1;
  min-width: 0;
}

.filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

/* Filter chip */
.filter-chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-md);
  font-size: 12px;
  overflow: hidden;
  transition: border-color 0.15s;
}

.filter-chip:hover {
  border-color: var(--tf-accent);
}

.chip-field {
  padding: 0 8px;
  color: var(--tf-text-secondary);
  font-weight: 500;
  cursor: pointer;
  height: 100%;
  display: flex;
  align-items: center;
  transition: background 0.15s;
}

.chip-field:hover {
  background: var(--tf-bg-hover);
}

.chip-operator {
  padding: 0 6px;
  color: var(--tf-accent);
  cursor: pointer;
  height: 100%;
  display: flex;
  align-items: center;
  border-left: 1px solid var(--tf-border-light);
  border-right: 1px solid var(--tf-border-light);
  font-size: 11px;
  transition: background 0.15s;
}

.chip-operator:hover {
  background: var(--tf-accent-bg);
}

.chip-value {
  padding: 0 8px;
  color: var(--tf-text-primary);
  cursor: pointer;
  height: 100%;
  display: flex;
  align-items: center;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background 0.15s;
}

.chip-value:hover {
  background: var(--tf-bg-hover);
}

.chip-remove {
  padding: 0 6px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  height: 100%;
  display: flex;
  align-items: center;
  font-size: 11px;
  border-left: 1px solid var(--tf-border-light);
  transition: background 0.15s, color 0.15s;
}

.chip-remove:hover {
  background: var(--tf-danger-bg);
  color: var(--tf-danger);
}

/* Add filter */
.add-filter-area {
  position: relative;
}

.add-filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border: 1px dashed var(--tf-border);
  border-radius: var(--tf-radius-md);
  background: transparent;
  color: var(--tf-text-tertiary);
  font-size: 12px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.add-filter-btn:hover {
  border-color: var(--tf-accent);
  color: var(--tf-accent);
  background: var(--tf-accent-bg);
}

.field-input-wrapper {
  position: relative;
}

.field-input {
  width: 160px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--tf-accent);
  border-radius: var(--tf-radius-md);
  background: var(--tf-bg-elevated);
  color: var(--tf-text-primary);
  font-size: 12px;
  outline: none;
}

.field-input::placeholder {
  color: var(--tf-text-tertiary);
}

/* Suggestions dropdown */
.suggestions-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  min-width: 180px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-lg);
  box-shadow: var(--tf-shadow-xl);
  padding: 4px;
  z-index: 300;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--tf-text-primary);
  transition: background 0.1s;
}

.suggestion-item:hover,
.suggestion-item.active {
  background: var(--tf-bg-hover);
}

.suggestion-icon {
  font-size: 13px;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}

.suggestion-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Popup overlay */
.popup-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 500;
  pointer-events: none;
}

.popup-panel {
  position: fixed;
  min-width: 160px;
  max-width: 280px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-lg);
  box-shadow: var(--tf-shadow-lg);
  padding: 4px;
  max-height: 320px;
  overflow-y: auto;
  pointer-events: auto;
}

.value-popup {
  min-width: 220px;
  max-width: 320px;
}

.popup-title {
  padding: 6px 10px;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.popup-search {
  padding: 4px;
  margin-bottom: 4px;
}

.popup-search-input {
  width: 100%;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--tf-border);
  border-radius: 4px;
  background: var(--tf-bg-surface);
  color: var(--tf-text-primary);
  font-size: 12px;
  outline: none;
}

.popup-search-input:focus {
  border-color: var(--tf-accent);
}

.popup-search-input::placeholder {
  color: var(--tf-text-tertiary);
}

.popup-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--tf-text-primary);
  transition: background 0.1s;
}

.popup-item:hover,
.popup-item.active {
  background: var(--tf-bg-hover);
}

.popup-item.selected {
  color: var(--tf-accent);
}

.popup-item.special {
  color: var(--tf-accent);
  border-bottom: 1px solid var(--tf-border-light);
  margin-bottom: 4px;
  padding-bottom: 8px;
}

.special-icon {
  font-size: 12px;
  flex-shrink: 0;
}

.popup-options {
  max-height: 220px;
  overflow-y: auto;
}

.popup-loading {
  display: flex;
  justify-content: center;
  padding: 16px;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--tf-border);
  border-top-color: var(--tf-accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.popup-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--tf-text-tertiary);
}

.check-icon {
  font-size: 13px;
  width: 16px;
  text-align: center;
  flex-shrink: 0;
}

.value-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.value-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popup-footer {
  padding: 6px;
  border-top: 1px solid var(--tf-border-light);
  margin-top: 4px;
}

.popup-confirm-btn {
  width: 100%;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: var(--tf-accent);
  color: var(--tf-text-on-accent);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.popup-confirm-btn:hover {
  opacity: 0.9;
}

.text-input-popup {
  padding: 8px;
}

.text-input-label {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-bottom: 4px;
  padding: 0 2px;
}
</style>
