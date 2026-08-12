<template>
  <div class="admin-toolbar">
    <!-- 左侧：搜索框 -->
    <a-input-search
      :model-value="modelValue"
      :placeholder="searchPlaceholder || '搜索...'"
      size="small"
      allow-clear
      :style="{ width: typeof searchWidth === 'number' ? `${searchWidth}px` : (searchWidth || '280px') }"
      @input="modelValue = $event"
      @search="$emit('search', $event)"
      @clear="$emit('clear')"
    />

    <!-- 中部：筛选器 slot -->
    <div v-if="$slots.default" class="admin-toolbar-filters">
      <slot />
    </div>

    <!-- 重置按钮（有筛选器时显示） -->
    <a-button
      v-if="$slots.default && showReset"
      size="small"
      type="secondary"
      @click="$emit('reset')"
    >
      重置
    </a-button>

    <!-- 弹性间隔 -->
    <div class="admin-toolbar-spacer" />

    <!-- 批量操作区（有选中行时显示） -->
    <Transition name="batch-fade">
      <div v-if="selectedCount && selectedCount > 0" class="admin-toolbar-batch">
        <slot name="batch-actions" :count="selectedCount" />
      </div>
    </Transition>

    <!-- 右侧：自定义 + 图标操作按钮组 -->
    <div v-if="$slots.right || showRefresh" class="admin-toolbar-right">
      <slot name="right" />

      <!-- 分隔线 -->
      <div v-if="showRefresh" class="toolbar-divider" />

      <!-- 刷新按钮 -->
      <a-tooltip v-if="showRefresh" content="刷新">
        <a-button
          size="small"
          type="secondary"
          class="toolbar-icon-btn"
          :loading="refreshLoading"
          @click="$emit('refresh')"
        >
          <template #icon><icon-refresh /></template>
        </a-button>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
const modelValue = defineModel<string>({ default: '' })

withDefaults(defineProps<{
  /** 搜索框占位符 */
  searchPlaceholder?: string
  /** 搜索框最大宽度，默认 280px */
  searchWidth?: string | number
  /** 已选中的行数（>0 时显示批量操作区） */
  selectedCount?: number
  /** 是否显示内置刷新按钮（默认 false） */
  showRefresh?: boolean
  /** 刷新按钮是否 loading */
  refreshLoading?: boolean
  /** 是否显示重置按钮（有筛选器时默认 true） */
  showReset?: boolean
}>(), {
  searchPlaceholder: '搜索...',
  searchWidth: '280px',
  selectedCount: 0,
  showRefresh: false,
  refreshLoading: false,
  showReset: true,
})

defineEmits<{
  'search': [value: string]
  'clear': []
  'refresh': []
  'reset': []
}>()
</script>

<style scoped>
.admin-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  flex-shrink: 0;
  flex-wrap: wrap;
  min-height: 52px;
  background: var(--tf-bg-surface);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
}

.admin-toolbar-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.admin-toolbar-spacer {
  flex: 1;
}

.admin-toolbar-batch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.admin-toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.toolbar-divider {
  width: 1px;
  height: 16px;
  background: var(--tf-border-light);
  flex-shrink: 0;
}

.toolbar-icon-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 批量操作区淡入动画 */
.batch-fade-enter-active,
.batch-fade-leave-active {
  transition: opacity 200ms, transform 200ms;
}
.batch-fade-enter-from,
.batch-fade-leave-to {
  opacity: 0;
  transform: translateX(8px);
}
</style>

