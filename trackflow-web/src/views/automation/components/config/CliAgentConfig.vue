<template>
  <div class="node-config cli-agent-config">
    <a-form :model="localData" layout="vertical" size="small">
      <a-form-item label="节点标签">
        <a-input v-model="localData.label" placeholder="CLI Agent" />
      </a-form-item>
      
      <a-form-item label="命令">
        <a-select v-model="localData.command" placeholder="选择命令">
          <a-option value="kiro-cli">kiro-cli</a-option>
          <a-option value="codex">codex</a-option>
          <a-option value="claude">claude</a-option>
          <a-option value="custom">自定义</a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="固定参数">
        <a-input v-model="localData.args" placeholder="--no-interactive --trust-all-tools" />
      </a-form-item>
      
      <a-form-item label="Prompt 模板">
        <a-textarea
          v-model="localData.prompt_template"
          placeholder="支持 {变量名} 占位符"
          :auto-size="{ minRows: 3, maxRows: 8 }"
        />
        <template #extra>支持 {变量名} 占位符，如 {workspace}、{req_file}；运行时自动替换为对应变量值</template>
      </a-form-item>
      
      <a-form-item label="模型">
        <a-select v-model="localData.model" placeholder="留空使用默认" allow-clear>
          <a-option value="claude-opus-4.5">claude-opus-4.5</a-option>
          <a-option value="claude-sonnet-4.6">claude-sonnet-4.6</a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="超时（秒）">
        <a-input-number v-model="localData.timeout" :min="60" :max="7200" :step="60" />
      </a-form-item>
      
      <a-form-item label="工作目录">
        <a-input v-model="localData.work_dir" placeholder="{workspace}" />
      </a-form-item>
      
      <a-form-item label="输出变量名">
        <a-input v-model="localData.output_var" placeholder="output" />
        <template #extra>执行结果（stdout）存入该变量名，后续节点可通过 {变量名} 引用</template>
      </a-form-item>
    </a-form>
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

const localData = ref({ ...props.data })

// 初始化默认值
onMounted(() => {
  if (!localData.value.command) localData.value.command = 'kiro-cli'
  if (!localData.value.args) localData.value.args = '--no-interactive --trust-all-tools'
  if (!localData.value.timeout) localData.value.timeout = 2400
  if (!localData.value.work_dir) localData.value.work_dir = '{workspace}'
  if (!localData.value.output_var) localData.value.output_var = 'output'
})

// 同步数据变化
watch(localData, (val) => {
  emit('update:data', { ...val })
}, { deep: true })

watch(() => props.data, (val) => {
  localData.value = { ...val }
}, { deep: true })
</script>

<style scoped>
.cli-agent-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}


</style>
