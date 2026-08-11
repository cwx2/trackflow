<template>
  <section v-if="contract" class="node-contract-summary">
    <div class="contract-heading">
      <span>节点运行约束</span>
      <a-tag :color="testTag.color" size="small">{{ testTag.label }}</a-tag>
    </div>
    <p>{{ testTag.description }}</p>
    <div class="contract-facts">
      <span>{{ contract.inputPorts.length }} 个输入</span>
      <span>{{ contract.outputPorts.length }} 个输出</span>
      <span>{{ retryLabel }}</span>
    </div>

    <div v-if="bindings.length" class="binding-list">
      <div class="binding-heading">已连接的数据输入</div>
      <div v-for="binding in bindings" :key="binding.name" class="binding-row">
        <span><strong>{{ binding.label }}</strong><code>{{ binding.source }}</code></span>
        <a-button type="text" size="mini" @click="emit('disconnect', binding.name)">断开</a-button>
      </div>
      <small>连线就是运行时数据来源；断开后可在下方改为固定值。</small>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AutomationNodeDefinitionVO, InputParameter } from '@/api'

const props = defineProps<{
  contract?: AutomationNodeDefinitionVO
  inputs?: InputParameter[]
}>()
const emit = defineEmits<{ disconnect: [portName: string] }>()

const testTag = computed(() => {
  switch (props.contract?.runtime?.testMode) {
    case 'confirm': return { color: 'orange', label: '真实执行需确认', description: '节点试运行会产生真实写操作、外部请求或脚本执行。' }
    case 'simulated': return { color: 'arcoblue', label: '仅支持预演', description: '该节点依赖完整编排上下文，请通过完整流程验证恢复与分支。' }
    default: return { color: 'green', label: '可安全单节点运行', description: '可在不写入正式执行记录的情况下直接调试此节点。' }
  }
})

const retryLabel = computed(() => {
  const runtime = props.contract?.runtime
  if (!runtime) return ''
  return runtime.retrySafe && runtime.defaultMaxAttempts > 1
    ? `失败最多尝试 ${runtime.defaultMaxAttempts} 次`
    : '失败不自动重试'
})

const bindings = computed(() => (props.inputs || []).flatMap(input => {
  const value = input.value
  if (value?.type !== 'ref') return []
  return [{
    name: input.name,
    label: input.label || input.name,
    source: value.path ? `${value.nodeId}.${value.outputName}.${value.path}` : `${value.nodeId}.${value.outputName}`,
  }]
}))
</script>

<style scoped>
.node-contract-summary { margin: 12px 16px 4px; padding: 10px 11px; border: 1px solid var(--tf-border); border-radius: 8px; background: linear-gradient(135deg, var(--tf-accent-bg), var(--tf-bg-elevated)); }
.contract-heading, .binding-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.contract-heading > span, .binding-heading { color: var(--tf-text-primary); font-size: 12px; font-weight: 600; }
.node-contract-summary > p { margin: 6px 0 8px; color: var(--tf-text-secondary); font-size: 11px; line-height: 1.5; }
.contract-facts { display: flex; flex-wrap: wrap; gap: 6px 12px; color: var(--tf-text-tertiary); font-size: 11px; }
.binding-list { margin-top: 10px; padding-top: 9px; border-top: 1px solid var(--tf-border); }
.binding-row { margin-top: 6px; padding: 6px 7px; border-radius: 6px; background: var(--tf-bg-body); }
.binding-row span { display: flex; min-width: 0; align-items: baseline; gap: 6px; }
.binding-row strong { color: var(--tf-text-secondary); font-size: 11px; }
.binding-row code { overflow: hidden; color: var(--tf-accent); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.binding-list small { display: block; margin-top: 7px; color: var(--tf-text-tertiary); font-size: 10px; line-height: 1.45; }
</style>
