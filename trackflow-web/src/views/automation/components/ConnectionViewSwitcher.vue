<template>
  <div class="connection-view-switcher" role="group" aria-label="画布连线视图">
    <button
      v-for="option in options"
      :key="option.value"
      class="connection-view-button"
      :class="[`is-${option.value}`, { active: modelValue === option.value }]"
      type="button"
      :title="option.description"
      :aria-pressed="modelValue === option.value"
      @click="emit('update:modelValue', option.value)"
    >
      <span class="connection-view-dot" aria-hidden="true" />
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { ConnectionViewMode } from '../graph/connection-semantics'

defineProps<{ modelValue: ConnectionViewMode }>()
const emit = defineEmits<{ 'update:modelValue': [mode: ConnectionViewMode] }>()

const options: Array<{ value: ConnectionViewMode; label: string; description: string }> = [
  { value: 'all', label: '全部', description: '同时查看执行流程与参数数据' },
  { value: 'flow', label: '流程', description: '只突出节点的执行顺序' },
  { value: 'data', label: '数据', description: '只突出字段的数据来源与去向' },
]
</script>

<style scoped>
.connection-view-switcher {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--wf-toolbar-border);
  border-radius: 9px;
  background: color-mix(in srgb, var(--wf-toolbar-hover) 58%, transparent);
}

.connection-view-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--wf-toolbar-muted);
  cursor: pointer;
  font-size: 11px;
  line-height: 1;
  transition: color 160ms ease, background 160ms ease, box-shadow 160ms ease;
}
.connection-view-button:hover { color: var(--wf-toolbar-text); background: var(--wf-toolbar-hover); }
.connection-view-button.active { color: var(--wf-toolbar-text); background: var(--wf-toolbar-active); box-shadow: 0 1px 3px rgba(0, 0, 0, .22); }

.connection-view-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.is-flow .connection-view-dot { color: var(--wf-flow-port, #a78bfa); }
.is-data .connection-view-dot { color: var(--wf-data-edge, #38bdf8); }
.is-all .connection-view-dot { background: linear-gradient(135deg, var(--wf-flow-port, #a78bfa) 48%, var(--wf-data-edge, #38bdf8) 52%); }
</style>
