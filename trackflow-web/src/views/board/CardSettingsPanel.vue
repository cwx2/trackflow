<template>
  <div class="cards-settings">
    <div class="settings-hint">
      <p>配置看板卡片上展示的字段和颜色方案。修改后实时生效�?/p>
    </div>

    <!-- 卡片字段选择 -->
    <div class="section">
      <div class="section-title">显示字段</div>
      <div class="section-desc">选择看板卡片上要展示的信息字段，并为每个字段选择显示格式</div>
      <div class="field-list">
        <!-- 内置字段 -->
        <div
          v-for="field in builtInFields"
          :key="field.key"
          class="field-item"
          :class="{ 'field-item--checked': selectedFields.has(field.key) }"
        >
          <a-checkbox
            :model-value="selectedFields.has(field.key)"
            @change="(val: boolean) => toggleField(field.key, val)"
          />
          <span class="field-icon">{{ field.icon }}</span>
          <span class="field-label">{{ field.label }}</span>
          <!-- Display mode toggle (Full name / Initial) �?仅字段已勾选时可操�?-->
          <div
            v-if="selectedFields.has(field.key)"
            class="field-display-mode"
          >
            <a-radio-group
              :model-value="getFieldDisplayMode(field.key)"
              size="mini"
              type="button"
              @change="(val: string) => setFieldDisplayMode(field.key, val as 'full_name' | 'initial')"
            >
              <a-radio value="full_name" title="显示完整名称">Full name</a-radio>
              <a-radio value="initial" title="仅显示首字母缩写">Initial</a-radio>
            </a-radio-group>
          </div>
          <div v-else class="field-display-mode-placeholder"></div>
        </div>
        <!-- 自定义字段分隔标�?-->
        <div v-if="customFieldOptions.length > 0" class="field-group-divider">
          <span class="field-group-title">Custom fields</span>
        </div>
        <!-- 项目自定义字�?-->
        <div
          v-for="field in customFieldOptions"
          :key="field.key"
          class="field-item"
          :class="{ 'field-item--checked': selectedFields.has(field.key) }"
        >
          <a-checkbox
            :model-value="selectedFields.has(field.key)"
            @change="(val: boolean) => toggleField(field.key, val)"
          />
          <span class="field-icon">{{ field.icon }}</span>
          <span class="field-label">{{ field.label }}</span>
          <div
            v-if="selectedFields.has(field.key)"
            class="field-display-mode"
          >
            <a-radio-group
              :model-value="getFieldDisplayMode(field.key)"
              size="mini"
              type="button"
              @change="(val: string) => setFieldDisplayMode(field.key, val as 'full_name' | 'initial')"
            >
              <a-radio value="full_name" title="显示完整名称">Full name</a-radio>
              <a-radio value="initial" title="仅显示首字母缩写">Initial</a-radio>
            </a-radio-group>
          </div>
          <div v-else class="field-display-mode-placeholder"></div>
        </div>
      </div>
    </div>

    <!-- 估算字段配置（YouTrack Cards Tab 对标�?-->
    <div class="section">
      <div class="section-title">估算字段</div>
      <div class="section-desc">配置看板卡片上展示的估算值字段，支持追踪 Sprint 期间工作量变�?/div>

      <div class="estimation-fields">
        <!-- Current estimation field -->
        <div class="estimation-field-row">
          <div class="estimation-field-label">
            <span class="estimation-field-name">Current estimation field</span>
            <span class="estimation-field-hint">Sprint 期间可编辑的当前估算�?/span>
          </div>
          <a-select
            v-model="selectedCurrentEstimationFieldId"
            placeholder="不配�?
            allow-clear
            :loading="fieldsLoading"
            class="estimation-field-select"
            @change="onCurrentEstimationFieldChange"
          >
            <a-option
              v-for="field in numericFields"
              :key="field.id"
              :value="field.id"
            >
              {{ field.name }}
              <span class="field-format-badge">{{ fieldFormatLabel(field.fieldFormat) }}</span>
            </a-option>
          </a-select>
        </div>

        <!-- Original estimation field -->
        <div class="estimation-field-row">
          <div class="estimation-field-label">
            <span class="estimation-field-name">Original estimation field</span>
            <span class="estimation-field-hint">Sprint 开始时快照的原始估算值（只读基线�?/span>
          </div>
          <a-select
            v-model="selectedOriginalEstimationFieldId"
            placeholder="不配�?
            allow-clear
            :loading="fieldsLoading"
            class="estimation-field-select"
            @change="onOriginalEstimationFieldChange"
          >
            <a-option
              v-for="field in numericFields"
              :key="field.id"
              :value="field.id"
            >
              {{ field.name }}
              <span class="field-format-badge">{{ fieldFormatLabel(field.fieldFormat) }}</span>
            </a-option>
          </a-select>
        </div>
      </div>

      <div class="estimation-fields-hint">
        <icon-info-circle class="hint-icon" />
        <span>
          仅支持数值类型（integer/float）的自定义字段。配置后燃尽图可使用估算值计算进度�?        </span>
      </div>
    </div>

    <!-- 颜色方案 -->
    <div class="section">
      <div class="section-title">颜色方案</div>
      <div class="section-desc">为卡片添加颜色指示，快速区分不同类别的工单</div>
      <a-radio-group v-model="selectedColorScheme" direction="vertical" class="color-scheme-group">
        <a-radio value="none">
          <div class="scheme-option">
            <span class="scheme-name">无着�?/span>
            <span class="scheme-desc">卡片不添加额外颜�?/span>
          </div>
        </a-radio>
        <a-radio value="project">
          <div class="scheme-option">
            <span class="scheme-name">按项目着�?/span>
            <span class="scheme-desc">多项目看板中，每个项目用不同颜色标识，快速区分卡片来�?/span>
          </div>
        </a-radio>
        <a-radio value="priority">
          <div class="scheme-option">
            <span class="scheme-name">按优先级着�?/span>
            <span class="scheme-desc">卡片左侧边框显示优先级对应颜�?/span>
          </div>
        </a-radio>
        <a-radio value="type">
          <div class="scheme-option">
            <span class="scheme-name">按类型着�?/span>
            <span class="scheme-desc">卡片左侧边框显示工单类型对应颜色</span>
          </div>
        </a-radio>
      </a-radio-group>
    </div>

    <!-- 自定义字段颜色指示（YouTrack Cards Tab 对标�?-->
    <div class="section">
      <div class="section-title">自定义字段颜�?/div>
      <div class="setting-row">
        <div class="setting-row-label">
          <span class="setting-row-name">显示其他自定义字段的颜色指示</span>
          <span class="setting-row-hint">为卡片上展示的自定义字段值附加对应颜色的视觉指示器（颜色圆点），便于快速识别字段值分�?/span>
        </div>
        <a-switch
          v-model="selectedShowCustomFieldColors"
          @change="onShowCustomFieldColorsChange"
        />
      </div>
    </div>

    <!-- 迭代分配设置（YouTrack Cards Tab: Allow cards to be assigned to multiple sprints�?-->
    <div class="section">
      <div class="section-title">迭代分配</div>
      <div class="setting-row">
        <div class="setting-row-label">
          <span class="setting-row-name">允许卡片分配到多个迭�?/span>
          <span class="setting-row-hint">启用后，工单可同时关联多�?Sprint，适用于跨迭代规划或延续性工作。关闭时工单仅能属于单个 Sprint�?/span>
        </div>
        <a-switch
          v-model="selectedAllowMultipleSprints"
          @change="onAllowMultipleSprintsChange"
        />
      </div>
    </div>

    <!-- 预览 -->
    <div class="section">
      <div class="section-title">卡片预览</div>
      <div class="card-preview" :class="previewColorClass">
        <div class="preview-header">
          <span class="preview-key">DE4-42</span>
          <span v-if="selectedFields.has('priority')" class="preview-priority">🟠</span>
        </div>
        <div class="preview-title">优化用户登录体验</div>
        <div class="preview-fields">
          <span v-if="selectedFields.has('type')" class="preview-tag">任务</span>
          <span v-if="selectedFields.has('tags')" class="preview-tag preview-tag--accent">UI</span>
          <span v-if="selectedFields.has('dueDate')" class="preview-tag">📅 07-20</span>
          <span v-if="selectedFields.has('sprint')" class="preview-tag">🏃 Sprint 3</span>
          <span v-if="selectedFields.has('estimatedHours')" class="preview-tag">�?4h</span>
          <span v-if="selectedCurrentEstimationFieldId" class="preview-tag preview-tag--estimation">�?6h</span>
          <!-- 自定义字段预�?-->
          <template v-for="field in customFieldOptions" :key="field.key">
            <span v-if="selectedFields.has(field.key)" class="preview-tag preview-tag--custom">{{ field.icon }} {{ field.label }}</span>
          </template>
        </div>
        <div class="preview-footer">
          <span v-if="selectedFields.has('assignee')" class="preview-assignee">
            <span v-if="getFieldDisplayMode('assignee') === 'initial'" class="preview-avatar">�?/span>
            <span v-else class="preview-assignee-name">张三</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { customFieldApi } from '@/api'
import type { CustomFieldDefinitionVO } from '@/api/types'

interface FieldOption {
  key: string
  label: string
  icon: string
}

const props = defineProps<{
  visibleFields: string[]
  colorScheme: string
  projectId: string
  currentEstimationFieldId?: string | null
  originalEstimationFieldId?: string | null
  fieldDisplayModes?: Record<string, 'full_name' | 'initial'> | null
  showCustomFieldColors?: boolean
  /** 是否允许卡片分配到多个迭代（对应 YouTrack: Allow cards to be assigned to multiple sprints�?*/
  allowMultipleSprints?: boolean
  /** 项目是否有活�?Sprint（仅当有 Sprint 功能时才显示此选项�?*/
  hasActiveSprint?: boolean
}>()

const emit = defineEmits<{
  'update:visibleFields': [fields: string[]]
  'update:colorScheme': [scheme: string]
  'update:currentEstimationFieldId': [id: string | null]
  'update:originalEstimationFieldId': [id: string | null]
  'update:fieldDisplayModes': [modes: Record<string, 'full_name' | 'initial'> | null]
  'update:showCustomFieldColors': [value: boolean]
  'update:allowMultipleSprints': [value: boolean]
}>()

/** 内置字段（固定） */
const builtInFields: FieldOption[] = [
  { key: 'assignee', label: '负责�?, icon: '👤' },
  { key: 'priority', label: '优先�?, icon: '🔴' },
  { key: 'type', label: '类型', icon: '📋' },
  { key: 'tags', label: '标签', icon: '🏷�? },
  { key: 'dueDate', label: '截止日期', icon: '📅' },
  { key: 'sprint', label: 'Sprint', icon: '🏃' },
  { key: 'estimatedHours', label: '预估工时', icon: '�? }
]

/** 适合在卡片上展示的自定义字段格式（枚�?布尔/用户 等有限值类型） */
const CARD_VISUAL_FORMATS = new Set(['list', 'user', 'bool', 'date'])

/** 可选字段列表（内置 + 项目自定义字段） */
const availableFields = computed<FieldOption[]>(() => {
  return [...builtInFields, ...customFieldOptions.value]
})

/** 项目自定义字段选项列表（过滤出适合卡片展示的类型） */
const customFieldOptions = computed<FieldOption[]>(() => {
  return projectFields.value
    .filter(f => CARD_VISUAL_FORMATS.has(f.fieldFormat) && !f.isPrivate)
    .map(f => ({
      key: `cf.${f.id}`,
      label: f.name,
      icon: getCustomFieldIcon(f.fieldFormat)
    }))
})

/** 自定义字段格式对应图�?*/
function getCustomFieldIcon(format: string): string {
  const map: Record<string, string> = {
    list: '📋',
    user: '👤',
    bool: '�?,
    date: '📅'
  }
  return map[format] || '🔖'
}

const selectedFields = ref<Set<string>>(new Set(props.visibleFields))
const selectedColorScheme = ref(props.colorScheme)
const selectedCurrentEstimationFieldId = ref<string | undefined | null>(props.currentEstimationFieldId)
const selectedOriginalEstimationFieldId = ref<string | undefined | null>(props.originalEstimationFieldId)
const selectedFieldDisplayModes = ref<Record<string, 'full_name' | 'initial'>>(
  props.fieldDisplayModes ? { ...props.fieldDisplayModes } : {}
)

// 自定义字段颜色指示开关（默认 true�?const selectedShowCustomFieldColors = ref<boolean>(props.showCustomFieldColors !== false)

// 允许卡片分配到多个迭代开关（默认 false，向后兼容）
const selectedAllowMultipleSprints = ref<boolean>(props.allowMultipleSprints ?? false)

// 项目自定义字段列表（数值类型）
const projectFields = ref<CustomFieldDefinitionVO[]>([])
const fieldsLoading = ref(false)

const numericFields = computed(() =>
  projectFields.value.filter(f => f.fieldFormat === 'int' || f.fieldFormat === 'float')
)

function fieldFormatLabel(format: string): string {
  const map: Record<string, string> = {
    int: 'integer',
    float: 'float'
  }
  return map[format] || format
}

async function loadProjectFields() {
  if (!props.projectId) return
  fieldsLoading.value = true
  try {
    const res = await customFieldApi.listByProject(props.projectId)
    projectFields.value = res.data || []
  } catch {
    projectFields.value = []
  } finally {
    fieldsLoading.value = false
  }
}

// 加载项目字段
watch(() => props.projectId, (id) => {
  if (id) loadProjectFields()
}, { immediate: true })

// Sync from props when they change externally
watch(() => props.visibleFields, (newFields) => {
  selectedFields.value = new Set(newFields)
})

watch(() => props.colorScheme, (newScheme) => {
  selectedColorScheme.value = newScheme
})

watch(() => props.currentEstimationFieldId, (val) => {
  selectedCurrentEstimationFieldId.value = val
})

watch(() => props.originalEstimationFieldId, (val) => {
  selectedOriginalEstimationFieldId.value = val
})

watch(() => props.fieldDisplayModes, (val) => {
  selectedFieldDisplayModes.value = val ? { ...val } : {}
})

watch(() => props.showCustomFieldColors, (val) => {
  selectedShowCustomFieldColors.value = val !== false
})

watch(() => props.allowMultipleSprints, (val) => {
  selectedAllowMultipleSprints.value = val ?? false
})

// Emit changes
watch(selectedColorScheme, (scheme) => {
  emit('update:colorScheme', scheme)
})

function toggleField(key: string, checked: boolean) {
  const newSet = new Set(selectedFields.value)
  if (checked) {
    newSet.add(key)
  } else {
    // Prevent deselecting all fields
    if (newSet.size <= 1) return
    newSet.delete(key)
  }
  selectedFields.value = newSet
  emit('update:visibleFields', Array.from(newSet))
}

/** 获取字段的显示模式，未配置时默认 full_name */
function getFieldDisplayMode(fieldKey: string): 'full_name' | 'initial' {
  return selectedFieldDisplayModes.value[fieldKey] ?? 'full_name'
}

/** 设置字段的显示模式并触发更新 */
function setFieldDisplayMode(fieldKey: string, mode: 'full_name' | 'initial') {
  const newModes = { ...selectedFieldDisplayModes.value }
  if (mode === 'full_name') {
    // full_name 是默认值，可以直接删除 key 以节省存�?    delete newModes[fieldKey]
  } else {
    newModes[fieldKey] = mode
  }
  selectedFieldDisplayModes.value = newModes
  emit('update:fieldDisplayModes', Object.keys(newModes).length > 0 ? newModes : null)
}

function onCurrentEstimationFieldChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  emit('update:currentEstimationFieldId', (val as string) ?? null)
}

function onOriginalEstimationFieldChange(val: string | number | boolean | Record<string, any> | (string | number | boolean | Record<string, any>)[] | undefined) {
  emit('update:originalEstimationFieldId', (val as string) ?? null)
}

function onShowCustomFieldColorsChange(val: boolean) {
  emit('update:showCustomFieldColors', val)
}

function onAllowMultipleSprintsChange(val: boolean) {
  selectedAllowMultipleSprints.value = val
  emit('update:allowMultipleSprints', val)
}

const previewColorClass = computed(() => {
  if (selectedColorScheme.value === 'priority') return 'card-preview--priority'
  if (selectedColorScheme.value === 'type') return 'card-preview--type'
  if (selectedColorScheme.value === 'project') return 'card-preview--project'
  return ''
})
</script>

<style scoped>
.cards-settings {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-hint p {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0;
  line-height: 1.5;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.section-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

/* ===== 字段列表 ===== */
.field-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.field-item:hover {
  background: var(--color-fill-1);
}

.field-item--checked {
  background: rgba(var(--primary-6), 0.04);
}

.field-item + .field-item {
  border-top: 1px solid var(--color-border-light, var(--color-border));
}

.field-group-divider {
  display: flex;
  align-items: center;
  padding: 6px 14px;
  background: var(--color-fill-1);
  border-top: 1px solid var(--color-border-light, var(--color-border));
}

.field-group-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.field-group-divider + .field-item {
  border-top: none;
}

.field-icon {
  font-size: 14px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

.field-label {
  font-size: 13px;
  color: var(--color-text-1);
  flex: 1;
}

/* ===== 字段显示模式切换 ===== */
.field-display-mode {
  margin-left: auto;
  flex-shrink: 0;
}

.field-display-mode :deep(.arco-radio-group-button .arco-radio-button) {
  padding: 0 7px;
  height: 22px;
  line-height: 22px;
  font-size: 11px;
}

.field-display-mode-placeholder {
  width: 128px;
  height: 22px;
  flex-shrink: 0;
  margin-left: auto;
}

/* ===== 估算字段配置 ===== */
.estimation-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 12px 14px;
}

.estimation-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.estimation-field-label {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.estimation-field-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.estimation-field-hint {
  font-size: 11px;
  color: var(--color-text-3);
}

.estimation-field-select {
  width: 180px;
  flex-shrink: 0;
}

.field-format-badge {
  font-size: 10px;
  color: var(--color-text-4);
  background: var(--color-fill-2);
  padding: 1px 5px;
  border-radius: 3px;
  margin-left: 6px;
}

.estimation-fields-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-3);
  line-height: 1.5;
}

.hint-icon {
  font-size: 13px;
  flex-shrink: 0;
  margin-top: 1px;
}

/* ===== 颜色方案 ===== */
.color-scheme-group {
  gap: 4px !important;
}

.color-scheme-group :deep(.arco-radio) {
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  transition: border-color 0.15s, background 0.15s;
  width: 100%;
}

.color-scheme-group :deep(.arco-radio:hover) {
  background: var(--color-fill-1);
}

.color-scheme-group :deep(.arco-radio-checked) {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.04);
}

.scheme-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.scheme-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.scheme-desc {
  font-size: 11px;
  color: var(--color-text-3);
}

/* ===== 卡片预览 ===== */
.card-preview {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  max-width: 260px;
  transition: border-left-color 0.2s;
}

.card-preview--priority {
  border-left: 3px solid var(--tf-warning);
}

.card-preview--type {
  border-left: 3px solid var(--tf-accent);

.card-preview--project {
  border-left: 3px solid var(--tf-text-link);
}
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.preview-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.preview-priority {
  font-size: 10px;
}

.preview-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  margin-bottom: 8px;
}

.preview-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.preview-tag {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.preview-tag--accent {
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.08);
}

.preview-tag--estimation {
  color: rgb(var(--success-6));
  background: rgba(var(--success-6), 0.08);
}

.preview-tag--custom {
  color: var(--color-text-2);
  background: var(--color-fill-3);
}

.preview-footer {
  display: flex;
  justify-content: flex-end;
}

.preview-assignee {
  display: flex;
  align-items: center;
}

.preview-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgb(var(--primary-6));
  color: var(--tf-text-on-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-assignee-name {
  font-size: 11px;
  color: var(--color-text-2);
}
</style>

<style scoped>
.cards-settings {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-hint p {
  font-size: 13px;
  color: var(--color-text-3);
  margin: 0;
  line-height: 1.5;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.section-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}

/* ===== 字段列表 ===== */
.field-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}

.field-item:hover {
  background: var(--color-fill-1);
}

.field-item--checked {
  background: rgba(var(--primary-6), 0.04);
}

.field-item + .field-item {
  border-top: 1px solid var(--color-border-light, var(--color-border));
}

.field-icon {
  font-size: 14px;
  width: 20px;
  text-align: center;
  flex-shrink: 0;
}

.field-label {
  font-size: 13px;
  color: var(--color-text-1);
}

/* ===== 颜色方案 ===== */
.color-scheme-group {
  gap: 4px !important;
}

.color-scheme-group :deep(.arco-radio) {
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  transition: border-color 0.15s, background 0.15s;
  width: 100%;
}

.color-scheme-group :deep(.arco-radio:hover) {
  background: var(--color-fill-1);
}

.color-scheme-group :deep(.arco-radio-checked) {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.04);
}

.scheme-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.scheme-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.scheme-desc {
  font-size: 11px;
  color: var(--color-text-3);
}

/* ===== 卡片预览 ===== */
.card-preview {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  max-width: 260px;
  transition: border-left-color 0.2s;
}

.card-preview--priority {
  border-left: 3px solid #f59e0b;
}

.card-preview--type {
  border-left: 3px solid #6366f1;
}


.card-preview--project {
  border-left: 3px solid #0ea5e9;
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.preview-key {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-3);
}

.preview-priority {
  font-size: 10px;
}

.preview-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  line-height: 1.4;
  margin-bottom: 8px;
}

.preview-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.preview-tag {
  font-size: 10px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  padding: 2px 6px;
  border-radius: 3px;
}

.preview-tag--accent {
  color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.08);
}

.preview-footer {
  display: flex;
  justify-content: flex-end;
}

.preview-assignee {
  display: flex;
  align-items: center;
}

.preview-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgb(var(--primary-6));
  color: var(--tf-text-on-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== 设置行（开关类单行设置�?===== */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.setting-row-label {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}

.setting-row-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.setting-row-hint {
  font-size: 11px;
  color: var(--color-text-3);
  line-height: 1.5;
}
</style>
