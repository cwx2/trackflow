<template>
  <button
    class="terminal-node"
    :class="`terminal-${variant}`"
    :aria-label="variant === 'start' ? '工作流开始节点' : '工作流结束节点'"
    type="button"
    @click.stop="onNodeClick"
  >
    <span class="terminal-icon" aria-hidden="true">
      <IconPlayArrowFill v-if="variant === 'start'" :size="16" />
      <IconRecordStop v-else :size="14" />
    </span>
    <span class="terminal-copy">
      <strong>{{ variant === 'start' ? '开始' : '结束' }}</strong>
      <small>{{ variant === 'start' ? '工作流入口' : '流程完成' }}</small>
    </span>
    <span class="terminal-port" :class="`terminal-port-${variant}`" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { IconPlayArrowFill, IconRecordStop } from '@arco-design/web-vue/es/icon'

defineProps<{
  variant: 'start' | 'end'
  onNodeClick?: () => void
}>()
</script>

<style scoped>
.terminal-node {
  --terminal-color: var(--wf-status-success);
  position: relative;
  display: flex;
  align-items: center;
  width: 112px;
  height: 64px;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, var(--terminal-color) 58%, var(--wf-node-border));
  border-radius: 14px;
  background: color-mix(in srgb, var(--terminal-color) 8%, var(--wf-node-bg));
  box-shadow: 0 2px 10px color-mix(in srgb, var(--terminal-color) 14%, transparent);
  color: var(--wf-node-title);
  cursor: pointer;
  transition: transform 150ms ease, border-color 150ms ease, box-shadow 150ms ease, background 150ms ease;
}

.terminal-node:hover {
  transform: translateY(-1px);
  border-color: var(--terminal-color);
  background: color-mix(in srgb, var(--terminal-color) 12%, var(--wf-node-bg));
  box-shadow: 0 5px 16px color-mix(in srgb, var(--terminal-color) 22%, transparent);
}

.terminal-node:focus-visible {
  outline: 2px solid var(--terminal-color);
  outline-offset: 3px;
}

.terminal-end { --terminal-color: var(--wf-status-failed); }

.terminal-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--terminal-color) 16%, transparent);
  color: var(--terminal-color);
  flex: 0 0 auto;
}

.terminal-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.terminal-copy strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.terminal-copy small {
  color: var(--wf-node-subtitle);
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
}

.terminal-port {
  position: absolute;
  top: 50%;
  z-index: 1;
  width: 10px;
  height: 10px;
  border: 2px solid var(--wf-node-bg);
  border-radius: 50%;
  background: var(--terminal-color);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--terminal-color) 62%, transparent);
  transform: translateY(-50%);
}

.terminal-port-start { right: -6px; }
.terminal-port-end { left: -6px; }
</style>
