<template>
  <div class="query-input-container" ref="containerRef">
    <div class="query-input-wrapper">
      <input
        ref="inputRef"
        :value="modelValue"
        class="query-input"
        :placeholder="placeholder"
        @input="onInput"
        @keydown="onKeydown"
        @focus="onFocus"
        @blur="onBlur"
      />
      <span v-if="modelValue" class="query-clear" @mousedown.prevent="modelValue = ''">✕</span>
    </div>
    <!-- Autocomplete dropdown -->
    <div v-if="showDropdown && suggestions.length > 0" class="query-dropdown" ref="dropdownRef">
      <div
        v-for="(item, i) in suggestions"
        :key="item.id + '-' + i"
        class="query-dropdown-item"
        :class="{ active: activeIndex === i, separator: item.isSeparator }"
        @mousedown.prevent="selectSuggestion(item)"
        @mouseenter="activeIndex = i"
      >
        <span class="suggestion-label">{{ item.label }}</span>
        <span v-if="item.hint" class="suggestion-hint">{{ item.hint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { projectApi, customFieldApi } from '@/api'
import type { IssueStatusVO, ProjectVO } from '@/api/types'
import { localizeStatusName, issueTypeLabelMap } from '@/utils/fieldLabels'
import { loadIssueTypeOptions } from '../composables/useIssueTypeOptions'

// ==================== Types ====================

interface Suggestion {
  id: string
  label: string
  hint?: string
  insertText: string
  isSeparator?: boolean
  type: 'field' | 'value' | 'operator' | 'keyword'
}

interface FieldDef {
  key: string         // Internal key for JSON filter (e.g. "status", "cf.123456")
  label: string       // Display name for suggestions (e.g. "状态")
  queryKey: string    // What user types in query (e.g. "状态")
  valueType: 'enum' | 'user' | 'date' | 'text' | 'number' | 'boolean'
  getValues?: () => Promise<Array<{ id: string; label: string }>> | Array<{ id: string; label: string }>
}

// ==================== Props & Emits ====================

const modelValue = defineModel<string>({ default: '' })

const props = defineProps<{
  placeholder?: string
  statusList: IssueStatusVO[]
  projectList: ProjectVO[]
  projectId?: string | null
}>()

const emit = defineEmits<{
  (e: 'submit'): void
}>()

// ==================== State ====================

const inputRef = ref<HTMLInputElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const showDropdown = ref(false)
const activeIndex = ref(0)
const suggestions = ref<Suggestion[]>([])
const userCache = ref<Array<{ id: string; label: string }>>([])
const issueTypeCache = ref<Array<{ id: string; label: string }>>([])
const customFields = ref<FieldDef[]>([])
let blurTimeout: ReturnType<typeof setTimeout> | null = null

// ==================== Built-in field definitions ====================

function getBuiltinFields(): FieldDef[] {
  return [
    {
      key: 'status', label: '状态', queryKey: '状态', valueType: 'enum',
      getValues: () => {
        const special = [{ id: '__open__', label: '未关闭' }, { id: '__closed__', label: '已关闭' }]
        const statuses = props.statusList.map(s => ({ id: s.name, label: localizeStatusName(s.name) }))
        return [...special, ...statuses]
      }
    },
    {
      key: 'priority', label: '优先级', queryKey: '优先级', valueType: 'enum',
      getValues: () => [
        { id: '阻塞', label: '阻塞' },
        { id: '紧急', label: '紧急' },
        { id: '高', label: '高' },
        { id: '普通', label: '普通' },
        { id: '低', label: '低' },
      ]
    },
    {
      key: 'assignee', label: '负责人', queryKey: '负责人', valueType: 'user',
      getValues: () => [{ id: 'me', label: '我' }, ...userCache.value]
    },
    {
      key: 'reporter', label: '报告人', queryKey: '报告人', valueType: 'user',
      getValues: () => [{ id: 'me', label: '我' }, ...userCache.value]
    },
    {
      key: 'type', label: '类型', queryKey: '类型', valueType: 'enum',
      getValues: () => issueTypeCache.value.length > 0
        ? issueTypeCache.value
        : Object.values(issueTypeLabelMap).map(label => ({ id: label, label }))
    },
    {
      key: 'sprint', label: 'Sprint', queryKey: 'Sprint', valueType: 'enum',
      getValues: () => [] // Loaded contextually
    },
    {
      key: 'project', label: '项目', queryKey: '项目', valueType: 'enum',
      getValues: () => props.projectList.map(p => ({ id: p.key || p.id, label: `${p.key} - ${p.name}` }))
    },
    {
      key: 'dueDate', label: '截止日期', queryKey: '截止日期', valueType: 'date',
      getValues: () => getDateKeywords()
    },
    {
      key: 'createdAt', label: '创建日期', queryKey: '创建日期', valueType: 'date',
      getValues: () => getDateKeywords()
    },
    {
      key: 'updatedAt', label: '更新日期', queryKey: '更新日期', valueType: 'date',
      getValues: () => getDateKeywords()
    },
    {
      key: 'resolvedAt', label: '解决日期', queryKey: '解决日期', valueType: 'date',
      getValues: () => getDateKeywords()
    },
    {
      key: 'keyword', label: '关键词', queryKey: '关键词', valueType: 'text',
      getValues: () => []
    },
  ]
}

function getDateKeywords(): Array<{ id: string; label: string }> {
  return [
    { id: 'today', label: '今天' },
    { id: 'yesterday', label: '昨天' },
    { id: 'this week', label: '本周' },
    { id: 'last week', label: '上周' },
    { id: 'this month', label: '本月' },
    { id: 'last month', label: '上月' },
  ]
}

// All available fields (built-in + custom)
function getAllFields(): FieldDef[] {
  return [...getBuiltinFields(), ...customFields.value]
}

// ==================== Operator syntax hints ====================

function getOperatorHints(): Suggestion[] {
  return [
    { id: 'op-comma', label: ',', hint: '多值（或关系）', insertText: ', ', type: 'operator' },
    { id: 'op-range', label: '..', hint: '值范围（日期/数值/枚举排序）', insertText: ' .. ', type: 'operator' },
    { id: 'op-exclude', label: '-', hint: '排除值', insertText: '-', type: 'operator' },
    { id: 'op-empty', label: '无', hint: '字段为空', insertText: '无', type: 'keyword' },
    { id: 'op-any', label: '有', hint: '字段非空', insertText: '有', type: 'keyword' },
  ]
}

// ==================== Input handling ====================

function onInput(e: Event) {
  const value = (e.target as HTMLInputElement).value
  modelValue.value = value
  nextTick(() => updateSuggestions())
}

function onFocus() {
  if (blurTimeout) { clearTimeout(blurTimeout); blurTimeout = null }
  updateSuggestions()
}

function onBlur() {
  blurTimeout = setTimeout(() => {
    showDropdown.value = false
    blurTimeout = null
  }, 200)
}

function onKeydown(e: KeyboardEvent) {
  if (!showDropdown.value || suggestions.value.length === 0) {
    if (e.key === 'Escape') showDropdown.value = false
    if (e.key === 'Enter') emit('submit')
    return
  }

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      activeIndex.value = (activeIndex.value + 1) % suggestions.value.length
      scrollActiveIntoView()
      break
    case 'ArrowUp':
      e.preventDefault()
      activeIndex.value = (activeIndex.value - 1 + suggestions.value.length) % suggestions.value.length
      scrollActiveIntoView()
      break
    case 'Enter':
    case 'Tab':
      e.preventDefault()
      selectSuggestion(suggestions.value[activeIndex.value])
      break
    case 'Escape':
      e.preventDefault()
      showDropdown.value = false
      break
  }
}

function scrollActiveIntoView() {
  nextTick(() => {
    const el = dropdownRef.value?.querySelector('.query-dropdown-item.active')
    el?.scrollIntoView({ block: 'nearest' })
  })
}

// ==================== Context detection ====================

interface CursorContext {
  type: 'field' | 'value' | 'operator_hint'
  partial: string
  fieldDef?: FieldDef
  inExclude?: boolean  // after "-" prefix
}

function getContextAtCursor(text: string, cursorPos: number): CursorContext {
  const before = text.substring(0, cursorPos)
  const allFields = getAllFields()

  // Find all field:value segments by detecting known field queryKeys
  const fieldPositions: Array<{ start: number; end: number; fieldDef: FieldDef }> = []
  for (const f of allFields) {
    const escaped = f.queryKey.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const pattern = new RegExp(`${escaped}[：:]\\s*`, 'g')
    let m: RegExpExecArray | null
    while ((m = pattern.exec(text)) !== null) {
      fieldPositions.push({ start: m.index, end: m.index + m[0].length, fieldDef: f })
    }
  }
  fieldPositions.sort((a, b) => a.start - b.start)

  // Check if cursor is inside a field's value area
  for (let i = 0; i < fieldPositions.length; i++) {
    const fp = fieldPositions[i]
    const nextStart = i + 1 < fieldPositions.length ? fieldPositions[i + 1].start : text.length
    if (cursorPos >= fp.end && cursorPos <= nextStart) {
      const valuePart = text.substring(fp.end, cursorPos)
      // Check if after last comma (multi-value context)
      const lastComma = valuePart.lastIndexOf(',')
      const currentValue = lastComma >= 0 ? valuePart.substring(lastComma + 1).trim() : valuePart.trim()
      const inExclude = currentValue.startsWith('-')
      const partial = inExclude ? currentValue.substring(1) : currentValue

      // If there's meaningful value text AND trailing space(s), user wants next field
      const trimmedValue = valuePart.trimEnd()
      const hasTrailingSpace = valuePart.length > trimmedValue.length && trimmedValue.length > 0
      const afterLastComma = lastComma >= 0 ? valuePart.substring(lastComma + 1) : valuePart
      const commaTrailing = lastComma >= 0 && afterLastComma.trim() === '' // Just typed comma + space

      if (hasTrailingSpace && !commaTrailing && trimmedValue.length > 0) {
        // User finished typing a value and pressed space → suggest new field
        return { type: 'field', partial: '' }
      }

      return { type: 'value', partial, fieldDef: fp.fieldDef, inExclude }
    }
  }

  // Cursor is not inside any field's value → typing a field name
  const lastSpaceIdx = before.lastIndexOf(' ')
  const partial = lastSpaceIdx >= 0 ? before.substring(lastSpaceIdx + 1) : before
  return { type: 'field', partial: partial.trim() }
}

// ==================== Suggestion generation ====================

async function updateSuggestions() {
  const input = inputRef.value
  if (!input) return

  const text = input.value || ''
  const cursorPos = input.selectionStart ?? text.length
  const context = getContextAtCursor(text, cursorPos)

  if (context.type === 'field') {
    const kw = context.partial.toLowerCase()
    const usedFields = getUsedFields(text)
    const allFields = getAllFields()

    const fieldSuggestions: Suggestion[] = allFields
      .filter(f => !usedFields.has(f.key))
      .filter(f => !kw || f.label.toLowerCase().includes(kw) || f.queryKey.toLowerCase().includes(kw))
      .slice(0, 12)
      .map(f => ({
        id: f.key,
        label: `${f.queryKey}:`,
        hint: `按 ${f.label} 筛选`,
        insertText: `${f.queryKey}: `,
        type: 'field' as const
      }))

    // Also suggest operators if text is non-empty
    if (text.trim().length > 0 && !kw) {
      suggestions.value = [...getOperatorHints(), ...fieldSuggestions]
    } else {
      suggestions.value = fieldSuggestions
    }
  } else if (context.type === 'value' && context.fieldDef) {
    const values = await resolveFieldValues(context.fieldDef)
    const kw = context.partial.toLowerCase()
    suggestions.value = values
      .filter(v => !kw || v.label.toLowerCase().includes(kw))
      .slice(0, 15)
      .map(v => ({
        id: v.id,
        label: v.label,
        hint: undefined,
        insertText: v.label,
        type: 'value' as const
      }))
  } else {
    suggestions.value = []
  }

  showDropdown.value = suggestions.value.length > 0
  activeIndex.value = 0
}

async function resolveFieldValues(fieldDef: FieldDef): Promise<Array<{ id: string; label: string }>> {
  if (!fieldDef.getValues) return []
  const result = fieldDef.getValues()
  if (result instanceof Promise) return await result
  return result
}

function getUsedFields(text: string): Set<string> {
  const used = new Set<string>()
  const allFields = getAllFields()
  for (const f of allFields) {
    if (text.includes(`${f.queryKey}:`) || text.includes(`${f.queryKey}：`)) {
      used.add(f.key)
    }
  }
  return used
}

// ==================== Select suggestion ====================

function selectSuggestion(item: Suggestion) {
  if (blurTimeout) { clearTimeout(blurTimeout); blurTimeout = null }

  const input = inputRef.value
  if (!input) return

  const text = input.value || ''
  const cursorPos = input.selectionStart ?? text.length
  const before = text.substring(0, cursorPos)
  const after = text.substring(cursorPos)
  const context = getContextAtCursor(text, cursorPos)

  let newText: string
  let newCursorPos: number

  if (item.type === 'operator') {
    // Insert operator at cursor position
    newText = before + item.insertText + after
    newCursorPos = before.length + item.insertText.length
  } else if (context.type === 'field') {
    // Replace partial field text
    const lastSpaceIdx = before.lastIndexOf(' ')
    const prefix = lastSpaceIdx >= 0 ? before.substring(0, lastSpaceIdx + 1) : ''
    newText = `${prefix}${item.insertText}${after}`
    newCursorPos = (prefix + item.insertText).length
  } else if (context.type === 'value' && context.fieldDef) {
    // Replace partial value with selected value
    // Find the field's colon position
    const allFields = getAllFields()
    let fieldEnd = -1
    for (const f of allFields) {
      const patterns = [`${f.queryKey}: `, `${f.queryKey}:`, `${f.queryKey}： `]
      for (const p of patterns) {
        const idx = before.lastIndexOf(p)
        if (idx >= 0 && idx + p.length > fieldEnd) {
          fieldEnd = idx + p.length
        }
      }
    }

    if (fieldEnd >= 0) {
      const valuePart = before.substring(fieldEnd)
      const lastComma = valuePart.lastIndexOf(',')
      let replaceFrom: number
      if (lastComma >= 0) {
        replaceFrom = fieldEnd + lastComma + 1
        // Preserve space after comma
        const afterComma = valuePart.substring(lastComma + 1)
        const leadingSpace = afterComma.match(/^\s*/)?.[0] || ''
        replaceFrom += leadingSpace.length
      } else {
        replaceFrom = fieldEnd
      }
      const prefix = text.substring(0, replaceFrom)
      newText = `${prefix}${item.insertText} ${after.trimStart()}`
      newCursorPos = (prefix + item.insertText + ' ').length
    } else {
      newText = text + item.insertText + ' '
      newCursorPos = newText.length
    }
  } else {
    newText = before + item.insertText + after
    newCursorPos = before.length + item.insertText.length
  }

  modelValue.value = newText
  if (inputRef.value) {
    inputRef.value.value = newText
  }

  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.focus()
      inputRef.value.setSelectionRange(newCursorPos, newCursorPos)
    }
    setTimeout(() => updateSuggestions(), 50)
  })
}

// ==================== Load dynamic data ====================

async function loadUsers() {
  try {
    let members: Array<{ displayName?: string; username: string }> = []

    if (props.projectId) {
      // Single project selected — load its members
      const res = await projectApi.listMembers(props.projectId, { _silent403: true })
      members = res.data || []
    } else {
      // All projects mode — aggregate members from all visible projects (deduplicated)
      const results = await Promise.allSettled(
        props.projectList.map(p => projectApi.listMembers(p.id, { _silent403: true }))
      )
      const seen = new Set<string>()
      for (const result of results) {
        if (result.status === 'fulfilled' && result.value.data) {
          for (const m of result.value.data) {
            const key = m.displayName || m.username
            if (!seen.has(key)) {
              seen.add(key)
              members.push(m)
            }
          }
        }
      }
    }

    userCache.value = members.map((m: any) => ({
      id: m.displayName || m.username,
      label: m.displayName || m.username
    }))
  } catch (e) {
    console.error('[QueryInput] 加载用户列表失败:', e)
  }
}

async function loadCustomFields() {
  try {
    const pid = props.projectId
    if (!pid) return
    const res = await customFieldApi.listByProject(pid)
    const fields = res.data || []
    customFields.value = fields.map((f: any) => ({
      key: `cf.${f.id}`,
      label: f.name,
      queryKey: f.name,
      valueType: mapFieldFormat(f.fieldFormat),
      getValues: () => {
        if ((f.fieldFormat === 'list' || f.fieldFormat === 'ownedField' || f.fieldFormat === 'version' || f.fieldFormat === 'build') && f.options) {
          return f.options.map((o: any) => ({ id: o.value, label: o.value }))
        }
        if (f.fieldFormat === 'user') {
          return [{ id: 'me', label: '我' }, ...userCache.value]
        }
        if (f.fieldFormat === 'bool') {
          return [{ id: 'true', label: '是' }, { id: 'false', label: '否' }]
        }
        if (f.fieldFormat === 'date' || f.fieldFormat === 'datetime') {
          return getDateKeywords()
        }
        return []
      }
    }))
  } catch (e) {
    console.error('[QueryInput] 加载自定义字段失败:', e)
  }
}

function mapFieldFormat(format: string): FieldDef['valueType'] {
  switch (format) {
    case 'list': case 'ownedField': case 'version': return 'enum'
    case 'user': return 'user'
    case 'date': case 'datetime': return 'date'
    case 'int': case 'float': return 'number'
    case 'bool': return 'boolean'
    default: return 'text'
  }
}

async function loadIssueTypes() {
  try {
    const pid = props.projectId
    if (!pid) return
    const opts = await loadIssueTypeOptions(pid)
    issueTypeCache.value = opts.map(o => ({ id: o.value, label: o.label }))
  } catch (e) {
    console.error('[QueryInput] 加载工单类型失败:', e)
  }
}

// Watch projectId changes to reload custom fields and users
watch(() => props.projectId, () => {
  loadCustomFields()
  loadUsers()
  loadIssueTypes()
})

onMounted(() => {
  loadUsers()
  loadCustomFields()
  loadIssueTypes()
})

// Close dropdown on outside click
function handleOutsideClick(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    showDropdown.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', handleOutsideClick))
onUnmounted(() => document.removeEventListener('mousedown', handleOutsideClick))
</script>

<style scoped>
.query-input-container {
  position: relative;
  width: 100%;
}

.query-input-wrapper {
  display: flex;
  align-items: center;
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-md);
  background: var(--tf-bg-surface);
  transition: border-color 0.15s;
}

.query-input-wrapper:focus-within {
  border-color: var(--tf-accent);
}

.query-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  padding: 6px 10px;
  font-size: 13px;
  color: var(--tf-text-primary);
  font-family: 'JetBrains Mono', 'Fira Code', monospace, var(--tf-font-family);
}

.query-input::placeholder {
  color: var(--tf-text-tertiary);
  font-family: var(--tf-font-family);
}

.query-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 6px;
  border-radius: 50%;
  font-size: 11px;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.query-clear:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

/* Dropdown */
.query-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 280px;
  overflow-y: auto;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: var(--tf-radius-md);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  z-index: 100;
}

.query-dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  cursor: pointer;
  transition: background 0.1s;
}

.query-dropdown-item:hover,
.query-dropdown-item.active {
  background: var(--tf-accent-bg);
}

.query-dropdown-item.separator {
  border-top: 1px solid var(--tf-border);
  margin-top: 4px;
  padding-top: 8px;
}

.suggestion-label {
  font-size: 13px;
  color: var(--tf-text-primary);
  font-weight: 500;
}

.suggestion-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-left: 12px;
  white-space: nowrap;
}
</style>
