<template>
  <span v-if="mode === 'dot'" class="priority-badge-dot" :class="sizeClass">
    <span class="dot" :style="{ background: resolvedColor }"></span>
    <span v-if="showLabel" class="dot-label">{{ displayLabel }}</span>
  </span>
  <span v-else-if="mode === 'block'" class="priority-badge-block" :class="sizeClass" :style="{ backgroundColor: resolvedColor }" :title="displayLabel">
    {{ priorityOrder }}
  </span>
  <span v-else class="priority-badge-text" :class="sizeClass" :style="{ color: resolvedColor }">
    {{ displayLabel }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getPriorityColor } from '@/views/issue/composables/usePriorityOptions'

/**
 * 优先级展示组件 - 统一所有场景的优先级 badge 渲染
 *
 * 支持三种模式：
 * - dot: 小圆点 + 可选文字标签（列表行内使用）
 * - block: 彩色数字方块（关联工单列表、子工单列表使用）
 * - text: 纯文字带颜色（极简场景）
 */

const props = withDefaults(defineProps<{
  /** 优先级名称（数据库中存储的值，如 "普通"、"高" 等） */
  priority: string | null | undefined
  /** 颜色（可选，不传则从全局颜色映射获取） */
  color?: string
  /** 展示模式：dot 圆点 | block 数字方块 | text 纯文字 */
  mode?: 'dot' | 'block' | 'text'
  /** 是否显示文字标签（mode="dot" 时生效），默认 false */
  showLabel?: boolean
  /** 尺寸，默认 small */
  size?: 'small' | 'medium'
  /** 项目 ID（可选，传入可从缓存获取更精确的颜色） */
  projectId?: string
}>(), {
  mode: 'dot',
  showLabel: false,
  size: 'small',
})

/** 中文标签映射 */
const LABELS: Record<string, string> = {
  'Show-stopper': '阻塞',
  'Critical': '紧急',
  'High': '高',
  'Medium': '普通',
  'Normal': '普通',
  'Low': '低',
  '阻塞': '阻塞',
  '紧急': '紧急',
  '高': '高',
  '普通': '普通',
  '低': '低',
}

/** 优先级序号映射（用于 block 模式显示数字） */
const PRIORITY_ORDER: Record<string, number> = {
  '阻塞': 1,
  '紧急': 2,
  '高': 3,
  '普通': 4,
  '低': 5,
  'Show-stopper': 1,
  'Critical': 2,
  'High': 3,
  'Normal': 4,
  'Medium': 4,
  'Low': 5,
}

const resolvedColor = computed(() => {
  if (props.color) return props.color
  return getPriorityColor(props.priority, props.projectId)
})

const displayLabel = computed(() => {
  if (!props.priority) return '普通'
  return LABELS[props.priority] || props.priority
})

const priorityOrder = computed(() => {
  if (!props.priority) return 4
  return PRIORITY_ORDER[props.priority] ?? 4
})

const sizeClass = computed(() => `size-${props.size}`)
</script>

<style scoped>
/* ===== Dot 模式（圆点 + 可选文字） ===== */
.priority-badge-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.priority-badge-dot .dot {
  display: inline-block;
  border-radius: 2px;
  flex-shrink: 0;
}

.priority-badge-dot.size-small .dot {
  width: 10px;
  height: 10px;
  min-width: 10px;
  min-height: 10px;
}

.priority-badge-dot.size-medium .dot {
  width: 12px;
  height: 12px;
  min-width: 12px;
  min-height: 12px;
}

.priority-badge-dot .dot-label {
  font-size: 12px;
  color: var(--tf-text-secondary, var(--color-text-2));
  white-space: nowrap;
}

.priority-badge-dot.size-medium .dot-label {
  font-size: 13px;
}

/* ===== Block 模式（彩色数字方块） ===== */
.priority-badge-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}

.priority-badge-block.size-small {
  width: 16px;
  height: 16px;
  font-size: 11px;
}

.priority-badge-block.size-medium {
  width: 20px;
  height: 20px;
  font-size: 12px;
}

/* ===== Text 模式（纯文字带颜色） ===== */
.priority-badge-text {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
  flex-shrink: 0;
  white-space: nowrap;
}

.priority-badge-text.size-small {
  font-size: 11px;
}

.priority-badge-text.size-medium {
  font-size: 13px;
}
</style>
