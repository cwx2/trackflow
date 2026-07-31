<template>
  <div class="custom-node condition-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔀</span>
      <span class="node-title">{{ data.label || '条件判断' }}</span>
    </div>
    <div class="node-body">
      <div class="condition-preview">{{ conditionPreview }}</div>
    </div>
    <!-- 连接点：1个输入，2个输出（True/False） -->
    <Handle type="target" :position="Position.Left" class="handle-left" />
    <Handle id="true" type="source" :position="Position.Right" :style="{ top: '30%' }" class="handle-true">
      <span class="handle-label handle-label-true">✓</span>
    </Handle>
    <Handle id="false" type="source" :position="Position.Right" :style="{ top: '70%' }" class="handle-false">
      <span class="handle-label handle-label-false">✗</span>
    </Handle>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: Record<string, any>
  selected?: boolean
}>()

// 运算符显示映射
const operatorLabels: Record<string, string> = {
  'contains': '包含',
  'not_contains': '不包含',
  'equals': '等于',
  'not_equals': '不等于',
  'is_empty': '为空',
  'is_not_empty': '不为空'
}

// 生成条件预览文本
const conditionPreview = computed(() => {
  const { variable, operator, value } = props.data
  if (!variable || !operator) return '未配置条件'
  
  const opLabel = operatorLabels[operator] || operator
  
  // 为空/不为空运算符不需要比较值
  if (operator === 'is_empty' || operator === 'is_not_empty') {
    return `${variable} ${opLabel}`
  }
  
  const displayValue = value ? `"${value}"` : '""'
  return `${variable} ${opLabel} ${displayValue}`
})
</script>

<style scoped>
.custom-node {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  border-radius: 8px;
  min-width: 180px;
  font-size: 13px;
  transition: all 0.15s;
  position: relative;
}

.condition-node {
  border-color: var(--color-warning-light-4, #ffc53d);
}

.custom-node.selected {
  border-color: var(--tf-accent);
  box-shadow: 0 0 0 2px var(--tf-accent-dim, rgba(88, 166, 255, 0.2));
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-warning-light-1, rgba(255, 197, 61, 0.1));
  border-bottom: 1px solid var(--tf-border);
  border-radius: 6px 6px 0 0;
}

.node-icon {
  font-size: 16px;
}

.node-title {
  font-weight: 600;
  color: var(--tf-text-primary);
}

.node-body {
  padding: 10px 12px;
}

.condition-preview {
  color: var(--tf-text-secondary);
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}

/* 连接点样式 */
.handle-left {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}

.handle-true,
.handle-false {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  width: 12px;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.handle-true {
  border-color: var(--color-success-light-4, #52c41a);
}

.handle-false {
  border-color: var(--color-danger-light-4, #f5222d);
}

/* Handle 标签 */
.handle-label {
  position: absolute;
  right: 16px;
  font-size: 10px;
  font-weight: 600;
  pointer-events: none;
}

.handle-label-true {
  color: var(--color-success-light-4, #52c41a);
}

.handle-label-false {
  color: var(--color-danger-light-4, #f5222d);
}
</style>
