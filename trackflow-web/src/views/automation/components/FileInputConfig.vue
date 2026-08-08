<template>
  <div class="file-input-config">
    <a-form :model="localData" layout="vertical" size="small">
      <a-form-item label="节点标签">
        <a-input v-model="localData.label" placeholder="文件输入" />
      </a-form-item>
      
      <a-form-item label="文件路径">
        <a-input v-model="localData.filePath" placeholder="{workspace}/requirements/develop/{req_file}" />
        <template #extra>支持 {变量名} 占位符引用全局变量</template>
      </a-form-item>
      
      <a-form-item label="读取模式">
        <a-radio-group v-model="localData.readMode" direction="vertical">
          <a-radio value="full">全文内容</a-radio>
          <a-radio value="first_line">仅首行</a-radio>
          <a-radio value="range">行数范围</a-radio>
        </a-radio-group>
      </a-form-item>
      
      <!-- 行数范围输入（仅在选择 range 时显示） -->
      <template v-if="localData.readMode === 'range'">
        <div class="range-inputs">
          <a-form-item label="起始行">
            <a-input-number v-model="localData.startLine" :min="1" :step="1" placeholder="1" />
          </a-form-item>
          <a-form-item label="结束行">
            <a-input-number v-model="localData.endLine" :min="1" :step="1" placeholder="10" />
          </a-form-item>
        </div>
      </template>
      
      <a-form-item label="编码">
        <a-select v-model="localData.encoding" placeholder="UTF-8">
          <a-option value="utf-8">UTF-8</a-option>
          <a-option value="gbk">GBK</a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="输出变量名">
        <a-input v-model="localData.outputVar" placeholder="file_content" />
        <template #extra>文件内容将存入指定变量，可在后续节点 prompt 中用 {变量名} 引用</template>
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
  if (!localData.value.label) localData.value.label = '文件输入'
  if (!localData.value.filePath) localData.value.filePath = '{workspace}/'
  if (!localData.value.readMode) localData.value.readMode = 'full'
  if (!localData.value.encoding) localData.value.encoding = 'utf-8'
  if (!localData.value.outputVar) localData.value.outputVar = 'file_content'
  if (localData.value.readMode === 'range') {
    if (!localData.value.startLine) localData.value.startLine = 1
    if (!localData.value.endLine) localData.value.endLine = 10
  }
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
.file-input-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.range-inputs {
  display: flex;
  gap: 12px;
}

.range-inputs .arco-form-item {
  flex: 1;
  margin-bottom: 12px;
}

:deep(.arco-form-item) {
  margin-bottom: 12px;
}

:deep(.arco-form-item-label) {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

:deep(.arco-radio-group-direction-vertical .arco-radio) {
  margin-bottom: 8px;
}
</style>
