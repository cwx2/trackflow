<template>
  <div class="custom-node loop-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔄</span>
      <span class="node-title">{{ data.label || '重试循环' }}</span>
    </div>
    <div class="node-body">
      <div class="loop-preview">{{ loopPreview }}</div>
    </div>
    <!-- 连接点：1个输入（左侧），2个输出（右侧"循环体"、下方"完成"） -->
    <Handle type="target" :position="Position.Left" class="handle-left" />
    <Handle id="loop" type="source" :position="Position.Right" :style="{ top: '50%' }" class="handle-loop">
      <span class="handle-label handle-label-loop">→</span>
    </Handle>
    <Handle id="done" type="source" :position="Position.Bottom" class="handle-done">
      <span class="handle-label handle-label-done">✓</span>
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

// 生成循环配置预览文本
const loopPreview = computed(() => {
  const { maxRetries, interval } = props.data
  const retries = maxRetries ?? 3
  const intervalSec = interval ?? 5
  return `最多 ${retries} 次 · 间隔 ${intervalSec}s`
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

.loop-node {
  border-color: var(--color-primary-light-4, #58a6ff);
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
  background: var(--color-primary-light-1, rgba(88, 166, 255, 0.1));
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
  /* 额外底部 padding 为下方 handle 留空间 */
  padding-bottom: 20px;
}

.loop-preview {
  color: var(--tf-text-secondary);
  font-family: monospace;
  font-size: 12px;
}

/* 连接点样式 */
.handle-left {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}

.handle-loop,
.handle-done {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  width: 12px;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.handle-loop {
  border-color: var(--color-primary-light-4, #58a6ff);
}

.handle-done {
  border-color: var(--color-success-light-4, #52c41a);
}

/* Handle 标签 */
.handle-label {
  position: absolute;
  font-size: 10px;
  font-weight: 600;
  pointer-events: none;
}

.handle-label-loop {
  right: 16px;
  color: var(--color-primary-light-4, #58a6ff);
}

.handle-label-done {
  bottom: -20px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--color-success-light-4, #52c41a);
}
</style>
