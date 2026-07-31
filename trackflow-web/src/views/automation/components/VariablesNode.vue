<template>
  <div class="custom-node variables-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📝</span>
      <span class="node-title">{{ data.label || '变量设置' }}</span>
    </div>
    <div class="node-body">
      <div v-for="v in displayVars" :key="v.key" class="var-row">
        <span class="var-key">{{ v.key }}</span>
        <span class="var-eq">=</span>
        <span class="var-value">{{ truncate(v.value, 15) }}</span>
      </div>
      <div v-if="moreCount > 0" class="var-more">+{{ moreCount }} more</div>
    </div>
    <!-- 只有输出连接点 -->
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

const vars = computed(() => props.data.vars || [])
const displayVars = computed(() => vars.value.slice(0, 3))
const moreCount = computed(() => Math.max(0, vars.value.length - 3))

function truncate(str: string, len: number): string {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '...' : str
}
</script>

<style scoped>
.custom-node {
  background: var(--tf-bg-elevated);
  border: 2px solid var(--tf-border);
  border-radius: 8px;
  min-width: 160px;
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

.var-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: monospace;
  font-size: 11px;
  margin-bottom: 2px;
}

.var-key {
  color: var(--tf-accent);
}

.var-eq {
  color: var(--tf-text-tertiary);
}

.var-value {
  color: var(--tf-text-secondary);
}

.var-more {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.handle-right {
  background: var(--tf-accent);
  border: 2px solid var(--tf-bg-elevated);
  width: 12px;
  height: 12px;
}
</style>
