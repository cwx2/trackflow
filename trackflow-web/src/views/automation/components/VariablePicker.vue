<template>
  <div class="variable-picker">
    <!-- 模式切换 -->
    <div class="mode-tabs">
      <button :class="['mode-tab', mode === 'literal' ? 'active' : '']" @click="setMode('literal')">手动输入</button>
      <button :class="['mode-tab', mode === 'ref' ? 'active' : '']" @click="setMode('ref')">引用变量</button>
    </div>

    <!-- 字面值输入 -->
    <div v-if="mode === 'literal'" class="literal-input">
      <a-input
        :model-value="literalValue"
        :placeholder="placeholder || '请输入值'"
        size="small"
        @input="onLiteralInput"
      />
    </div>

    <!-- 变量引用选择 -->
    <div v-else class="ref-select">
      <!-- 已选显示 -->
      <div v-if="selectedRef" class="selected-ref" @click="showDropdown = !showDropdown">
        <span class="ref-tag">
          <span class="ref-node">{{ selectedRef.nodeName }}</span>
          <span class="ref-sep">.</span>
          <span class="ref-port">{{ selectedRef.outputName }}</span>
          <span class="ref-type">({{ selectedRef.valueType }})</span>
        </span>
        <span class="clear-btn" @click.stop="clearRef">✕</span>
      </div>
      <a-button v-else size="mini" @click="showDropdown = !showDropdown">
        选择变量 ▾
      </a-button>

      <!-- 下拉列表 -->
      <div v-if="showDropdown" class="var-dropdown" v-click-outside="() => showDropdown = false">
        <div v-if="availableVars.length === 0" class="empty-hint">
          暂无可用上游变量
        </div>
        <template v-for="group in availableVars" :key="group.nodeId">
          <div class="var-group-title">{{ group.nodeName }}</div>
          <div
            v-for="port in group.ports"
            :key="port.name"
            class="var-option"
            :class="{ disabled: targetValueType && port.valueType !== targetValueType }"
            @click="selectRef(group, port)"
          >
            <span class="port-name">{{ port.name }}</span>
            <span class="port-type">{{ port.valueType }}</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 引用失效警告 -->
    <div v-if="isInvalid" class="invalid-hint">⚠ 引用节点已删除</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { InputValue, ValueType } from '@/api/automation'

interface PortGroup {
  nodeId: string
  nodeName: string
  ports: Array<{ name: string; valueType: string }>
}

const props = defineProps<{
  modelValue: InputValue | null
  /** 当前节点ID，用于过滤自身和下游节点 */
  currentNodeId: string
  /** 目标端口类型，用于类型过滤 */
  targetValueType?: ValueType
  placeholder?: string
  /** 上游节点列表（由父组件提供） */
  upstreamNodes: Array<{
    id: string
    title: string
    outputs: Array<{ name: string; valueType: string }>
  }>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: InputValue | null]
}>()

const showDropdown = ref(false)

// 当前模式
const mode = ref<'literal' | 'ref'>(
  props.modelValue?.type === 'ref' ? 'ref' : 'literal'
)

// 字面值
const literalValue = computed(() =>
  props.modelValue?.type === 'literal' ? String(props.modelValue.value ?? '') : ''
)

// 当前引用信息
const selectedRef = computed(() => {
  if (props.modelValue?.type !== 'ref') return null
  const ref2 = props.modelValue
  const node = props.upstreamNodes.find(n => n.id === ref2.nodeId)
  const port = node?.outputs.find(o => o.name === ref2.outputName)
  if (!node || !port) return null
  return { nodeName: node.title, outputName: port.name, valueType: port.valueType }
})

// 引用失效检测
const isInvalid = computed(() =>
  props.modelValue?.type === 'ref' && !selectedRef.value
)

// 可用变量（按节点分组）
const availableVars = computed<PortGroup[]>(() =>
  props.upstreamNodes
    .filter(n => n.outputs.length > 0)
    .map(n => ({
      nodeId: n.id,
      nodeName: n.title,
      ports: n.outputs.filter(p =>
        !props.targetValueType || p.valueType === props.targetValueType || props.targetValueType === 'object'
      ),
    }))
    .filter(g => g.ports.length > 0)
)

function setMode(m: 'literal' | 'ref') {
  mode.value = m
  if (m === 'literal') {
    emit('update:modelValue', { type: 'literal', value: '' })
  } else {
    emit('update:modelValue', null)
  }
}

function onLiteralInput(val: string) {
  emit('update:modelValue', { type: 'literal', value: val })
}

function selectRef(group: PortGroup, port: { name: string; valueType: string }) {
  if (props.targetValueType && port.valueType !== props.targetValueType && props.targetValueType !== 'object') return
  emit('update:modelValue', { type: 'ref', nodeId: group.nodeId, outputName: port.name })
  showDropdown.value = false
}

function clearRef() {
  emit('update:modelValue', null)
}

// 监听外部值变化同步 mode
watch(() => props.modelValue, (val) => {
  mode.value = val?.type === 'ref' ? 'ref' : 'literal'
})
</script>

<style scoped>
.variable-picker { position: relative; }

.mode-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}
.mode-tab {
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 11px;
  border: 1px solid var(--tf-border);
  background: transparent;
  color: var(--tf-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
}
.mode-tab.active {
  background: var(--tf-accent);
  border-color: var(--tf-accent);
  color: var(--tf-text-on-accent);
}

.selected-ref {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  background: var(--tf-accent-bg);
  border: 1px solid var(--tf-accent);
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.ref-tag { display: flex; align-items: center; gap: 2px; flex: 1; }
.ref-node { color: var(--tf-accent); font-weight: 500; }
.ref-sep  { color: var(--tf-text-muted); }
.ref-port { color: var(--tf-text-primary); }
.ref-type { color: var(--tf-text-tertiary); font-size: 10px; }
.clear-btn { color: var(--tf-text-tertiary); cursor: pointer; font-size: 10px; }
.clear-btn:hover { color: var(--tf-danger); }

.var-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--tf-bg-elevated);
  border: 1px solid var(--tf-border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,.3);
  max-height: 220px;
  overflow-y: auto;
  margin-top: 4px;
}
.var-group-title {
  padding: 6px 10px 3px;
  font-size: 10px;
  font-weight: 600;
  color: var(--tf-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.var-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.1s;
}
.var-option:hover { background: var(--tf-bg-hover); }
.var-option.disabled { opacity: 0.4; cursor: not-allowed; }
.port-name { color: var(--tf-text-primary); }
.port-type { color: var(--tf-text-tertiary); font-size: 10px; }

.empty-hint { padding: 12px; text-align: center; color: var(--tf-text-tertiary); font-size: 12px; }
.invalid-hint { margin-top: 4px; color: var(--tf-warning); font-size: 11px; }
</style>
