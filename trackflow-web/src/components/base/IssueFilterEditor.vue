<template>
  <!-- ===== 筛选 chip 列表：每个 chip 代表一条筛选条件（字段 + 操作符 + 值） ===== -->
  <div class="issue-filter-editor" :class="{ readonly }">
    <div class="filter-chips">
      <div
        v-for="(chip, index) in chips"
        :key="chip.field + '-' + index"
        class="filter-chip"
      >
        <span class="chip-field" @click="!readonly && openFieldSelector(index)">{{ chip.fieldLabel }}</span>
        <span class="chip-operator" @click="!readonly && openOperatorSelector(index)">{{ chip.operatorLabel }}</span>
        <span
          v-if="chip.valueLabel"
          class="chip-value"
          @click="!readonly && openValueSelector(index)"
        >{{ chip.valueLabel }}</span>
        <span v-if="!readonly" class="chip-remove" @click="removeChip(index)">✕</span>
      </div>

      <!-- ===== 添加筛选按钮 / 内联字段搜索输入 ===== -->
      <div v-if="!readonly" class="add-filter-area">
        <div v-if="showFieldInput" class="field-input-wrapper" ref="fieldInputWrapperRef">
          <input
            ref="fieldInputRef"
            v-model="fieldSearchText"
            class="field-input"
            placeholder="输入属性名..."
            @keydown.escape="cancelFieldInput"
            @keydown.enter="selectHighlightedField"
            @keydown.down.prevent="moveFieldHighlight(1)"
            @keydown.up.prevent="moveFieldHighlight(-1)"
          />
        </div>
        <button v-else class="add-filter-btn" @click="startAddFilter">
          <icon-plus :size="12" />
          <span>添加筛选</span>
        </button>
      </div>
    </div>

    <!-- ===== 字段建议下拉 ===== -->
    <Teleport to="body">
      <div
        v-if="showFieldInput && filteredFields.length > 0"
        class="ife-dropdown ife-field-dropdown"
        :style="dropdownPosition"
      >
        <div
          v-for="(field, i) in filteredFields"
          :key="field.key"
          class="ife-dropdown-item"
          :class="{ active: fieldHighlightIndex === i }"
          @click="selectField(field)"
          @mouseenter="fieldHighlightIndex = i"
        >
          {{ field.label }}
        </div>
      </div>
    </Teleport>

    <!-- ===== 操作符选择下拉 ===== -->
    <Teleport to="body">
      <div v-if="showOperatorDropdown" class="ife-overlay" @mousedown.self="closeDropdowns">
        <div class="ife-dropdown ife-operator-dropdown" :style="dropdownPosition">
          <div class="ife-dropdown-title">选择操作符</div>
          <div
            v-for="op in currentOperators"
            :key="op.key"
            class="ife-dropdown-item"
            :class="{ selected: editingIndex !== null && chips[editingIndex]?.operator === op.key }"
            @click="selectOperator(op)"
          >
            {{ op.label }}
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ===== 值选择下拉 ===== -->
    <Teleport to="body">
      <div v-if="showValueDropdown" class="ife-overlay" @mousedown.self="closeDropdowns">
        <div class="ife-dropdown ife-value-dropdown" :style="dropdownPosition">
          <!-- 日期/文本输入模式 -->
          <template v-if="valueInputMode === 'date' || valueInputMode === 'text'">
            <div class="ife-text-input-area">
              <div class="ife-input-label">
                {{ valueInputMode === 'text' ? '输入值' : (currentOperatorKey === 'between' ? '起始日期 (yyyy-MM-dd)' : '日期 (yyyy-MM-dd)') }}
              </div>
              <input
                ref="valueInputRef"
                v-model="textInputValue1"
                class="ife-text-input"
                :placeholder="valueInputMode === 'text' ? '输入...' : 'yyyy-MM-dd'"
                @keydown.escape="closeDropdowns"
                @keydown.enter="confirmTextInput"
              />
              <template v-if="currentOperatorKey === 'between'">
                <div class="ife-input-label" style="margin-top: 8px">结束日期 (yyyy-MM-dd)</div>
                <input
                  v-model="textInputValue2"
                  class="ife-text-input"
                  placeholder="yyyy-MM-dd"
                  @keydown.escape="closeDropdowns"
                  @keydown.enter="confirmTextInput"
                />
              </template>
              <div class="ife-dropdown-footer">
                <button class="ife-confirm-btn" @click="confirmTextInput">确定</button>
              </div>
            </div>
          </template>

          <!-- 枚举/用户多选下拉模式 -->
          <template v-else>
            <div class="ife-search-area">
              <input
                ref="valueInputRef"
                v-model="valueSearchText"
                class="ife-text-input"
                placeholder="搜索..."
                @keydown.escape="closeDropdowns"
                @keydown.enter="selectHighlightedValue"
                @keydown.down.prevent="moveValueHighlight(1)"
                @keydown.up.prevent="moveValueHighlight(-1)"
              />
            </div>
            <div v-if="valueOptionsLoading" class="ife-loading">加载中...</div>
            <div v-else class="ife-options-list">
              <div
                v-for="(opt, i) in filteredValueOptions"
                :key="opt.id"
                class="ife-dropdown-item"
                :class="{ active: valueHighlightIndex === i, selected: tempSelectedIds.has(opt.id), special: opt.isSpecial }"
                @click="toggleValueOption(opt)"
                @mouseenter="valueHighlightIndex = i"
              >
                <span v-if="isMultiSelectMode" class="ife-check">{{ tempSelectedIds.has(opt.id) ? '☑' : '☐' }}</span>
                <span v-if="opt.color" class="ife-color-dot" :style="{ background: opt.color }"></span>
                <span class="ife-option-label">{{ opt.label }}</span>
              </div>
              <div v-if="filteredValueOptions.length === 0" class="ife-empty">无匹配选项</div>
            </div>
            <div v-if="isMultiSelectMode && tempSelectedIds.size > 0" class="ife-dropdown-footer">
              <button class="ife-confirm-btn" @click="confirmMultiSelect">确定 ({{ tempSelectedIds.size }})</button>
            </div>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * IssueFilterEditor — 统一工单筛选器组件
 *
 * 职责：
 * - 以 chip 形式展示/编辑筛选条件
 * - 字段列表完全动态（系统固定字段 + 项目自定义字段通过 API 加载）
 * - 输出格式与 SavedQuery filters（SavedQueryFilter[]）完全兼容
 *
 * 对外接口：
 * - Props: projectId（决定可用字段范围），modelValue（双向绑定筛选条件），statusList/projectList（避免重复请求）
 * - Emits: update:modelValue（条件变化时触发）
 */
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { IconPlus } from '@arco-design/web-vue/es/icon'
import { customFieldApi, projectApi, sprintApi, tagApi } from '@/api'
import type { CustomFieldDefinitionVO, CustomFieldOptionVO, IssueStatusVO, ProjectVO, SprintVO } from '@/api/types'

// ===== 类型定义 =====

/** 筛选条件（与 SavedQueryFilter 兼容） */
export interface FilterCondition {
  field: string
  operator: string
  value?: string[]
}

/** 操作符定义 */
interface OperatorDef {
  key: string
  label: string
  /** 是否支持多选 */
  multi?: boolean
  /** 选完操作符即完成（无需选值） */
  noValue?: boolean
}

/** 字段定义 */
interface FieldDef {
  key: string
  label: string
  /** 字段值类型（决定操作符组和值输入方式） */
  valueType: 'enum' | 'user' | 'date' | 'number' | 'text' | 'bool' | 'state' | 'parent'
  operators: OperatorDef[]
  /** 是否为系统固定字段 */
  isSystem: boolean
}

/** 值选项 */
interface ValueOption {
  id: string
  label: string
  color?: string
  isSpecial?: boolean
}

/** 内部 chip 展示结构 */
interface ChipData {
  field: string
  fieldLabel: string
  operator: string
  operatorLabel: string
  values: string[]
  valueLabel: string
}

// ===== Props & Emits =====

const props = withDefaults(defineProps<{
  projectId?: string | null
  modelValue: FilterCondition[]
  statusList?: IssueStatusVO[]
  projectList?: ProjectVO[]
  readonly?: boolean
}>(), {
  projectId: null,
  statusList: () => [],
  projectList: () => [],
  readonly: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: FilterCondition[]): void
}>()

// ===== 操作符组定义 =====

const OPERATORS_ENUM: OperatorDef[] = [
  { key: 'in', label: '属于', multi: true },
  { key: 'not_in', label: '不属于', multi: true },
  { key: 'is_empty', label: '为空', noValue: true },
  { key: 'is_not_empty', label: '不为空', noValue: true },
]

const OPERATORS_STATE: OperatorDef[] = [
  { key: 'in', label: '属于', multi: true },
  { key: 'not_in', label: '不属于', multi: true },
  { key: 'open', label: '所有未关闭', noValue: true },
  { key: 'closed', label: '所有已关闭', noValue: true },
  { key: 'is_empty', label: '为空', noValue: true },
  { key: 'is_not_empty', label: '不为空', noValue: true },
]

const OPERATORS_USER: OperatorDef[] = [
  { key: 'in', label: '属于', multi: true },
  { key: 'not_in', label: '不属于', multi: true },
  { key: 'is_empty', label: '未分配', noValue: true },
  { key: 'is_not_empty', label: '已分配', noValue: true },
]

const OPERATORS_DATE: OperatorDef[] = [
  { key: 'relative', label: '相对时间' },
  { key: 'gte', label: '晚于' },
  { key: 'lte', label: '早于' },
  { key: 'between', label: '在范围内' },
  { key: 'is_empty', label: '未设置', noValue: true },
  { key: 'is_not_empty', label: '已设置', noValue: true },
]

const OPERATORS_DATE_DUE: OperatorDef[] = [
  { key: 'relative', label: '相对时间' },
  { key: 'gte', label: '晚于' },
  { key: 'lte', label: '早于' },
  { key: 'between', label: '在范围内' },
  { key: 'is_empty', label: '未设置', noValue: true },
  { key: 'is_not_empty', label: '已设置', noValue: true },
]

const OPERATORS_NUMBER: OperatorDef[] = [
  { key: 'eq', label: '等于' },
  { key: 'neq', label: '不等于' },
  { key: 'gt', label: '大于' },
  { key: 'gte', label: '大于等于' },
  { key: 'lt', label: '小于' },
  { key: 'lte', label: '小于等于' },
  { key: 'between', label: '在范围内' },
  { key: 'is_empty', label: '为空', noValue: true },
  { key: 'is_not_empty', label: '不为空', noValue: true },
]

const OPERATORS_TEXT: OperatorDef[] = [
  { key: 'contains', label: '包含' },
  { key: 'is_empty', label: '为空', noValue: true },
  { key: 'is_not_empty', label: '不为空', noValue: true },
]

const OPERATORS_BOOL: OperatorDef[] = [
  { key: 'eq', label: '等于' },
  { key: 'is_empty', label: '为空', noValue: true },
]

const OPERATORS_PARENT: OperatorDef[] = [
  { key: 'eq', label: '是' },
  { key: 'is_not_empty', label: '有父工单', noValue: true },
  { key: 'is_empty', label: '无父工单', noValue: true },
]

// ===== 日期相对时间预设 =====

const DATE_RELATIVE_OPTIONS: ValueOption[] = [
  { id: 'today', label: '今天' },
  { id: 'yesterday', label: '昨天' },
  { id: 'this_week', label: '本周' },
  { id: 'last_week', label: '上周' },
  { id: 'last_7_days', label: '最近 7 天' },
  { id: 'this_month', label: '本月' },
  { id: 'last_30_days', label: '最近 30 天' },
  { id: 'overdue', label: '已逾期' },
]

// ===== 系统固定字段定义 =====

const SYSTEM_FIELDS: FieldDef[] = [
  { key: 'project', label: '项目', valueType: 'enum', operators: OPERATORS_ENUM, isSystem: true },
  { key: 'assignee', label: '负责人', valueType: 'user', operators: OPERATORS_USER, isSystem: true },
  { key: 'reporter', label: '报告人', valueType: 'user', operators: OPERATORS_USER, isSystem: true },
  { key: 'sprint', label: 'Sprint', valueType: 'enum', operators: OPERATORS_ENUM, isSystem: true },
  { key: 'tag', label: '标签', valueType: 'enum', operators: OPERATORS_ENUM, isSystem: true },
  { key: 'parent', label: '父工单', valueType: 'parent', operators: OPERATORS_PARENT, isSystem: true },
  { key: 'createdAt', label: '创建时间', valueType: 'date', operators: OPERATORS_DATE, isSystem: true },
  { key: 'updatedAt', label: '更新时间', valueType: 'date', operators: OPERATORS_DATE, isSystem: true },
  { key: 'resolvedAt', label: '解决时间', valueType: 'date', operators: OPERATORS_DATE, isSystem: true },
  { key: 'commenter', label: '评论人', valueType: 'user', operators: OPERATORS_USER, isSystem: true },
  { key: 'keyword', label: '关键词', valueType: 'text', operators: OPERATORS_TEXT, isSystem: true },
]

// ===== 状态 =====

const chips = ref<ChipData[]>([])
const projectFields = ref<FieldDef[]>([])
const projectFieldsLoaded = ref(false)

// 字段输入
const showFieldInput = ref(false)
const fieldSearchText = ref('')
const fieldInputRef = ref<HTMLInputElement | null>(null)
const fieldInputWrapperRef = ref<HTMLElement | null>(null)
const fieldHighlightIndex = ref(0)

// 操作符/值下拉
const showOperatorDropdown = ref(false)
const showValueDropdown = ref(false)
const editingIndex = ref<number | null>(null)
const dropdownPosition = ref<{ top: string; left: string; minWidth?: string }>({ top: '0', left: '0' })

// 值选择
const valueSearchText = ref('')
const valueInputRef = ref<HTMLInputElement | null>(null)
const valueHighlightIndex = ref(0)
const valueOptionsLoading = ref(false)
const valueOptions = ref<ValueOption[]>([])
const tempSelectedIds = ref<Set<string>>(new Set())
const textInputValue1 = ref('')
const textInputValue2 = ref('')

// ===== Computed =====

/** 所有可用字段（系统固定字段 + 项目自定义字段） */
const allFields = computed<FieldDef[]>(() => {
  return [...SYSTEM_FIELDS, ...projectFields.value]
})

/** 未使用的可筛选字段（已使用字段排除） */
const availableFields = computed<FieldDef[]>(() => {
  const usedKeys = new Set(chips.value.map(c => c.field))
  return allFields.value.filter(f => !usedKeys.has(f.key))
})

/** 字段搜索过滤结果 */
const filteredFields = computed<FieldDef[]>(() => {
  if (!fieldSearchText.value.trim()) return availableFields.value
  const kw = fieldSearchText.value.toLowerCase()
  return availableFields.value.filter(f => f.label.toLowerCase().includes(kw) || f.key.toLowerCase().includes(kw))
})

/** 当前编辑 chip 对应的操作符列表 */
const currentOperators = computed<OperatorDef[]>(() => {
  if (editingIndex.value === null) return []
  const chip = chips.value[editingIndex.value]
  if (!chip) return []
  const field = allFields.value.find(f => f.key === chip.field)
  return field?.operators || []
})

/** 当前操作符 key */
const currentOperatorKey = computed(() => {
  if (editingIndex.value === null) return ''
  return chips.value[editingIndex.value]?.operator || ''
})

/** 当前编辑字段的值输入模式 */
const valueInputMode = computed<'enum' | 'date' | 'number' | 'text' | null>(() => {
  if (editingIndex.value === null) return null
  const chip = chips.value[editingIndex.value]
  if (!chip) return null
  const field = allFields.value.find(f => f.key === chip.field)
  if (!field) return null
  if (field.valueType === 'date') {
    // 'relative' 操作符使用枚举模式显示预设选项
    if (chip.operator === 'relative') return 'enum'
    return 'date'
  }
  if (field.valueType === 'number') return 'number'
  if (field.valueType === 'text' || field.valueType === 'parent') return 'text'
  if (field.valueType === 'bool') return 'enum'
  return 'enum'
})

/** 是否为多选模式 */
const isMultiSelectMode = computed(() => {
  if (editingIndex.value === null) return false
  const chip = chips.value[editingIndex.value]
  if (!chip) return false
  const field = allFields.value.find(f => f.key === chip.field)
  const op = field?.operators.find(o => o.key === chip.operator)
  return op?.multi ?? false
})

/** 过滤后的值选项 */
const filteredValueOptions = computed<ValueOption[]>(() => {
  if (!valueSearchText.value.trim()) return valueOptions.value
  const kw = valueSearchText.value.toLowerCase()
  return valueOptions.value.filter(o => o.label.toLowerCase().includes(kw))
})

// ===== 初始化：加载项目字段 =====

watch(() => props.projectId, (newId) => {
  if (newId) loadProjectFields(newId)
  else { projectFields.value = []; projectFieldsLoaded.value = true }
}, { immediate: true })

async function loadProjectFields(projectId: string) {
  try {
    const res = await customFieldApi.listByProject(projectId)
    const fields = res.data || []
    projectFields.value = fields.map(cfToFieldDef)
    projectFieldsLoaded.value = true
  } catch {
    projectFields.value = []
    projectFieldsLoaded.value = true
  }
}

/** 将 CustomFieldDefinitionVO 转换为 FieldDef */
function cfToFieldDef(cf: CustomFieldDefinitionVO): FieldDef {
  const key = `cf.${cf.id}`
  let valueType: FieldDef['valueType'] = 'text'
  let operators: OperatorDef[] = OPERATORS_TEXT

  switch (cf.fieldFormat) {
    case 'list':
    case 'version':
    case 'build':
    case 'ownedField':
      valueType = 'enum'
      operators = OPERATORS_ENUM
      break
    case 'state':
      valueType = 'state'
      operators = OPERATORS_STATE
      break
    case 'user':
    case 'group':
      valueType = 'user'
      operators = OPERATORS_USER
      break
    case 'date':
    case 'datetime':
      valueType = 'date'
      operators = OPERATORS_DATE_DUE
      break
    case 'int':
    case 'float':
      valueType = 'number'
      operators = OPERATORS_NUMBER
      break
    case 'bool':
      valueType = 'bool'
      operators = OPERATORS_BOOL
      break
    case 'string':
    case 'text':
      valueType = 'text'
      operators = OPERATORS_TEXT
      break
  }

  return { key, label: cf.name, valueType, operators, isSystem: false }
}

// ===== 同步 modelValue → chips =====

watch(() => props.modelValue, (newVal) => {
  // 只在外部值真正变化时重建（避免循环）
  if (!arraysEqual(newVal, chipsToConditions())) {
    rebuildChips(newVal)
  }
}, { immediate: true, deep: true })

function rebuildChips(conditions: FilterCondition[]) {
  chips.value = conditions.map(cond => {
    const field = allFields.value.find(f => f.key === cond.field)
    const op = field?.operators.find(o => o.key === cond.operator)
    return {
      field: cond.field,
      fieldLabel: field?.label || cond.field,
      operator: cond.operator,
      operatorLabel: op?.label || cond.operator,
      values: cond.value || [],
      valueLabel: buildValueLabel(cond.field, cond.operator, cond.value || [])
    }
  })
}

function buildValueLabel(_fieldKey: string, operator: string, values: string[]): string {
  if (values.length === 0) return ''
  // 日期相对时间
  if (operator === 'relative') {
    const labels = values.map(v => DATE_RELATIVE_OPTIONS.find(o => o.id === v)?.label || v)
    return labels.join(', ')
  }
  // ${currentUser} / ${currentSprint}
  const display = values.map(v => {
    if (v === '${currentUser}') return '我'
    if (v === '${currentSprint}') return '当前 Sprint'
    return v
  })
  if (display.length <= 2) return display.join(', ')
  return `${display[0]}, ${display[1]} 等${display.length}项`
}

function chipsToConditions(): FilterCondition[] {
  return chips.value
    .filter(c => c.values.length > 0 || isNoValueOperator(c.operator))
    .map(c => {
      const cond: FilterCondition = { field: c.field, operator: c.operator }
      if (c.values.length > 0 && !isNoValueOperator(c.operator)) {
        cond.value = [...c.values]
      }
      return cond
    })
}

function isNoValueOperator(op: string): boolean {
  return ['is_empty', 'is_not_empty', 'open', 'closed'].includes(op)
}

function arraysEqual(a: FilterCondition[], b: FilterCondition[]): boolean {
  if (a.length !== b.length) return false
  return JSON.stringify(a) === JSON.stringify(b)
}

// ===== 字段输入 =====

function startAddFilter() {
  showFieldInput.value = true
  fieldSearchText.value = ''
  fieldHighlightIndex.value = 0
  nextTick(() => {
    fieldInputRef.value?.focus()
    updateDropdownPosition(fieldInputWrapperRef.value)
  })
}

function cancelFieldInput() {
  showFieldInput.value = false
  fieldSearchText.value = ''
}

function moveFieldHighlight(delta: number) {
  const len = filteredFields.value.length
  if (len === 0) return
  fieldHighlightIndex.value = (fieldHighlightIndex.value + delta + len) % len
}

function selectHighlightedField() {
  if (filteredFields.value.length > 0) {
    selectField(filteredFields.value[fieldHighlightIndex.value])
  }
}

function selectField(field: FieldDef) {
  const defaultOp = field.operators[0]
  const newChip: ChipData = {
    field: field.key,
    fieldLabel: field.label,
    operator: defaultOp.key,
    operatorLabel: defaultOp.label,
    values: [],
    valueLabel: ''
  }

  // 如果默认操作符不需要值，直接完成
  if (defaultOp.noValue) {
    chips.value.push(newChip)
    showFieldInput.value = false
    fieldSearchText.value = ''
    emitChange()
    return
  }

  chips.value.push(newChip)
  showFieldInput.value = false
  fieldSearchText.value = ''

  // 自动打开值选择
  nextTick(() => openValueSelector(chips.value.length - 1))
}

// ===== 操作符选择 =====

function openFieldSelector(index: number) {
  // 点击字段名时打开操作符选择（字段选定后不可更换，只能删除重建）
  openOperatorSelector(index)
}

function openOperatorSelector(index: number) {
  editingIndex.value = index
  positionDropdownAtChip(index)
  showOperatorDropdown.value = true
  showValueDropdown.value = false
}

function selectOperator(op: OperatorDef) {
  if (editingIndex.value === null) return
  const chip = chips.value[editingIndex.value]
  if (!chip) return

  chip.operator = op.key
  chip.operatorLabel = op.label

  // 无需值的操作符，直接完成
  if (op.noValue) {
    chip.values = []
    chip.valueLabel = ''
    showOperatorDropdown.value = false
    emitChange()
    return
  }

  // 切换操作符后可能需要清空已选值
  if (!op.multi && chip.values.length > 1) {
    chip.values = [chip.values[0]]
    chip.valueLabel = chip.values[0] || ''
  }

  showOperatorDropdown.value = false
  // 自动打开值选择
  nextTick(() => openValueSelector(editingIndex.value!))
}

// ===== 值选择 =====

async function openValueSelector(index: number) {
  editingIndex.value = index
  const chip = chips.value[index]
  if (!chip) return

  const field = allFields.value.find(f => f.key === chip.field)
  if (!field) return

  // 无需值的操作符不打开
  const op = field.operators.find(o => o.key === chip.operator)
  if (op?.noValue) return

  positionDropdownAtChip(index)
  valueSearchText.value = ''
  valueHighlightIndex.value = 0
  tempSelectedIds.value = new Set(chip.values)
  textInputValue1.value = chip.values[0] || ''
  textInputValue2.value = chip.values[1] || ''

  showValueDropdown.value = true
  showOperatorDropdown.value = false

  // 加载选项
  await loadValueOptions(chip.field, chip.operator)
  nextTick(() => valueInputRef.value?.focus())
}

async function loadValueOptions(fieldKey: string, operator: string) {
  valueOptionsLoading.value = true
  valueOptions.value = []

  try {
    const field = allFields.value.find(f => f.key === fieldKey)
    if (!field) return

    // 日期相对时间
    if (field.valueType === 'date' && operator === 'relative') {
      // 过滤掉 "已逾期" 给非截止日期字段
      const isDueDate = fieldKey === 'dueDate' || fieldKey.includes('due')
      valueOptions.value = isDueDate
        ? DATE_RELATIVE_OPTIONS
        : DATE_RELATIVE_OPTIONS.filter(o => o.id !== 'overdue')
      return
    }

    // 布尔字段
    if (field.valueType === 'bool') {
      valueOptions.value = [
        { id: 'true', label: '是' },
        { id: 'false', label: '否' },
      ]
      return
    }

    // 不需要下拉选项的类型
    if (field.valueType === 'date' || field.valueType === 'number' || field.valueType === 'text' || field.valueType === 'parent') {
      return
    }

    // 系统固定字段的值加载
    if (field.isSystem) {
      await loadSystemFieldOptions(fieldKey)
      return
    }

    // 自定义字段的值加载
    if (fieldKey.startsWith('cf.')) {
      const cfId = fieldKey.slice(3)
      await loadCustomFieldOptions(cfId)
    }
  } finally {
    valueOptionsLoading.value = false
  }
}

async function loadSystemFieldOptions(fieldKey: string) {
  const meOption: ValueOption = { id: '${currentUser}', label: '我（当前用户）', isSpecial: true }

  switch (fieldKey) {
    case 'project':
      valueOptions.value = props.projectList.map(p => ({ id: String(p.id), label: `${p.key} - ${p.name}` }))
      break

    case 'assignee':
    case 'reporter':
    case 'commenter': {
      const members = await loadProjectMembers()
      valueOptions.value = [meOption, ...members]
      break
    }

    case 'sprint': {
      const currentSprintOpt: ValueOption = { id: '${currentSprint}', label: '当前 Sprint', isSpecial: true }
      const sprints = await loadSprints()
      valueOptions.value = [currentSprintOpt, ...sprints]
      break
    }

    case 'tag': {
      const tags = await loadTags()
      valueOptions.value = tags
      break
    }

    // status 和 priority/type 现在走自定义字段体系
    // 但如果在 SYSTEM_FIELDS 中仍然需要兜底
    default:
      valueOptions.value = []
  }
}

async function loadCustomFieldOptions(cfId: string) {
  if (!props.projectId) {
    valueOptions.value = []
    return
  }
  try {
    const res = await customFieldApi.getProjectFieldOptions(props.projectId, cfId)
    const options = res.data || []
    valueOptions.value = options
      .filter((o: CustomFieldOptionVO) => !o.isArchived)
      .map((o: CustomFieldOptionVO) => ({ id: o.id, label: o.value, color: o.color || undefined }))
  } catch {
    valueOptions.value = []
  }
}

async function loadProjectMembers(): Promise<ValueOption[]> {
  const pid = props.projectId
  if (!pid) {
    // 全局模式，聚合前 10 个项目的成员
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
    return allMembers
  }
  try {
    const res = await projectApi.listMembers(pid, { _silent403: true })
    return (res.data || []).map((m: any) => ({ id: m.userId, label: m.displayName || m.username }))
  } catch {
    return []
  }
}

async function loadSprints(): Promise<ValueOption[]> {
  const pid = props.projectId
  if (!pid) {
    // 全局模式，聚合前 5 个项目的 sprint
    const all: ValueOption[] = []
    for (const p of props.projectList.slice(0, 5)) {
      try {
        const res = await sprintApi.listByProject(p.id, { _silent403: true })
        const sprints = res.data?.list || []
        sprints.forEach((s: SprintVO) => {
          all.push({ id: s.id, label: `${p.key} / ${s.name}${s.status === 'Active' ? ' (进行中)' : ''}` })
        })
      } catch { /* skip */ }
    }
    return all
  }
  try {
    const res = await sprintApi.listByProject(pid, { _silent403: true })
    const sprints = res.data?.list || []
    return sprints.map((s: SprintVO) => ({
      id: s.id,
      label: `${s.name}${s.status === 'Active' ? ' (进行中)' : s.status === 'Completed' ? ' (已完成)' : ''}`
    }))
  } catch {
    return []
  }
}

async function loadTags(): Promise<ValueOption[]> {
  const pid = props.projectId
  if (!pid) {
    // 全局模式
    const all: ValueOption[] = []
    const seen = new Set<string>()
    for (const p of props.projectList.slice(0, 10)) {
      try {
        const res = await tagApi.listProjectTags(p.id, { _silent403: true })
        const tags = res.data || []
        tags.forEach((t: any) => {
          if (!seen.has(t.id)) {
            seen.add(t.id)
            all.push({ id: t.id, label: t.name, color: t.color })
          }
        })
      } catch { /* skip */ }
    }
    return all
  }
  try {
    const res = await tagApi.listProjectTags(pid, { _silent403: true })
    return (res.data || []).map((t: any) => ({ id: t.id, label: t.name, color: t.color }))
  } catch {
    return []
  }
}

// ===== 值选择交互 =====

function toggleValueOption(opt: ValueOption) {
  if (isMultiSelectMode.value) {
    if (tempSelectedIds.value.has(opt.id)) {
      tempSelectedIds.value.delete(opt.id)
    } else {
      tempSelectedIds.value.add(opt.id)
    }
    // 强制 reactivity
    tempSelectedIds.value = new Set(tempSelectedIds.value)
  } else {
    // 单选：立即应用
    if (editingIndex.value === null) return
    const chip = chips.value[editingIndex.value]
    if (!chip) return
    chip.values = [opt.id]
    chip.valueLabel = opt.label
    showValueDropdown.value = false
    emitChange()
  }
}

function confirmMultiSelect() {
  if (editingIndex.value === null) return
  const chip = chips.value[editingIndex.value]
  if (!chip) return
  chip.values = [...tempSelectedIds.value]
  chip.valueLabel = buildValueLabelFromOptions(chip.values)
  showValueDropdown.value = false
  emitChange()
}

function buildValueLabelFromOptions(ids: string[]): string {
  if (ids.length === 0) return ''
  const labels = ids.map(id => {
    if (id === '${currentUser}') return '我'
    if (id === '${currentSprint}') return '当前 Sprint'
    const opt = valueOptions.value.find(o => o.id === id)
    return opt?.label || id
  })
  if (labels.length <= 2) return labels.join(', ')
  return `${labels[0]}, ${labels[1]} 等${labels.length}项`
}

function confirmTextInput() {
  if (editingIndex.value === null) return
  const chip = chips.value[editingIndex.value]
  if (!chip) return

  const val1 = textInputValue1.value.trim()
  if (!val1) return

  if (currentOperatorKey.value === 'between') {
    const val2 = textInputValue2.value.trim()
    if (!val2) return
    chip.values = [val1, val2]
    chip.valueLabel = `${val1} ~ ${val2}`
  } else {
    chip.values = [val1]
    chip.valueLabel = val1
  }

  showValueDropdown.value = false
  emitChange()
}

function selectHighlightedValue() {
  if (filteredValueOptions.value.length > 0) {
    toggleValueOption(filteredValueOptions.value[valueHighlightIndex.value])
    if (!isMultiSelectMode.value) return
  }
}

function moveValueHighlight(delta: number) {
  const len = filteredValueOptions.value.length
  if (len === 0) return
  valueHighlightIndex.value = (valueHighlightIndex.value + delta + len) % len
}

// ===== Chip 管理 =====

function removeChip(index: number) {
  chips.value.splice(index, 1)
  emitChange()
}

function emitChange() {
  emit('update:modelValue', chipsToConditions())
}

// ===== 下拉定位 =====

function positionDropdownAtChip(index: number) {
  const chipEls = document.querySelectorAll('.issue-filter-editor .filter-chip')
  const el = chipEls[index]
  if (el) {
    const rect = el.getBoundingClientRect()
    dropdownPosition.value = { top: `${rect.bottom + 4}px`, left: `${rect.left}px`, minWidth: '180px' }
  }
}

function updateDropdownPosition(el: HTMLElement | null) {
  if (el) {
    const rect = el.getBoundingClientRect()
    dropdownPosition.value = { top: `${rect.bottom + 4}px`, left: `${rect.left}px`, minWidth: `${Math.max(rect.width, 180)}px` }
  }
}

function closeDropdowns() {
  showOperatorDropdown.value = false
  showValueDropdown.value = false
  // 如果多选进行中，应用已选值
  if (isMultiSelectMode.value && editingIndex.value !== null && tempSelectedIds.value.size > 0) {
    const chip = chips.value[editingIndex.value]
    if (chip) {
      chip.values = [...tempSelectedIds.value]
      chip.valueLabel = buildValueLabelFromOptions(chip.values)
      emitChange()
    }
  }
  editingIndex.value = null
}

// ===== 全局事件：点击外部关闭字段输入 =====

function handleClickOutside(e: MouseEvent) {
  if (showFieldInput.value && fieldInputWrapperRef.value && !fieldInputWrapperRef.value.contains(e.target as Node)) {
    cancelFieldInput()
  }
}

onMounted(() => document.addEventListener('mousedown', handleClickOutside))
onUnmounted(() => document.removeEventListener('mousedown', handleClickOutside))
</script>

<style scoped>
.issue-filter-editor {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.filter-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-chip {
  display: flex;
  align-items: center;
  height: 26px;
  border-radius: 4px;
  background: var(--tf-bg-elevated, #2a2d33);
  border: 1px solid var(--tf-border, #3d4148);
  font-size: 12px;
  overflow: hidden;
}

.chip-field,
.chip-operator,
.chip-value {
  padding: 0 6px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.1s;
}

.chip-field {
  color: var(--tf-text-secondary, #9ca3af);
  font-weight: 500;
}

.chip-operator {
  color: var(--tf-accent, #58a6ff);
  border-left: 1px solid var(--tf-border, #3d4148);
  border-right: 1px solid var(--tf-border, #3d4148);
}

.chip-value {
  color: var(--tf-text-primary, #e6edf3);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip-field:hover,
.chip-operator:hover,
.chip-value:hover {
  background: var(--tf-bg-hover, #30363d);
}

.chip-remove {
  padding: 0 5px;
  cursor: pointer;
  color: var(--tf-text-tertiary, #6b7280);
  font-size: 11px;
  transition: color 0.1s;
}

.chip-remove:hover {
  color: var(--tf-error, #f85149);
}

.readonly .chip-field,
.readonly .chip-operator,
.readonly .chip-value {
  cursor: default;
}

.readonly .chip-remove {
  display: none;
}

/* Add filter area */
.add-filter-area {
  flex-shrink: 0;
}

.add-filter-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px dashed var(--tf-border, #3d4148);
  border-radius: 4px;
  background: transparent;
  color: var(--tf-text-tertiary, #6b7280);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.add-filter-btn:hover {
  border-color: var(--tf-accent, #58a6ff);
  color: var(--tf-accent, #58a6ff);
}

.field-input-wrapper {
  position: relative;
}

.field-input {
  width: 140px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--tf-accent, #58a6ff);
  border-radius: 4px;
  background: var(--tf-bg-surface, #22252a);
  color: var(--tf-text-primary, #e6edf3);
  font-size: 12px;
  outline: none;
}

/* Dropdown styles (shared) */
.ife-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
}

.ife-dropdown {
  position: fixed;
  z-index: 1001;
  background: var(--tf-bg-elevated, #2a2d33);
  border: 1px solid var(--tf-border, #3d4148);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  max-height: 280px;
  overflow-y: auto;
  min-width: 180px;
}

.ife-field-dropdown {
  z-index: 1001;
  padding: 4px 0;
}

.ife-dropdown-title {
  padding: 6px 12px;
  font-size: 11px;
  color: var(--tf-text-tertiary, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.ife-dropdown-item {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--tf-text-primary, #e6edf3);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: background 0.1s;
}

.ife-dropdown-item:hover,
.ife-dropdown-item.active {
  background: var(--tf-bg-hover, #30363d);
}

.ife-dropdown-item.selected {
  color: var(--tf-accent, #58a6ff);
}

.ife-dropdown-item.special {
  font-style: italic;
  color: var(--tf-accent, #58a6ff);
}

.ife-check {
  font-size: 13px;
  flex-shrink: 0;
}

.ife-color-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ife-option-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ife-search-area,
.ife-text-input-area {
  padding: 8px;
  border-bottom: 1px solid var(--tf-border, #3d4148);
}

.ife-text-input-area {
  border-bottom: none;
}

.ife-text-input {
  width: 100%;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--tf-border, #3d4148);
  border-radius: 4px;
  background: var(--tf-bg-surface, #22252a);
  color: var(--tf-text-primary, #e6edf3);
  font-size: 12px;
  outline: none;
}

.ife-text-input:focus {
  border-color: var(--tf-accent, #58a6ff);
}

.ife-input-label {
  font-size: 11px;
  color: var(--tf-text-tertiary, #6b7280);
  margin-bottom: 4px;
}

.ife-options-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px 0;
}

.ife-loading,
.ife-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--tf-text-tertiary, #6b7280);
  text-align: center;
}

.ife-dropdown-footer {
  padding: 8px;
  border-top: 1px solid var(--tf-border, #3d4148);
  display: flex;
  justify-content: flex-end;
}

.ife-confirm-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  background: var(--tf-accent, #58a6ff);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.ife-confirm-btn:hover {
  opacity: 0.9;
}
</style>
