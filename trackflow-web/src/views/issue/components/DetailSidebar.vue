<template>
  <aside class="detail-sidebar" :class="{ collapsed: collapsed }">
    <!-- 折叠控制按钮 -->
    <button
      class="sidebar-toggle-btn"
      :title="collapsed ? '展开字段面板' : '折叠字段面板'"
      @click="$emit('toggle-collapse')"
    >
      <svg
        viewBox="0 0 16 16"
        width="14"
        height="14"
        fill="currentColor"
        class="toggle-icon"
        :class="{ flipped: collapsed }"
      >
        <path d="M9.78 12.78a.75.75 0 0 1-1.06 0L4.47 8.53a.75.75 0 0 1 0-1.06l4.25-4.25a.75.75 0 0 1 1.06 1.06L6.06 8l3.72 3.72a.75.75 0 0 1 0 1.06z" />
      </svg>
    </button>

    <!-- 折叠状态下的占位提示 -->
    <div v-if="collapsed" class="collapsed-hint">
      <span class="collapsed-label">字段</span>
    </div>

    <!-- 字段列表（折叠时隐藏） -->
    <template v-if="!collapsed">
    <div
      v-for="field in fields"
      :key="field.key"
      class="sb-field"
      :class="{
        readonly: field.readonly || !field.editType,
        separator: field.key === '_sep',
        editable: !field.readonly && field.editType
      }"
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
            <TimeProgressIndicator
              v-if="field.progress && field.progress.estimated > 0"
              :spent="field.progress.spent"
              :estimated="field.progress.estimated"
            />
            <span v-if="field.dot" class="val-dot" :style="{ background: field.dot }"></span>
            <span class="val-text editable">{{ field.value }}</span>
            <span class="val-chevron" aria-hidden="true">‹</span>
          </div>
          <template #content>
            <div class="dropdown-panel">
              <!-- 搜索框 -->
              <div class="dropdown-search" v-if="field.editType === 'select' || field.editType === 'user-select' || field.editType === 'multi-select'">
                <input
                  ref="searchInputRef"
                  v-model="searchText"
                  class="search-input"
                  placeholder="筛选条目"
                  @keyup.escape="cancelEdit"
                />
              </div>
              <!-- 单选列表 -->
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
                <div v-if="getFilteredOptions(field).length === 0 && !addingOption" class="dropdown-empty">无匹配项</div>
                <!-- 内联添加新选项入口 -->
                <template v-if="field.canAddOption">
                  <div v-if="!addingOption" class="dropdown-add-option" @click.stop="startAddOption()">
                    <span class="add-icon">+</span>
                    <span class="add-text">添加新值</span>
                  </div>
                  <div v-else class="dropdown-add-input" @click.stop>
                    <input
                      ref="newOptionInputRef"
                      v-model="newOptionValue"
                      class="add-option-field"
                      placeholder="输入新值名称"
                      @keyup.enter="confirmAddOption(field)"
                      @keyup.escape="cancelAddOption()"
                    />
                    <button class="input-btn add-option-btn" :disabled="!newOptionValue.trim()" @click="confirmAddOption(field)">添加</button>
                  </div>
                </template>
              </div>
              <!-- 多选列表 -->
              <div class="dropdown-list" v-if="field.editType === 'multi-select'">
                <div
                  v-for="opt in getFilteredOptions(field)"
                  :key="opt.value"
                  class="dropdown-item multi-item"
                  :class="{ selected: multiSelectedValues.includes(opt.value) }"
                  @click="toggleMultiOption(field, opt.value)"
                >
                  <span class="item-check">{{ multiSelectedValues.includes(opt.value) ? '✓' : '' }}</span>
                  <span class="item-text">{{ opt.label }}</span>
                </div>
                <div v-if="getFilteredOptions(field).length === 0 && !addingOption" class="dropdown-empty">无匹配项</div>
                <!-- 内联添加新选项入口 -->
                <template v-if="field.canAddOption">
                  <div v-if="!addingOption" class="dropdown-add-option" @click.stop="startAddOption()">
                    <span class="add-icon">+</span>
                    <span class="add-text">添加新值</span>
                  </div>
                  <div v-else class="dropdown-add-input" @click.stop>
                    <input
                      ref="newOptionInputRef"
                      v-model="newOptionValue"
                      class="add-option-field"
                      placeholder="输入新值名称"
                      @keyup.enter="confirmAddOption(field)"
                      @keyup.escape="cancelAddOption()"
                    />
                    <button class="input-btn add-option-btn" :disabled="!newOptionValue.trim()" @click="confirmAddOption(field)">添加</button>
                  </div>
                </template>
              </div>
              <!-- 日期输入 -->
              <div class="dropdown-input" v-if="field.editType === 'date'">
                <input v-model="inputValue" type="date" class="input-field" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
                <button v-if="field.rawValue" class="input-btn clear-btn" @click="clearField(field)">清除</button>
              </div>
              <!-- 日期时间输入 -->
              <div class="dropdown-input" v-if="field.editType === 'datetime'">
                <input v-model="inputValue" type="datetime-local" class="input-field" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
                <button v-if="field.rawValue" class="input-btn clear-btn" @click="clearField(field)">清除</button>
              </div>
              <!-- 数字输入 -->
              <div class="dropdown-input" v-if="field.editType === 'number'">
                <input v-model="inputValue" type="number" min="0" step="0.5" class="input-field" placeholder="小时数" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
                <button v-if="field.rawValue" class="input-btn clear-btn" @click="clearField(field)">清除</button>
              </div>
              <!-- Issue 搜索 -->
              <div class="dropdown-input" v-if="field.editType === 'issue-search'">
                <input v-model="inputValue" type="text" class="input-field" placeholder="Issue Key" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
              </div>
              <!-- 文本输入 -->
              <div class="dropdown-input" v-if="field.editType === 'text'">
                <input v-model="inputValue" type="text" class="input-field" :placeholder="field.label" @keyup.enter="commitInput(field)" />
                <button class="input-btn" @click="commitInput(field)">确定</button>
              </div>
            </div>
          </template>
        </a-trigger>

        <!-- 只读字段（无 editType 或被权限限制） -->
        <div v-else class="sb-value readonly-value">
          <TimeProgressIndicator
            v-if="field.progress && field.progress.estimated > 0"
            :spent="field.progress.spent"
            :estimated="field.progress.estimated"
          />
          <span v-if="field.dot" class="val-dot" :style="{ background: field.dot }"></span>
          <span class="val-text">{{ field.value }}</span>
        </div>
      </template>
      <div v-else class="sep-line"></div>
    </div>
    </template><!-- end v-if="!collapsed" -->
  </aside>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import TimeProgressIndicator from './TimeProgressIndicator.vue'

export interface FieldOption {
  value: string
  label: string
  badge?: string
  badgeColor?: string
}

export interface TimeProgress {
  spent: number
  estimated: number
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
  editType?: 'select' | 'multi-select' | 'user-select' | 'date' | 'datetime' | 'number' | 'issue-search' | 'text'
  options?: FieldOption[]
  rawValue?: string
  /** 多值字段：当前选中的 ID 列表 */
  rawValues?: string[]
  /** 时间进度指示器数据（预估工时字段专用） */
  progress?: TimeProgress
  /** 是否允许内联添加新选项（仅 list 类型字段，有权限时为 true） */
  canAddOption?: boolean
  /** 自定义字段 ID（用于添加选项 API） */
  customFieldId?: string
}

export interface StatusInfo {
  id: string
  name: string
  color: string
  blocked?: boolean
  blockedBy?: string[]
  requireComment?: boolean
}

const props = defineProps<{
  status: StatusInfo
  transitions: StatusInfo[]
  fields: SidebarField[]
  collapsed?: boolean
}>()

const emit = defineEmits<{
  transition: [target: StatusInfo]
  'edit-field': [fieldKey: string, newValue: string | string[]]
  'clear-field': [fieldKey: string]
  'add-option': [fieldId: string, value: string]
  'toggle-collapse': []
}>()

const editingKey = ref<string | null>(null)
const searchText = ref('')
const inputValue = ref('')
const searchInputRef = ref<HTMLInputElement[]>()
/** 多值字段编辑状态：当前选中的值列表 */
const multiSelectedValues = ref<string[]>([])
/** 多值字段去抖保存定时器 */
let multiSaveTimer: ReturnType<typeof setTimeout> | null = null
/** 内联添加选项模式 */
const addingOption = ref(false)
const newOptionValue = ref('')
const addOptionLoading = ref(false)
const newOptionInputRef = ref<HTMLInputElement[]>()
function openEdit(field: SidebarField) {
  editingKey.value = field.key
  searchText.value = ''
  inputValue.value = field.rawValue || ''
  addingOption.value = false
  newOptionValue.value = ''
  // 初始化多值状态
  if (field.editType === 'multi-select') {
    multiSelectedValues.value = [...(field.rawValues || [])]
  }
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

function clearField(field: SidebarField) {
  emit('clear-field', field.key)
  editingKey.value = null
}

function toggleMultiOption(field: SidebarField, value: string) {
  const idx = multiSelectedValues.value.indexOf(value)
  if (idx >= 0) {
    multiSelectedValues.value.splice(idx, 1)
  } else {
    multiSelectedValues.value.push(value)
  }
  // 去抖保存：快速连续勾选时，300ms 内只发一次请求（取最终状态）
  if (multiSaveTimer) clearTimeout(multiSaveTimer)
  multiSaveTimer = setTimeout(() => {
    emit('edit-field', field.key, [...multiSelectedValues.value])
    multiSaveTimer = null
  }, 300)
}

function startAddOption() {
  addingOption.value = true
  newOptionValue.value = ''
  nextTick(() => {
    if (newOptionInputRef.value?.[0]) newOptionInputRef.value[0].focus()
  })
}

function cancelAddOption() {
  addingOption.value = false
  newOptionValue.value = ''
}

function confirmAddOption(field: SidebarField) {
  const val = newOptionValue.value.trim()
  if (!val || !field.customFieldId) return
  addOptionLoading.value = true
  emit('add-option', field.customFieldId, val)
  // Reset state — parent will reload field defs and options
  addingOption.value = false
  newOptionValue.value = ''
  addOptionLoading.value = false
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
  /* 过渡动画 */
  transition: width 200ms ease, padding 200ms ease;
  position: relative;
}

/* 折叠状态：只显示一个细条 */
.detail-sidebar.collapsed {
  width: 32px;
  min-width: 32px;
  padding: 8px 0;
  overflow: hidden;
}

/* ========== 折叠控制按钮 ========== */
.sidebar-toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: none;
  background: none;
  color: var(--tf-text-tertiary);
  cursor: pointer;
  transition: background 150ms, color 150ms;
  flex-shrink: 0;
  margin-bottom: 8px;
  margin-left: auto;
}

.detail-sidebar.collapsed .sidebar-toggle-btn {
  margin: 0 auto 8px;
}

.sidebar-toggle-btn:hover {
  background: var(--tf-bg-hover);
  color: var(--tf-text-primary);
}

.toggle-icon {
  transition: transform 200ms ease;
  flex-shrink: 0;
}

/* 折叠状态时图标翻转（朝右） */
.toggle-icon.flipped {
  transform: rotate(180deg);
}

/* ========== 折叠占位提示 ========== */
.collapsed-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8px;
}

.collapsed-label {
  font-size: 11px;
  color: var(--tf-text-muted);
  writing-mode: vertical-rl;
  text-orientation: mixed;
  letter-spacing: 1px;
  cursor: default;
}

/* ========== 字段容器 ========== */
.sb-field {
  padding: 6px 6px;
  border-radius: 3px;
  transition: background 150ms;
}

/* 可编辑字段 hover 效果 */
.sb-field.editable:hover {
  background: var(--tf-bg-hover);
}

/* 只读字段：无 hover 效果，cursor 保持默认 */
.sb-field.readonly {
  cursor: default;
}

.sb-field.separator {
  padding: 0;
  margin: 8px 0;
}

/* ========== Label ========== */
.sb-label {
  font-size: 11px;
  color: var(--tf-text-muted);
  margin-bottom: 2px;
}

/* ========== Value 通用 ========== */
.sb-value {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--tf-text-primary);
  min-height: 20px;
}

/* 可编辑字段值 */
.sb-value.clickable {
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  margin: -2px -4px;
  transition: background 120ms;
}
.sb-value.clickable:hover {
  background: var(--tf-bg-active, rgba(255, 255, 255, 0.06));
}

/* 只读字段值 */
.sb-value.readonly-value {
  cursor: default;
  color: var(--tf-text-secondary, var(--tf-text-primary));
}

/* ========== 值文本 ========== */
.val-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.val-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

/* 可编辑文本用强调色标识 */
.val-text.editable {
  color: var(--tf-accent);
}

/* 下拉箭头指示器（仅可编辑字段） */
.val-chevron {
  font-size: 11px;
  color: var(--tf-text-muted);
  transform: rotate(-90deg);
  opacity: 0;
  transition: opacity 150ms;
  flex-shrink: 0;
  margin-left: 2px;
}
.sb-field.editable:hover .val-chevron {
  opacity: 1;
}

.val-badge {
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  padding: 2px 5px;
  border-radius: 3px;
  flex-shrink: 0;
}

.sep-line {
  height: 1px;
  background: var(--tf-border-light);
}

/* ========== Dropdown Panel - YouTrack 风格 ========== */
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
  width: 100%;
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  color: var(--tf-text-primary);
}
.search-input::placeholder {
  color: var(--tf-text-muted);
}

.dropdown-list {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px 0;
}
.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  color: var(--tf-text-primary);
  transition: background 120ms;
}
.dropdown-item:hover {
  background: var(--tf-bg-hover);
}
.dropdown-item.selected {
  background: var(--tf-accent-bg);
  font-weight: 500;
}
.item-text {
  flex: 1;
}
.item-badge {
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  padding: 2px 6px;
  border-radius: 3px;
}
.dropdown-empty {
  padding: 16px;
  text-align: center;
  color: var(--tf-text-muted);
  font-size: 12px;
}

.dropdown-input {
  padding: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.input-field {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--tf-border);
  border-radius: 4px;
  background: var(--tf-bg-body);
  color: var(--tf-text-primary);
  font-size: 13px;
  outline: none;
}
.input-field:focus {
  border-color: var(--tf-accent);
}
.input-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  background: var(--tf-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.input-btn:hover {
  background: var(--tf-accent-hover);
}
.clear-btn {
  background: transparent;
  color: var(--tf-text-tertiary);
  border: 1px solid var(--tf-border);
}
.clear-btn:hover {
  color: var(--tf-error, #f85149);
  border-color: var(--tf-error, #f85149);
  background: transparent;
}

/* ===== Multi-select ===== */
.multi-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.multi-item .item-check {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  border: 1px solid var(--tf-border);
  font-size: 10px;
  color: var(--tf-accent);
  flex-shrink: 0;
}
.multi-item.selected .item-check {
  background: var(--tf-accent);
  border-color: var(--tf-accent);
  color: #fff;
}

/* ===== Inline Add Option ===== */
.dropdown-add-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 12px;
  color: var(--tf-accent);
  border-top: 1px solid var(--tf-border-light);
  transition: background 120ms;
}
.dropdown-add-option:hover {
  background: var(--tf-bg-hover);
}
.dropdown-add-option .add-icon {
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
}
.dropdown-add-option .add-text {
  font-size: 12px;
}
.dropdown-add-input {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-top: 1px solid var(--tf-border-light);
}
.add-option-field {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid var(--tf-border);
  border-radius: 4px;
  background: var(--tf-bg-body);
  color: var(--tf-text-primary);
  font-size: 12px;
  outline: none;
}
.add-option-field:focus {
  border-color: var(--tf-accent);
}
.add-option-btn {
  padding: 4px 8px;
  font-size: 11px;
}
.add-option-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
