<template>
  <div class="delay-config">
    <a-form :model="localData" layout="vertical" size="small">
      <a-form-item label="节点标签">
        <a-input v-model="localData.label" placeholder="延时等待" />
      </a-form-item>
      
      <a-form-item label="等待时长来源">
        <a-radio-group v-model="localData.mode" direction="vertical">
          <a-radio value="fixed">固定时长</a-radio>
          <a-radio value="variable">读取变量</a-radio>
        </a-radio-group>
      </a-form-item>
      
      <!-- 固定时长模式 -->
      <template v-if="localData.mode === 'fixed'">
        <a-form-item label="等待秒数">
          <a-input-number
            v-model="localData.seconds"
            :min="1"
            :max="3600"
            :default-value="15"
            placeholder="15"
            style="width: 100%"
          >
            <template #suffix>秒</template>
          </a-input-number>
          <template #extra>固定等待时长，范围 1-3600 秒（最长 1 小时）；超长等待建议拆分为多个节点</template>
        </a-form-item>
      </template>
      
      <!-- 读取变量模式 -->
      <template v-else>
        <a-form-item label="变量名">
          <a-select
            v-model="localData.variable"
            placeholder="选择或输入变量名"
            allow-create
            allow-clear
          >
            <a-option value="{wait_seconds}">&#123;wait_seconds&#125;</a-option>
            <a-option value="{delay}">&#123;delay&#125;</a-option>
            <a-option value="{timeout}">&#123;timeout&#125;</a-option>
          </a-select>
          <template #extra>运行时从该变量读取等待秒数，使用 {变量名} 格式；变量值必须为正整数</template>
        </a-form-item>
      </template>
      
      <a-form-item label="描述（可选）">
        <a-textarea
          v-model="localData.description"
          placeholder="说明这段等待的用途，如：等待后端服务启动"
          :auto-size="{ minRows: 2, maxRows: 4 }"
        />
      </a-form-item>
      
      <a-alert type="info" style="margin-top: 12px">
        <template #icon><span>💡</span></template>
        延时节点在工作流执行时暂停指定秒数，适用于等待服务启动、任务处理等场景
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{
  data: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:data', _value: Record<string, any>): void
}>()

const localData = ref({ ...props.data })

// 初始化默认值
onMounted(() => {
  if (!localData.value.label) localData.value.label = '延时等待'
  if (!localData.value.mode) localData.value.mode = 'fixed'
  if (localData.value.seconds === undefined) localData.value.seconds = 15
  if (!localData.value.variable) localData.value.variable = ''
  if (localData.value.description === undefined) localData.value.description = ''
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
.delay-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

:deep(.arco-form-item) {
  margin-bottom: 12px;
}

:deep(.arco-form-item-label) {
  font-size: 12px;
  color: var(--tf-text-secondary);
}

:deep(.arco-alert) {
  padding: 8px 12px;
  font-size: 12px;
}

:deep(.arco-radio-group) {
  width: 100%;
}

:deep(.arco-radio) {
  margin-bottom: 8px;
}
</style>
