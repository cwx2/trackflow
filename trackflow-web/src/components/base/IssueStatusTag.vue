<template>
  <span class="issue-status-tag" :class="sizeClass" :style="tagStyle">
    <span v-if="showDot" class="status-dot" :style="{ background: resolvedColor }"></span>
    <span class="status-label">{{ name }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 状态标签组件 - 统一所有场景的状态 tag 渲染
 *
 * 支持：
 * - 工单状态（带动态颜色）
 * - 用户状态（active/disabled）
 * - Sprint 状态
 * - 任何需要 "圆点 + 文字" 或 "彩色背景标签" 的场景
 */

const props = withDefaults(defineProps<{
  /** 显示文字 */
  name: string
  /** 标签颜色（十六进制） */
  color?: string
  /** 尺寸，默认 small */
  size?: 'small' | 'medium'
  /** 是否显示圆点，默认 true */
  showDot?: boolean
  /** 展示风格：pill 带背景 | plain 仅文字+点 */
  variant?: 'pill' | 'plain'
}>(), {
  size: 'small',
  showDot: true,
  variant: 'pill',
})

const resolvedColor = computed(() => props.color || '#6b7280')

const sizeClass = computed(() => `size-${props.size}`)

const tagStyle = computed(() => {
  if (props.variant === 'plain') {
    return { color: resolvedColor.value }
  }
  // pill variant: 带浅色背景
  return {
    background: resolvedColor.value + '20',
    color: resolvedColor.value,
  }
})
</script>

<style scoped>
.issue-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 3px;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
}

.issue-status-tag.size-small {
  padding: 2px 6px;
  font-size: 10px;
}

.issue-status-tag.size-medium {
  padding: 2px 8px;
  font-size: 12px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.issue-status-tag.size-medium .status-dot {
  width: 8px;
  height: 8px;
}

.status-label {
  line-height: 1.4;
}
</style>
