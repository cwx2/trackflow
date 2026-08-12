<template>
  <!--
    FilterSelect — 工具栏筛选器 pill 组件
    将 label + select 封装成统一样式的控件：[ 标签  选中值 ▼ ]
    用于 AdminDataTable #toolbar-filters slot 内。
  -->
  <div class="filter-select" :class="{ 'filter-select--active': hasValue }">
    <span class="filter-select__label">{{ label }}</span>
    <a-select
      :model-value="modelValue ?? undefined"
      :placeholder="placeholder || '全部'"
      size="small"
      :allow-clear="allowClear"
      :style="{ minWidth: width ? (typeof width === 'number' ? `${width}px` : width) : '80px' }"
      class="filter-select__input"
      v-bind="$attrs"
      @update:model-value="$emit('update:modelValue', $event as any)"
      @change="$emit('change', $event as any)"
      @clear="$emit('clear')"
    >
      <slot />
    </a-select>
  </div>
</template>

<script setup lang="ts">
/**
 * FilterSelect — 工具栏筛选 pill 组件
 *
 * 用法：
 * <FilterSelect label="角色类型" v-model="filters.roleType" @change="reload">
 *   <a-option value="global">全局</a-option>
 *   <a-option value="project">项目级</a-option>
 * </FilterSelect>
 */
import { computed } from 'vue'

defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<{
  /** 左侧标签文字 */
  label: string
  /** 当前选中值（v-model） */
  modelValue?: string | number | null
  /** select 占位符，默认"全部" */
  placeholder?: string
  /** 是否允许清空，默认 true */
  allowClear?: boolean
  /** select 最小宽度 */
  width?: string | number
}>(), {
  allowClear: true,
})

defineEmits<{
  'update:modelValue': [value: string | number | null | undefined]
  'change': [value: string | number | null | undefined]
  'clear': []
}>()

const hasValue = computed(() =>
  props.modelValue !== null &&
  props.modelValue !== undefined &&
  props.modelValue !== ''
)
</script>

<style scoped>
.filter-select {
  display: inline-flex;
  align-items: center;
  background: var(--tf-bg-body);
  border: 1px solid var(--tf-border-light);
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 150ms;
  height: 28px;
}

.filter-select:hover {
  border-color: var(--tf-border);
}

.filter-select--active {
  border-color: var(--tf-accent);
}

/* ===== 左侧标签 ===== */
.filter-select__label {
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--tf-text-secondary);
  white-space: nowrap;
  border-right: 1px solid var(--tf-border-light);
  height: 100%;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  background: var(--tf-bg-surface);
}

/* ===== 右侧 select：去掉 Arco 默认边框融入 pill ===== */
.filter-select__input :deep(.arco-select-view) {
  border: none !important;
  border-radius: 0 !important;
  background: transparent !important;
  height: 26px;
  min-height: 26px;
  box-shadow: none !important;
}

.filter-select__input :deep(.arco-select-view-single) {
  padding: 0 8px;
  font-size: 12px;
}

.filter-select__input :deep(.arco-select-view:hover) {
  background: var(--tf-bg-elevated) !important;
}

.filter-select__input :deep(.arco-select-view-value) {
  color: var(--tf-text-primary);
  font-size: 12px;
}

.filter-select__input :deep(.arco-select-view-suffix) {
  color: var(--tf-text-tertiary);
}
</style>
