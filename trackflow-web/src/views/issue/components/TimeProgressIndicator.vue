<template>
  <a-tooltip :content="tooltipText" position="top" mini>
    <!-- 保留 SVG：自绘圆弧进度指示器，Arco 无等效组件可替代 -->
    <svg
      class="time-progress-indicator"
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
    >
      <!-- Background circle (remaining / empty) -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        :fill="bgColor"
        :stroke="strokeColor"
        :stroke-width="strokeWidth"
      />
      <!-- Progress arc (spent time) -->
      <path
        v-if="ratio > 0 && ratio < 1"
        :d="arcPath"
        :fill="progressColor"
      />
      <!-- Full circle when >= 100% (overdue) -->
      <circle
        v-if="ratio >= 1"
        :cx="center"
        :cy="center"
        :r="radius"
        :fill="overdueColor"
        :stroke="overdueStroke"
        :stroke-width="strokeWidth"
      />
    </svg>
  </a-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** Spent time in hours */
  spent: number
  /** Estimated time in hours */
  estimated: number
  /** SVG size in px */
  size?: number
}>(), {
  size: 14,
})

const center = computed(() => props.size / 2)
const radius = computed(() => (props.size / 2) - 1)
const strokeWidth = 0.5

/** Ratio of spent/estimated, capped at display purposes */
const ratio = computed(() => {
  if (props.estimated <= 0) return 0
  return props.spent / props.estimated
})

const isOverdue = computed(() => ratio.value >= 1)

// Colors following the design system
const progressColor = computed(() => isOverdue.value ? 'var(--tf-error, #f85149)' : 'var(--tf-success, #3fb950)')
const overdueColor = 'var(--tf-error, #f85149)'
const overdueStroke = 'var(--tf-error, #f85149)'
const bgColor = computed(() => isOverdue.value ? overdueColor : 'var(--tf-bg-hover, rgba(255,255,255,0.06))')
const strokeColor = computed(() => isOverdue.value ? overdueStroke : 'var(--tf-border-light, rgba(255,255,255,0.1))')

/** SVG arc path for the spent portion (clockwise from 12 o'clock) */
const arcPath = computed(() => {
  if (ratio.value <= 0) return ''
  if (ratio.value >= 1) return '' // full circle handled by the <circle> element

  const angle = ratio.value * 360
  const startAngle = -90 // start from top (12 o'clock)
  const endAngle = startAngle + angle

  const startRad = (startAngle * Math.PI) / 180
  const endRad = (endAngle * Math.PI) / 180

  const cx = center.value
  const cy = center.value
  const r = radius.value

  const x1 = cx + r * Math.cos(startRad)
  const y1 = cy + r * Math.sin(startRad)
  const x2 = cx + r * Math.cos(endRad)
  const y2 = cy + r * Math.sin(endRad)

  const largeArcFlag = angle > 180 ? 1 : 0

  return `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${largeArcFlag} 1 ${x2} ${y2} Z`
})

/** Tooltip text */
const tooltipText = computed(() => {
  const spentStr = formatHours(props.spent)
  const estStr = formatHours(props.estimated)
  if (isOverdue.value) {
    const overAmount = props.spent - props.estimated
    return `已花 ${spentStr} / 预估 ${estStr}，超出 ${formatHours(overAmount)}`
  }
  const remaining = props.estimated - props.spent
  return `已花 ${spentStr} / 预估 ${estStr}，剩余 ${formatHours(remaining)}`
})

function formatHours(h: number): string {
  if (h <= 0) return '0h'
  if (h < 1) return `${Math.round(h * 60)}m`
  if (Number.isInteger(h)) return `${h}h`
  return `${h.toFixed(1)}h`
}
</script>

<style scoped>
.time-progress-indicator {
  flex-shrink: 0;
  vertical-align: middle;
}
</style>
