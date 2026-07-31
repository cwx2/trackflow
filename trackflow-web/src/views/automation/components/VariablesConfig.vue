<template>
  <div class="variables-config">
    <div class="var-list">
      <div v-for="(item, index) in localVars" :key="index" class="var-row">
        <a-input v-model="item.key" placeholder="变量名" class="var-key-input" />
        <span class="var-eq">=</span>
        <a-input v-model="item.value" placeholder="值" class="var-value-input" />
        <a-button type="text" size="small" status="danger" @click="removeVar(index)">
          ✕
        </a-button>
      </div>
    </div>
    
    <a-button type="dashed" long size="small" @click="addVar">
      + 添加变量
    </a-button>
    
    <div class="preset-section">
      <div class="preset-title">常用预设</div>
      <div class="preset-buttons">
        <a-button size="mini" @click="addPreset('workspace', '/project/YT')">workspace</a-button>
        <a-button size="mini" @click="addPreset('model', 'claude-opus-4.5')">model</a-button>
        <a-button size="mini" @click="addPreset('skill_path', '.kiro/skills')">skill_path</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{
  data: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:data', value: Record<string, any>): void
}>()

const localVars = ref<Array<{ key: string; value: string }>>([])

// 初始化
onMounted(() => {
  localVars.value = props.data.vars ? [...props.data.vars] : []
  if (localVars.value.length === 0) {
    localVars.value.push({ key: '', value: '' })
  }
})

// 添加变量
function addVar() {
  localVars.value.push({ key: '', value: '' })
}

// 移除变量
function removeVar(index: number) {
  localVars.value.splice(index, 1)
  if (localVars.value.length === 0) {
    localVars.value.push({ key: '', value: '' })
  }
}

// 添加预设
function addPreset(key: string, value: string) {
  // 检查是否已存在
  const exists = localVars.value.some(v => v.key === key)
  if (!exists) {
    // 替换空行或添加新行
    const emptyIndex = localVars.value.findIndex(v => !v.key && !v.value)
    if (emptyIndex >= 0) {
      localVars.value[emptyIndex] = { key, value }
    } else {
      localVars.value.push({ key, value })
    }
  }
}

// 同步数据变化
watch(localVars, (val) => {
  emit('update:data', {
    ...props.data,
    vars: val.filter(v => v.key) // 过滤掉空行
  })
}, { deep: true })

watch(() => props.data, (val) => {
  localVars.value = val.vars ? [...val.vars] : []
  if (localVars.value.length === 0) {
    localVars.value.push({ key: '', value: '' })
  }
}, { deep: true })
</script>

<style scoped>
.variables-config {
  padding: 12px 16px;
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

.var-key-input {
  width: 80px;
  flex-shrink: 0;
}

.var-eq {
  color: var(--tf-text-tertiary);
  flex-shrink: 0;
}

.var-value-input {
  flex: 1;
}

.preset-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border);
}

.preset-title {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-bottom: 8px;
}

.preset-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
