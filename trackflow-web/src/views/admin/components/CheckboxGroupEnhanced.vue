<template>
  <div class="checkbox-group-enhanced">
    <!-- 操作栏 -->
    <div class="cge-toolbar">
      <div class="cge-actions">
        <a-button type="text" size="mini" @click="selectAll">全选</a-button>
        <a-button type="text" size="mini" @click="clearAll" :disabled="modelValue.length === 0">清空</a-button>
      </div>
      <span class="cge-count">已选 {{ modelValue.length }}/{{ options.length }}</span>
    </div>

    <!-- 搜索框（项目数超过阈值时显示） -->
    <div v-if="options.length > searchThreshold" class="cge-search">
      <a-input
        v-model="searchText"
        placeholder="搜索..."
        size="mini"
        allow-clear
      >
        <template #prefix>
          <icon-search />
        </template>
      </a-input>
    </div>

    <!-- checkbox 列表 -->
    <div class="cge-list" :class="{ 'cge-list-scrollable': options.length > scrollThreshold }">
      <div
        v-for="opt in filteredOptions"
        :key="opt.value"
        class="cge-item"
        @click="toggleOption(opt.value)"
      >
        <a-checkbox
          :model-value="modelValue.includes(opt.value)"
          @change="(checked: boolean | (string | number | boolean)[]) => handleCheck(opt.value, checked as boolean)"
          @click.stop
        >
          <span class="cge-label">
            <span v-if="opt.extra" class="cge-label-extra">{{ opt.extra }}</span>
            <span>{{ opt.label }}</span>
          </span>
        </a-checkbox>
      </div>

      <!-- 搜索无结果 -->
      <div v-if="filteredOptions.length === 0 && searchText" class="cge-empty">
        无匹配项
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { IconSearch } from '@arco-design/web-vue/es/icon'

export interface CheckboxOption {
  value: string
  label: string
  /** 额外展示信息（如项目 Key），显示在 label 前 */
  extra?: string
  /** 用于搜索匹配的额外关键词 */
  searchKeywords?: string[]
}

const props = withDefaults(defineProps<{
  modelValue: string[]
  options: CheckboxOption[]
  /** 超过此数量显示搜索框，默认 8 */
  searchThreshold?: number
  /** 超过此数量列表区域启用滚动，默认 10 */
  scrollThreshold?: number
}>(), {
  searchThreshold: 8,
  scrollThreshold: 10
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const searchText = ref('')

const filteredOptions = computed(() => {
  if (!searchText.value.trim()) return props.options
  const keyword = searchText.value.trim().toLowerCase()
  return props.options.filter(opt => {
    if (opt.label.toLowerCase().includes(keyword)) return true
    if (opt.extra && opt.extra.toLowerCase().includes(keyword)) return true
    if (opt.searchKeywords?.some(kw => kw.toLowerCase().includes(keyword))) return true
    return false
  })
})

function selectAll() {
  // 选中当前过滤后显示的所有项（如果有搜索则只选搜索结果）
  const filteredValues = new Set(filteredOptions.value.map(o => o.value))
  const currentSet = new Set(props.modelValue)
  filteredValues.forEach(v => currentSet.add(v))
  emit('update:modelValue', [...currentSet])
}

function clearAll() {
  if (searchText.value.trim()) {
    // 有搜索时，只清除搜索结果中的项
    const filteredValues = new Set(filteredOptions.value.map(o => o.value))
    emit('update:modelValue', props.modelValue.filter(v => !filteredValues.has(v)))
  } else {
    emit('update:modelValue', [])
  }
}

function toggleOption(value: string) {
  const idx = props.modelValue.indexOf(value)
  if (idx >= 0) {
    const next = [...props.modelValue]
    next.splice(idx, 1)
    emit('update:modelValue', next)
  } else {
    emit('update:modelValue', [...props.modelValue, value])
  }
}

function handleCheck(value: string, checked: boolean) {
  if (checked) {
    emit('update:modelValue', [...props.modelValue, value])
  } else {
    emit('update:modelValue', props.modelValue.filter(v => v !== value))
  }
}
</script>

<style scoped>
.checkbox-group-enhanced {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--tf-border);
  border-radius: 6px;
  padding: 8px;
  background: var(--tf-bg-body);
}

.cge-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--tf-border);
}

.cge-actions {
  display: flex;
  gap: 4px;
}

.cge-actions :deep(.arco-btn-text) {
  font-size: 12px;
  padding: 2px 6px;
  height: 22px;
  color: var(--tf-accent);
}

.cge-actions :deep(.arco-btn-text:disabled) {
  color: var(--tf-text-quaternary);
}

.cge-count {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}

.cge-search {
  padding: 0 2px;
}

.cge-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cge-list-scrollable {
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
}

.cge-item {
  display: flex;
  align-items: center;
  padding: 4px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 100ms;
}

.cge-item:hover {
  background: var(--tf-bg-hover);
}

.cge-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--tf-text-primary);
}

.cge-label-extra {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  font-weight: 500;
  padding: 1px 4px;
  background: var(--tf-bg-surface);
  border-radius: 3px;
}

.cge-empty {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  text-align: center;
  padding: 12px 0;
}
</style>
