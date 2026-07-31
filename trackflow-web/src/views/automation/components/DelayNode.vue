<template>
  <div class="custom-node delay-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">⏱</span>
      <span class="node-title">{{ data.label || '延时等待' }}</span>
    </div>
    <div class="node-body">
      <div class="delay-preview">{{ delayPreview }}</div>
    </div>
    <!-- 连接点：1个输入（左侧），1个输出（右侧） -->
    <Handle type="target" :position="Position.Left" class="handle-left" />
    <Handle type="source" :position="Position.Right" class="handle-right" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: Record<string, any>
  selected?: boolean
}>()

// 生成等待时长预览文本
const delayPreview = computed(() => {
  const { mode, seconds, variable } = props.data
  if (mode === 'variable' && variable) {
    return `等待 ${variable} 秒`
  }
  const secs = seconds ?? 15
  return `等待 ${secs} 秒`
})
</script>

<style scoped>
.custom-node {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  border-radius: 8px;
  min-width: 160px;
  font-size: 13px;
  transition: all 0.15s;
  position: relative;
}

.delay-node {
  /* 使用次要色调（灰紫/neutral）区分于其他节点 */
  border-color: var(--color-neutral-6, #6b7280);
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
  background: var(--color-neutral-1, rgba(107, 114, 128, 0.1));
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

.delay-preview {
  color: var(--tf-text-secondary);
  font-family: monospace;
  font-size: 12px;
}

/* 连接点样式 */
.handle-left,
.handle-right {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}
</style>
