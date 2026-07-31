<template>
  <div class="custom-node cli-agent-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-title">{{ data.label || 'CLI Agent' }}</span>
    </div>
    <div class="node-body">
      <div class="node-field">{{ data.command || 'kiro-cli' }}</div>
      <div v-if="data.model" class="node-field secondary">{{ data.model }}</div>
    </div>
    <!-- 连接点 -->
    <Handle type="target" :position="Position.Left" class="handle-left" />
    <Handle type="source" :position="Position.Right" class="handle-right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: Record<string, any>
  selected?: boolean
}>()
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

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--tf-bg-surface);
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
  font-size: 12px;
}

.node-field.secondary {
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.handle-left,
.handle-right {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}
</style>
