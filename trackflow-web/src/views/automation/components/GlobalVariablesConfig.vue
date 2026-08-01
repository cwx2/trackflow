<template>
  <div class="global-variables-config">
    <div class="config-desc">
      全局变量可在所有节点的 Prompt 模板中通过 {变量名} 引用
    </div>

    <div class="var-list">
      <div v-for="(val, key) in localVars" :key="key" class="var-row">
        <span class="var-key">{{ key }}</span>
        <span class="var-eq">=</span>
        <a-input v-model="localVars[key]" size="small" class="var-value-input" placeholder="默认值" />
        <a-button type="text" size="small" status="danger" @click="removeVar(key as string)">✕</a-button>
      </div>
    </div>

    <div class="add-var-row">
      <a-input v-model="newKey" size="small" placeholder="变量名" class="new-key-input" />
      <a-input v-model="newValue" size="small" placeholder="默认值" class="new-value-input" />
      <a-button type="primary" size="small" :disabled="!newKey" @click="addVar">添加</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import type { GlobalVariable } from '@/api'

/**
 * 内部用 Record<string, string> 存每个变量的 defaultValue（便于 v-model 绑定输入框）
 * props / emit 都是 Record<string, GlobalVariable>
 */
const props = defineProps<{
  variables: Record<string, GlobalVariable>
}>()

const emit = defineEmits<{
  (e: 'update:variables', val: Record<string, GlobalVariable>): void
}>()

// 内部：只存 defaultValue 字符串（供输入框绑定）
const localVars = ref<Record<string, string>>({})
const newKey = ref('')
const newValue = ref('')

/** 将父组件的 GlobalVariable 对象映射成 defaultValue 字符串 */
function toLocal(vars: Record<string, GlobalVariable>): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [k, v] of Object.entries(vars)) {
    result[k] = v.defaultValue != null ? String(v.defaultValue) : ''
  }
  return result
}

/** 将本地字符串 map 包回 GlobalVariable 对象（保留原有 type） */
function toGlobal(
  localMap: Record<string, string>,
  prevVars: Record<string, GlobalVariable>,
): Record<string, GlobalVariable> {
  const result: Record<string, GlobalVariable> = {}
  for (const [k, v] of Object.entries(localMap)) {
    result[k] = {
      type: prevVars[k]?.type ?? 'string',
      defaultValue: v,
    }
  }
  return result
}

onMounted(() => {
  localVars.value = toLocal(props.variables)
})

function addVar() {
  if (!newKey.value) return
  localVars.value[newKey.value] = newValue.value
  newKey.value = ''
  newValue.value = ''
}

function removeVar(key: string) {
  const copy = { ...localVars.value }
  delete copy[key]
  localVars.value = copy
}

// ── 防循环：用 flag 避免 localVars→emit→props→localVars 死循环
let _syncing = false

watch(localVars, (val) => {
  if (_syncing) return
  _syncing = true
  emit('update:variables', toGlobal(val, props.variables))
  _syncing = false
}, { deep: true })

watch(() => props.variables, (val) => {
  if (_syncing) return
  const fresh = toLocal(val)
  const freshStr = JSON.stringify(fresh)
  if (freshStr !== JSON.stringify(localVars.value)) {
    localVars.value = fresh
  }
}, { deep: true })
</script>

<style scoped>
.global-variables-config {
  padding: 12px 16px;
}

.config-desc {
  font-size: 12px;
  color: var(--tf-text-tertiary);
  margin-bottom: 16px;
  line-height: 1.5;
}

.var-list {
  margin-bottom: 12px;
}

.var-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}

.var-key {
  font-family: monospace;
  font-size: 12px;
  color: var(--tf-accent);
  min-width: 80px;
}

.var-eq {
  color: var(--tf-text-tertiary);
}

.var-value-input {
  flex: 1;
}

.add-var-row {
  display: flex;
  gap: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border);
}

.new-key-input {
  width: 80px;
  flex-shrink: 0;
}

.new-value-input {
  flex: 1;
}
</style>
