<template>
  <button
    class="terminal-node"
    :class="[
      `terminal-${variant}`,
      `connection-view-${connectionViewMode}`,
      connectionDragKind ? `is-connecting-${connectionDragKind}` : '',
    ]"
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
    <span class="terminal-port" :class="`terminal-port-${variant}`" aria-hidden="true">
      <span class="terminal-port-label">{{ variant === 'start' ? '流程继续' : '流程进入' }}</span>
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { IconPlayArrowFill, IconRecordStop } from '@arco-design/web-vue/es/icon'

const props = defineProps<{
  variant: 'start' | 'end'
  properties?: Record<string, unknown>
  onNodeClick?: () => void
}>()

const connectionViewMode = computed<'all' | 'flow' | 'data'>(() =>
  (props.properties?.connectionViewMode as 'all' | 'flow' | 'data' | undefined) || 'all')
const connectionDragKind = computed<'control' | 'data' | null>(() =>
  (props.properties?.connectionDragKind as 'control' | 'data' | null | undefined) || null)
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
.connection-view-data,
.is-connecting-data { opacity: .22; transition: opacity 180ms ease; }

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
  /* 与 NodeCard 端口相同：节点提供空心插座，边提供实心插头。 */
  position: absolute;
  top: 50%;
  z-index: 1;
  box-sizing: border-box;
  width: 14px;
  height: 14px;
  border: 2px solid var(--wf-flow-port, #a78bfa);
  border-radius: 50%;
  background: transparent;
  pointer-events: none;
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-flow-port, #a78bfa) 36%, transparent);
  transform: translateY(-50%);
}

/* 补偿端点节点内容盒的 1px 边框，使插座圆心与模型锚点严格重合。 */
.terminal-port-start { right: -14px; }
.terminal-port-end { left: -14px; }

.terminal-port::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--wf-flow-port, #a78bfa);
}
.terminal-port-label {
  position: absolute;
  top: -28px;
  padding: 4px 6px;
  border: 1px solid color-mix(in srgb, var(--wf-flow-port, #a78bfa) 42%, var(--wf-node-border));
  border-radius: 5px;
  background: var(--wf-node-bg);
  box-shadow: 0 4px 10px rgba(0, 0, 0, .22);
  color: var(--wf-flow-port, #a78bfa);
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  opacity: 0;
  white-space: nowrap;
  transition: opacity 150ms ease;
}
.terminal-port-start .terminal-port-label { right: -2px; transform: translateX(100%); }
.terminal-port-end .terminal-port-label { left: -2px; transform: translateX(-100%); }
.terminal-node:hover .terminal-port-label,
.connection-view-flow .terminal-port-label,
.is-connecting-control .terminal-port-label { opacity: 1; }
.connection-view-flow .terminal-port,
.is-connecting-control .terminal-port { animation: terminal-port-breathe 1.45s ease-in-out infinite; }
@keyframes terminal-port-breathe {
  0%, 100% { box-shadow: 0 0 0 1px color-mix(in srgb, var(--wf-flow-port) 40%, transparent); }
  50% { box-shadow: 0 0 0 5px color-mix(in srgb, var(--wf-flow-port) 17%, transparent); }
}
</style>
