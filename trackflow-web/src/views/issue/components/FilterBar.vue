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
      <input
        ref="searchInputRef"
        v-model="searchKeyword"
        class="search-input"
        placeholder="输入搜索请求"
        @keyup.enter="emitSearch"
        @input="onSearchInput"
      />
      <span v-if="searchKeyword" class="clear-btn" @click="clearSearch">✕</span>
    </div>

    <!-- Filter mode -->
    <div v-else class="filter-mode">
      <div class="filter-chips">
        <!-- Active filter chips -->
        <div
          v-for="(chip, index) in activeFilters"
          :key="index"
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
                  selected: isValueSelected(opt.id)
                }"
                @click="toggleValue(opt)"
                @mouseenter="valueSuggestionIndex = i"
              >
                <span v-if="isMultiSelect" class="check-icon">{{ isValueSelected(opt.id) ? '☑' : '☐' }}</span>
                <span v-if="opt.color" class="value-dot" :style="{ background: opt.color }"></span>
                <span class="value-label">{{ opt.label }}</span>
              </div>
              <div v-if="filteredValueOptions.length === 0" class="popup-empty">无匹配选项</div>
            </div>
            <div v-if="isMultiSelect && tempSelectedValues.length > 0" class="popup-footer">
              <button class="popup-confirm-btn" @click="confirmMultiSelect">确定</button>
            </div>
          </div>
        </div>
      </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { IconFilter, IconSearch, IconPlus } from '@arco-design/web-vue/es/icon'
import { issueApi, projectApi, sprintApi, userApi } from '@/api'
import type { IssueStatusVO, ProjectVO, SprintVO, UserVO } from '@/api/types'

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
}>()

const emit = defineEmits<{
  (e: 'search', keyword: string): void
  (e: 'filter', filters: Record<string, any>): void
  (e: 'mode-change', mode: 'search' | 'filter'): void
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

const FILTER_FIELDS: FilterField[] = [
  { key: 'project', label: '项目', icon: '📁', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'status', label: '状态', icon: '🔵', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'priority', label: '优先级', icon: '🔴', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'assignee', label: '负责人', icon: '👤', type: 'user', operators: OPERATORS_USER },
  { key: 'issueType', label: '类型', icon: '📋', type: 'enum', operators: OPERATORS_ENUM },
  { key: 'sprint', label: 'Sprint', icon: '🏃', type: 'enum', operators: OPERATORS_ENUM },
]

// ==================== State ====================

const mode = ref<'search' | 'filter'>('search')
const searchKeyword = ref('')
const searchInputRef = ref<HTMLInputElement | null>(null)
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

function onSearchInput() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(() => {
    emitSearch()
  }, 300)
}

function emitSearch() {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  emit('search', searchKeyword.value.trim())
}

function clearSearch() {
  searchKeyword.value = ''
  emit('search', '')
}

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
          label: s.name,
          color: s.color
        }))
        break

      case 'priority':
        valueOptions.value = [
          { id: 'Critical', label: 'Critical', color: '#f85149' },
          { id: 'High', label: 'High', color: '#d29922' },
          { id: 'Normal', label: 'Normal', color: '#58a6ff' },
          { id: 'Low', label: 'Low', color: '#6b7280' },
        ]
        break

      case 'issueType':
        valueOptions.value = [
          { id: 'Task', label: '任务' },
          { id: 'Bug', label: '缺陷' },
          { id: 'Feature', label: '需求' },
          { id: 'Epic', label: '史诗' },
        ]
        break

      case 'project':
        valueOptions.value = props.projectList.map(p => ({
          id: p.id,
          label: `${p.key} - ${p.name}`
        }))
        break

      case 'assignee': {
        // Load all users
        const res = await userApi.list({ pageSize: 100 })
        const users = res.data?.list || []
        valueOptions.value = users.map((u: UserVO) => ({
          id: u.id,
          label: u.displayName || u.username
        }))
        break
      }

      case 'sprint': {
        // Load sprints - if project is selected, load from that project
        const projectFilter = activeFilters.value.find(f => f.fieldKey === 'project')
        const pid = projectFilter?.values[0] || props.projectId
        if (pid) {
          const res = await sprintApi.listByProject(pid)
          const sprints = res.data || []
          valueOptions.value = sprints.map((s: SprintVO) => ({
            id: s.id,
            label: `${s.name}${s.status === 'Active' ? ' (进行中)' : s.status === 'Completed' ? ' (已完成)' : ''}`
          }))
        } else {
          // Load from all projects
          const allSprints: ValueOption[] = []
          for (const p of props.projectList.slice(0, 5)) {
            try {
              const res = await sprintApi.listByProject(p.id)
              const sprints = res.data || []
              sprints.forEach((s: SprintVO) => {
                allSprints.push({ id: s.id, label: `${p.key} / ${s.name}` })
              })
            } catch { /* ignore */ }
          }
          valueOptions.value = allSprints
        }
        break
      }
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
        if (!isNegative) filters.assigneeId = chip.values.join(',')
        else filters.assigneeIdNot = chip.values.join(',')
        break
      case 'sprint':
        if (!isNegative) filters.sprintId = chip.values.join(',')
        else filters.sprintIdNot = chip.values.join(',')
        break
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

function applyInitialFilters(filters: InitialFilter[]) {
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
}
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
  background: rgba(248, 81, 73, 0.1);
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
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
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
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
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
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.popup-confirm-btn:hover {
  opacity: 0.9;
}
</style>
