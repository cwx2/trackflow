<template>
  <div class="custom-node file-input-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📁</span>
      <span class="node-title">{{ data.label || '文件输入' }}</span>
    </div>
    <div class="node-body">
      <div class="node-field path-field">{{ truncatePath(data.filePath) }}</div>
      <div class="node-field output-field">
        <span class="output-arrow">→</span>
        <span class="output-var">{{'{'}}{{ data.outputVar || 'file_content' }}{{'}'}}</span>
      </div>
    </div>
    <!-- 只有输出连接点（作为流程起点，无输入） -->
    <Handle type="source" :position="Position.Right" class="handle-right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: Record<string, any>
  selected?: boolean
}>()

function truncatePath(path: string): string {
  if (!path) return '{workspace}/'
  if (path.length <= 28) return path
  // 保留开头和结尾，中间用 ... 省略
  const prefix = path.slice(0, 12)
  const suffix = path.slice(-12)
  return `${prefix}...${suffix}`
}
</script>

<style scoped>
.custom-node {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  border-radius: 8px;
  min-width: 180px;
  font-size: 13px;
  transition: all 0.15s;
}

.custom-node.selected {
  border-color: var(--tf-accent);
  box-shadow: 0 0 0 2px var(--tf-accent-dim, rgba(88, 166, 255, 0.2));
}

/* 文件输入节点使用 success 色调（绿色）区分 */
.file-input-node .node-header {
  background: var(--color-success-light-4, rgba(59, 179, 70, 0.15));
}

.file-input-node.selected {
  border-color: var(--color-success-light-2, #3bb346);
  box-shadow: 0 0 0 2px var(--color-success-light-4, rgba(59, 179, 70, 0.2));
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
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
  padding: 8px 12px;
}

.node-field {
  color: var(--tf-text-secondary);
  font-family: monospace;
  font-size: 11px;
}

.path-field {
  margin-bottom: 4px;
  word-break: break-all;
}

.output-field {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--tf-text-tertiary);
}

.output-arrow {
  color: var(--color-success-light-2, #3bb346);
}

.output-var {
  color: var(--color-success-light-2, #3bb346);
}

.handle-right {
  background: var(--color-success-light-2, #3bb346);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}
</style>
