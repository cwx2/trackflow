<template>
  <aside class="detail-sidebar">
    <div
      v-for="field in fields"
      :key="field.key"
      class="sb-field"
      :class="{ readonly: field.readonly, separator: field.key === '_sep', 'permission-locked': field.readonly && field.editType }"
    >
      <template v-if="field.key !== '_sep'">
        <div class="sb-label">{{ field.label }}</div>

        <!-- 可编辑字段用 a-trigger 包裹 -->
        <a-trigger
          v-if="!field.readonly && field.editType"
          trigger="click"
          position="bl"
          :popup-visible="editingKey === field.key"
          @update:popup-visible="v => v ? openEdit(field) : cancelEdit()"
        >
          <div class="sb-value clickable">
            <span v-if="field.dot" class="val-dot" :style="{ background: field.dot }"></span>
            <span class="val-text editable">{{ field.value }}</span>
            <span v-if="field.badge" class="val-badge" :style="{ background: field.badgeColor || 'var(--tf-accent)' }">{{ field.badge }}</span>
          </div>
          <template #content>
            <div class="dropdown-panel">
              <!-- 搜索框 -->
              <div class="dropdown-search" v-if="field.editType === 'select' || field.editType === 'user-select'">
                <input
                  ref="searchInputRef"
                  v-model="searchText"
                  class="search-input"
                  placeholder="筛选条目"
                  @keyup.escape="cancelEdit"
                />
              </div>
              <!-- 选项列表 -->
              <div class="dropdown-list" v-if="field.editType === 'select' || field.editType === 'user-select'">
                <div
                  v-for="opt in getFilteredOptions(field)"
                  :key="opt.value"
                  class="dropdown-item"
                  :class="{ selected: opt.value === (field.rawValue || '') }"
                  @click="selectOption(field, opt.value)"
                >
                  <span class="item-text">{{ opt.label }}</span>
                  <span v-if="opt.badge" class="item-badge" :style="{ background: opt.badgeColor || 'var(--tf-accent)' }">{{ opt.badge }}</span>
                </div>
                <div v-if="getFilteredOptions(field).length === 0" class="dropdown-empty">无匹配项</div>
              </div>
              <!-- 日期输入 -->
              <div class="dropdown-input" v-if="field.editType === 'date'">
                <input v-model="inputValue" type="date" class="input-field" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
              </div>
              <!-- 数字输入 -->
              <div class="dropdown-input" v-if="field.editType === 'number'">
                <input v-model="inputValue" type="number" min="0" step="0.5" class="input-field" placeholder="小时数" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
              </div>
              <!-- Issue 搜索 -->
              <div class="dropdown-input" v-if="field.editType === 'issue-search'">
                <input v-model="inputValue" type="text" class="input-field" placeholder="Issue Key" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
              </div>
            </div>
          </template>
        </a-trigger>

        <!-- 只读字段 -->
        <div v-else class="sb-value">
          <span v-if="field.dot" class="val-dot" :style="{ background: field.dot }"></span>
          <span class="val-text">{{ field.value }}</span>
          <span v-if="field.readonly && field.editType" class="val-lock" title="权限不足，此字段为只读">🔒</span>
        </div>
      </template>
      <div v-else class="sep-line"></div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

export interface FieldOption {
  value: string
  label: string
  badge?: string
  badgeColor?: string
}

export interface SidebarField {
  key: string
  label: string
  value: string
  dot?: string
  badge?: string
  badgeColor?: string
  class?: string
  readonly?: boolean
  editType?: 'select' | 'user-select' | 'date' | 'number' | 'issue-search'
  options?: FieldOption[]
  rawValue?: string
}

export interface StatusInfo {
  id: string
  name: string
  color: string
}

const props = defineProps<{
  status: StatusInfo
  transitions: StatusInfo[]
  fields: SidebarField[]
}>()

const emit = defineEmits<{
  transition: [target: StatusInfo]
  'edit-field': [fieldKey: string, newValue: string]
}>()

const editingKey = ref<string | null>(null)
const searchText = ref('')
const inputValue = ref('')
const searchInputRef = ref<HTMLInputElement[]>()

function openEdit(field: SidebarField) {
  editingKey.value = field.key
  searchText.value = ''
  inputValue.value = field.rawValue || ''
  nextTick(() => {
    if (searchInputRef.value?.[0]) searchInputRef.value[0].focus()
  })
}

function cancelEdit() {
  editingKey.value = null
}

function getFilteredOptions(field: SidebarField) {
  const opts = field.options || []
  if (!searchText.value) return opts
  const kw = searchText.value.toLowerCase()
  return opts.filter(o => o.label.toLowerCase().includes(kw))
}

function selectOption(field: SidebarField, value: string) {
  if (field.key === 'state') {
    const target = [props.status, ...props.transitions].find(s => s.id === value)
    if (target && target.id !== props.status.id) {
      emit('transition', target)
    }
  } else {
    emit('edit-field', field.key, value)
  }
  editingKey.value = null
}

function commitInput(field: SidebarField) {
  emit('edit-field', field.key, inputValue.value)
  editingKey.value = null
}
</script>

<style scoped>
.detail-sidebar {
  width: 240px;
  flex-shrink: 0;
  border-left: 1px solid var(--tf-border);
  padding: 12px;
  overflow-y: auto;
  font-size: 12px;
  background: var(--tf-bg-surface);
}

.sb-field {
  padding: 6px 6px;
  border-radius: 3px;
  transition: background 150ms;
}
.sb-field:not(.readonly):not(.separator):hover { background: var(--tf-bg-hover); }
.sb-field.readonly { cursor: default; }
.sb-field.permission-locked { opacity: 0.7; }
.sb-field.permission-locked:hover { opacity: 0.85; }
.sb-field.separator { padding: 0; margin: 8px 0; }

.sb-label { font-size: 11px; color: var(--tf-text-muted); margin-bottom: 2px; }

.sb-value { display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--tf-text-primary); }
.sb-value.clickable { cursor: pointer; }
.val-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.val-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.val-text.editable { color: var(--tf-accent); }
.val-badge {
  font-size: 10px; font-weight: 700; color: #fff; padding: 2px 5px;
  border-radius: 3px; flex-shrink: 0;
}
.val-lock {
  font-size: 9px; flex-shrink: 0; opacity: 0.5; margin-left: 2px;
}
.sep-line { height: 1px; background: var(--tf-border-light); }

/* Dropdown Panel - YouTrack 风格 */
.dropdown-panel {
  width: 240px;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.dropdown-search {
  padding: 8px 12px;
  border-bottom: 1px solid var(--tf-border-light);
}
.search-input {
  width: 100%; border: none; outline: none; background: none;
  font-size: 13px; color: var(--tf-text-primary);
}
.search-input::placeholder { color: var(--tf-text-muted); }

.dropdown-list { max-height: 240px; overflow-y: auto; padding: 4px 0; }
.dropdown-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 14px; cursor: pointer; font-size: 13px;
  color: var(--tf-text-primary); transition: background 120ms;
}
.dropdown-item:hover { background: var(--tf-bg-hover); }
.dropdown-item.selected { background: var(--tf-accent-bg); font-weight: 500; }
.item-text { flex: 1; }
.item-badge {
  font-size: 10px; font-weight: 700; color: #fff;
  padding: 2px 6px; border-radius: 3px;
}
.dropdown-empty { padding: 16px; text-align: center; color: var(--tf-text-muted); font-size: 12px; }

.dropdown-input {
  padding: 12px; display: flex; gap: 8px; align-items: center;
}
.input-field {
  flex: 1; padding: 6px 10px; border: 1px solid var(--tf-border);
  border-radius: 4px; background: var(--tf-bg-body); color: var(--tf-text-primary);
  font-size: 13px; outline: none;
}
.input-field:focus { border-color: var(--tf-accent); }
.input-btn {
  padding: 6px 12px; border: none; border-radius: 4px;
  background: var(--tf-accent); color: #fff; font-size: 12px;
  font-weight: 500; cursor: pointer;
}
.input-btn:hover { background: var(--tf-accent-hover); }
</style>
