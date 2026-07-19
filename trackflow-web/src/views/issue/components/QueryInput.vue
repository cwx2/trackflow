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

  // Check if we're after a "field:" pattern (typing a value)
  // Match the last "字段: " before cursor
  const fieldValueMatch = before.match(/(\S+)[：:]\s*([^:：]*)$/)
  if (fieldValueMatch) {
    const fieldLabel = fieldValueMatch[1]
    const valuePart = fieldValueMatch[2]
    const field = FIELDS.find(f => f.queryKey === fieldLabel || f.label === fieldLabel)
    if (field) {
      return { type: 'value', partial: valuePart.trim(), fieldKey: field.key }
    }
  }

  // Check if we're at the beginning of a new token (typing a field name)
  // This is when: at start, after double-space, or after a complete "field: value" segment
  const lastSegment = before.split(/\s{2,}/).pop() || ''
  // If lastSegment doesn't contain ":" we're typing a field name
  if (!lastSegment.includes(':') && !lastSegment.includes('：')) {
    return { type: 'field', partial: lastSegment.trim(), fieldKey: '' }
  }

  // After a completed value, if there's trailing spaces, suggest new field
  if (/[：:]\s*\S+\s+$/.test(before) || /\s{2,}$/.test(before)) {
    return { type: 'field', partial: '', fieldKey: '' }
  }

  return { type: 'none', partial: '', fieldKey: '' }
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
    const lastSeparatorIdx = Math.max(before.lastIndexOf('  '), -1)
    const prefix = before.substring(0, lastSeparatorIdx + 1)
    const trailingSpace = prefix && !prefix.endsWith(' ') ? '  ' : ''
    newText = `${prefix}${trailingSpace}${item.insertText}${after}`
    newCursorPos = (prefix + trailingSpace + item.insertText).length
  } else if (context.type === 'value') {
    // Replace partial value with selected value
    const fieldMatch = before.match(/^(.*\S+[：:]\s*)([^:：]*)$/)
    if (fieldMatch) {
      const prefix = fieldMatch[1]
      newText = `${prefix}${item.insertText}${after}`
      newCursorPos = (prefix + item.insertText).length
    } else {
      newText = text + item.insertText
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
    // After inserting a field, immediately show value suggestions
    if (context.type === 'field') {
      nextTick(() => updateSuggestions())
    } else {
      showDropdown.value = false
    }
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
