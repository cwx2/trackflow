<template>
  <div class="cards-settings">
    <div class="settings-hint">
      <p>配置看板卡片上展示的字段和颜色方案。修改后实时生效。</p>
    </div>

    <!-- 卡片字段选择 -->
    <div class="section">
      <div class="section-title">显示字段</div>
      <div class="section-desc">选择看板卡片上要展示的信息字段</div>
      <div class="field-list">
        <label
          v-for="field in availableFields"
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
        </label>
      </div>
    </div>

    <!-- 颜色方案 -->
    <div class="section">
      <div class="section-title">颜色方案</div>
      <div class="section-desc">为卡片添加颜色指示，快速区分不同类别的工单</div>
      <a-radio-group v-model="selectedColorScheme" direction="vertical" class="color-scheme-group">
        <a-radio value="none">
          <div class="scheme-option">
            <span class="scheme-name">无着色</span>
            <span class="scheme-desc">卡片不添加额外颜色</span>
          </div>
        </a-radio>
        <a-radio value="priority">
          <div class="scheme-option">
            <span class="scheme-name">按优先级着色</span>
            <span class="scheme-desc">卡片左侧边框显示优先级对应颜色</span>
          </div>
        </a-radio>
        <a-radio value="type">
          <div class="scheme-option">
            <span class="scheme-name">按类型着色</span>
            <span class="scheme-desc">卡片左侧边框显示工单类型对应颜色</span>
          </div>
        </a-radio>
      </a-radio-group>
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
          <span v-if="selectedFields.has('estimatedHours')" class="preview-tag">⏱ 4h</span>
        </div>
        <div class="preview-footer">
          <span v-if="selectedFields.has('assignee')" class="preview-assignee">
            <span class="preview-avatar">张</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'

interface FieldOption {
  key: string
  label: string
  icon: string
}

const props = defineProps<{
  visibleFields: string[]
  colorScheme: string
}>()

const emit = defineEmits<{
  'update:visibleFields': [fields: string[]]
  'update:colorScheme': [scheme: string]
}>()

const availableFields: FieldOption[] = [
  { key: 'assignee', label: '负责人', icon: '👤' },
  { key: 'priority', label: '优先级', icon: '🔴' },
  { key: 'type', label: '类型', icon: '📋' },
  { key: 'tags', label: '标签', icon: '🏷️' },
  { key: 'dueDate', label: '截止日期', icon: '📅' },
  { key: 'sprint', label: 'Sprint', icon: '🏃' },
  { key: 'estimatedHours', label: '预估工时', icon: '⏱' }
]

const selectedFields = ref<Set<string>>(new Set(props.visibleFields))
const selectedColorScheme = ref(props.colorScheme)

// Sync from props when they change externally
watch(() => props.visibleFields, (newFields) => {
  selectedFields.value = new Set(newFields)
})

watch(() => props.colorScheme, (newScheme) => {
  selectedColorScheme.value = newScheme
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

const previewColorClass = computed(() => {
  if (selectedColorScheme.value === 'priority') return 'card-preview--priority'
  if (selectedColorScheme.value === 'type') return 'card-preview--type'
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
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
