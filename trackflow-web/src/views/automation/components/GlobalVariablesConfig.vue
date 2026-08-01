<template>
  <div class="global-variables-config">
    <div class="config-desc">
      全局变量可在所有节点的 Prompt 模板中通过 {变量名} 引用
    </div>
    
    <div class="var-list">
      <div v-for="(_value, key) in localVars" :key="key" class="var-row">
        <span class="var-key">{{ key }}</span>
        <span class="var-eq">=</span>
        <a-input v-model="localVars[key]" size="small" class="var-value-input" />
        <a-button type="text" size="small" status="danger" @click="removeVar(key)">✕</a-button>
      </div>
    </div>
    
    <div class="add-var-row">
      <a-input v-model="newKey" size="small" placeholder="变量名" class="new-key-input" />
      <a-input v-model="newValue" size="small" placeholder="值" class="new-value-input" />
      <a-button type="primary" size="small" :disabled="!newKey" @click="addVar">添加</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{
  variables: Record<string, string>
}>()

const emit = defineEmits(['update:variables'])

const localVars = ref<Record<string, string>>({})
const newKey = ref('')
const newValue = ref('')

onMounted(() => {
  localVars.value = { ...props.variables }
})

function addVar() {
  if (!newKey.value) return
  localVars.value[newKey.value] = newValue.value
  newKey.value = ''
  newValue.value = ''
}

function removeVar(key: string) {
  delete localVars.value[key]
  // 触发响应式更新
  localVars.value = { ...localVars.value }
}

watch(localVars, (val) => {
  emit('update:variables', { ...val })
}, { deep: true })

watch(() => props.variables, (val) => {
  localVars.value = { ...val }
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
