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
      <span v-if="modelValue" class="query-clear" @mousedown.prevent="$emit('update:modelValue', '')">✕</span>
    </div>
    <!-- Autocomplete dropdown -->
    <div v-if="showDropdown && suggestions.length > 0" class="query-dropdown" ref="dropdownRef">
      <div
        v-for="(item, i) in suggestions"
        :key="item.id"
        class="query-dropdown-item"
        :class="{ active: activeIndex === i }"
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
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { issueApi, userApi, sprintApi } from '@/api'
import type { IssueStatusVO, ProjectVO } from '@/api/types'
import { localizeStatusName, issueTypeLabelMap, priorityLabelMap } from '@/utils/fieldLabels'

// ==================== Types ====================

interface Suggestion {
  id: string
  label: string
  hint?: string
  insertText: string  // What to insert when selected
}

interface FieldDef {
  key: string
  label: string       // Chinese display name
  queryKey: string    // What appears in query text (e.g. "状态")
}

// ==================== Props & Emits ====================

const props = defineProps<{
  modelValue: string
  placeholder?: string
  statusList: IssueStatusVO[]
  projectList: ProjectVO[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

// ==================== Field definitions ====================

const FIELDS: FieldDef[] = [
  { key: 'status', label: '状态', queryKey: '状态' },
  { key: 'priority', label: '优先级', queryKey: '优先级' },
  { key: 'assignee', label: '负责人', queryKey: '负责人' },
  { key: 'reporter', label: '报告人', queryKey: '报告人' },
  { key: 'type', label: '类型', queryKey: '类型' },
  { key: 'sprint', label: 'Sprint', queryKey: 'Sprint' },
  { key: 'project', label: '项目', queryKey: '项目' },
]

// ==================== State ====================

const inputRef = ref<HTMLInputElement | null>(null)
const containerRef = ref<HTMLElement | null>(null)
const dropdownRef = ref<HTMLElement | null>(null)
const showDropdown = ref(false)
const activeIndex = ref(0)
const suggestions = ref<Suggestion[]>([])
const userCache = ref<Array<{ id: string; label: string }>>([])

// ==================== Input handling ====================

function onInput(e: Event) {
  const value = (e.target as HTMLInputElement).value
  emit('update:modelValue', value)
  nextTick(() => updateSuggestions())
}

function onFocus() {
  updateSuggestions()
}

function onBlur() {
  // Delay to allow mousedown on dropdown items
  setTimeout(() => {
    showDropdown.value = false
  }, 200)
}

function onKeydown(e: KeyboardEvent) {
  if (!showDropdown.value || suggestions.value.length === 0) {
    // If Enter is pressed without dropdown, just let it through
    if (e.key === 'Escape') {
      showDropdown.value = false
    }
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

// ==================== Suggestion logic ====================

function updateSuggestions() {
  const input = inputRef.value
  if (!input) return

  const text = props.modelValue || ''
  const cursorPos = input.selectionStart ?? text.length

  // Determine context: what's around the cursor?
  const context = getContextAtCursor(text, cursorPos)

  if (context.type === 'field') {
    // Suggest field names
    const kw = context.partial.toLowerCase()
    const usedFields = getUsedFields(text)
    suggestions.value = FIELDS
      .filter(f => !usedFields.has(f.key))
      .filter(f => !kw || f.label.toLowerCase().includes(kw) || f.queryKey.toLowerCase().includes(kw))
      .map(f => ({
        id: f.key,
        label: `${f.queryKey}:`,
        hint: `按 ${f.label} 筛选`,
        insertText: `${f.queryKey}: `
      }))
  } else if (context.type === 'value') {
    // Suggest values for the field
    suggestions.value = getValueSuggestions(context.fieldKey, context.partial)
  } else {
    suggestions.value = []
  }

  showDropdown.value = suggestions.value.length > 0
  activeIndex.value = 0
}

interface CursorContext {
  type: 'field' | 'value' | 'none'
  partial: string
  fieldKey: string
}

function getContextAtCursor(text: string, cursorPos: number): CursorContext {
  // Get text before cursor
  const before = text.substring(0, cursorPos)

  // Strategy: parse the text as segments separated by known field patterns
  // A segment starts with a known field label followed by ":"
  // Find all field positions in the full text
  const fieldPositions: Array<{ start: number; end: number; key: string }> = []
  for (const f of FIELDS) {
    const pattern = new RegExp(`${f.queryKey}[：:]`, 'g')
    let m: RegExpExecArray | null
    while ((m = pattern.exec(text)) !== null) {
      fieldPositions.push({ start: m.index, end: m.index + m[0].length, key: f.key })
    }
  }
  fieldPositions.sort((a, b) => a.start - b.start)

  // Find which segment the cursor is in
  // If cursor is inside a "field: value" segment, we're editing a value
  // If cursor is after the last segment's value, we might be starting a new field
  
  let currentSegmentField: string | null = null
  for (let i = 0; i < fieldPositions.length; i++) {
    const fp = fieldPositions[i]
    const nextStart = i + 1 < fieldPositions.length ? fieldPositions[i + 1].start : text.length
    if (cursorPos >= fp.end && cursorPos <= nextStart) {
      // Cursor is in the value area of this field
      const valueSoFar = text.substring(fp.end, cursorPos).trimStart()
      currentSegmentField = fp.key
      // Check if the last character typed is a space after some value text
      // If user typed space after completing a value, they might want a new field
      // Heuristic: if there's a trailing space and some non-space before it, suggest fields
      const trimmedBefore = before.trimEnd()
      const trailingSpaces = before.length - trimmedBefore.length
      if (valueSoFar.length > 0 && trailingSpaces >= 1) {
        // User finished a value and pressed space → suggest new field
        // But only if the partial after the last space doesn't look like part of the value
        const afterLastSpace = before.substring(before.lastIndexOf(' ') + 1)
        // If after last space matches a partial field name, suggest fields
        const isPartialField = FIELDS.some(f => f.queryKey.startsWith(afterLastSpace) || f.label.startsWith(afterLastSpace))
        if (isPartialField || afterLastSpace === '') {
          return { type: 'field', partial: afterLastSpace, fieldKey: '' }
        }
      }
      return { type: 'value', partial: valueSoFar, fieldKey: currentSegmentField }
    }
  }

  // Cursor is before the first field, or there are no fields
  // Check if we have a partial field name
  const lastSpaceIdx = before.lastIndexOf(' ')
  const partial = lastSpaceIdx >= 0 ? before.substring(lastSpaceIdx + 1) : before
  return { type: 'field', partial: partial.trim(), fieldKey: '' }
}

function getUsedFields(text: string): Set<string> {
  const used = new Set<string>()
  for (const f of FIELDS) {
    if (text.includes(`${f.queryKey}:`) || text.includes(`${f.queryKey}：`)) {
      used.add(f.key)
    }
  }
  return used
}

function getValueSuggestions(fieldKey: string, partial: string): Suggestion[] {
  const kw = partial.toLowerCase()
  let options: Array<{ id: string; label: string }> = []

  switch (fieldKey) {
    case 'status':
      options = props.statusList.map(s => ({
        id: s.name,
        label: localizeStatusName(s.name)
      }))
      // Add special "未关闭" option
      options.unshift({ id: '__open__', label: '未关闭' })
      break

    case 'priority':
      options = [
        { id: 'Critical', label: priorityLabelMap['Critical'] || '紧急' },
        { id: 'High', label: priorityLabelMap['High'] || '高' },
        { id: 'Normal', label: priorityLabelMap['Normal'] || '普通' },
        { id: 'Low', label: priorityLabelMap['Low'] || '低' },
      ]
      break

    case 'type':
      options = Object.entries(issueTypeLabelMap).map(([id, label]) => ({
        id, label
      }))
      break

    case 'assignee':
    case 'reporter':
      options = [{ id: 'me', label: '我' }, ...userCache.value]
      break

    case 'project':
      options = props.projectList.map(p => ({
        id: p.key || p.name,
        label: p.key ? `${p.key} - ${p.name}` : p.name
      }))
      break

    case 'sprint':
      // Sprint values are contextual — just show hint
      options = [{ id: '_hint', label: '输入 Sprint 名称' }]
      break
  }

  if (kw) {
    options = options.filter(o => o.label.toLowerCase().includes(kw))
  }

  return options.slice(0, 12).map(o => ({
    id: o.id,
    label: o.label,
    insertText: o.label
  }))
}

// ==================== Select suggestion ====================

function selectSuggestion(item: Suggestion) {
  const input = inputRef.value
  if (!input) return

  const text = props.modelValue || ''
  const cursorPos = input.selectionStart ?? text.length
  const before = text.substring(0, cursorPos)
  const after = text.substring(cursorPos)

  const context = getContextAtCursor(text, cursorPos)

  let newText: string
  let newCursorPos: number

  if (context.type === 'field') {
    // Replace partial field text with selected field
    const lastSpaceIdx = before.lastIndexOf(' ')
    const prefix = lastSpaceIdx >= 0 ? before.substring(0, lastSpaceIdx + 1) : ''
    newText = `${prefix}${item.insertText}${after}`
    newCursorPos = (prefix + item.insertText).length
  } else if (context.type === 'value') {
    // Replace partial value with selected value, then add trailing space for next field
    // Find the "field:" prefix in the current segment
    const fieldPositions: Array<{ start: number; end: number }> = []
    for (const f of FIELDS) {
      const pattern = new RegExp(`${f.queryKey}[：:]\\s*`, 'g')
      let m: RegExpExecArray | null
      while ((m = pattern.exec(before)) !== null) {
        fieldPositions.push({ start: m.index, end: m.index + m[0].length })
      }
    }
    const lastField = fieldPositions[fieldPositions.length - 1]
    if (lastField) {
      const prefix = before.substring(0, lastField.end)
      newText = `${prefix}${item.insertText} ${after.trimStart()}`
      newCursorPos = (prefix + item.insertText + ' ').length
    } else {
      newText = text + item.insertText + ' '
      newCursorPos = newText.length
    }
  } else {
    newText = text + item.insertText
    newCursorPos = newText.length
  }

  emit('update:modelValue', newText)

  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.focus()
      inputRef.value.setSelectionRange(newCursorPos, newCursorPos)
    }
    // After inserting, show next suggestions
    nextTick(() => updateSuggestions())
  })
}

// ==================== Load users cache ====================

async function loadUsers() {
  try {
    const res = await userApi.list({ pageSize: 50 })
    const users = res.data?.list || []
    userCache.value = users.map((u: any) => ({
      id: u.displayName || u.username,
      label: u.displayName || u.username
    }))
  } catch { /* ignore */ }
}

onMounted(() => {
  loadUsers()
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
  max-height: 240px;
  overflow-y: auto;
  background: var(--tf-bg-elevated, #2a2d33);
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
