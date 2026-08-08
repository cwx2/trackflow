<template>
  <div class="loop-config">
    <a-form :model="localData" layout="vertical" size="small">
      <a-form-item label="节点标签">
        <a-input v-model="localData.label" placeholder="重试循环" />
      </a-form-item>
      
      <a-form-item label="最大重试次数">
        <a-input-number
          v-model="localData.maxRetries"
          :min="1"
          :max="20"
          :default-value="3"
          placeholder="3"
          style="width: 100%"
        />
        <template #extra>达到上限后强制退出循环，无论退出条件是否满足（防止无限循环），范围 1-20</template>
      </a-form-item>

      <a-form-item label="循环子工作流 ID">
        <a-input v-model="localData.workflowId" placeholder="选择一个已发布工作流的 ID" />
        <template #extra>填入已发布工作流的数字 ID；每轮循环独立执行该工作流，循环体本身不在画布上形成回边</template>
      </a-form-item>
      
      <a-form-item label="重试间隔（秒）">
        <a-input-number
          v-model="localData.interval"
          :min="0"
          :max="300"
          :default-value="5"
          placeholder="5"
          style="width: 100%"
        />
        <template #extra>每次重试之间的等待时间（0-300秒）</template>
      </a-form-item>
      
      <a-divider style="margin: 16px 0 12px" />
      
      <div class="section-title">退出条件</div>
      
      <a-form-item label="条件变量">
        <a-select
          v-model="localData.exitVariable"
          placeholder="选择或输入变量名"
          allow-create
          allow-clear
        >
          <a-option value="{output}">&#123;output&#125; - 上一节点输出</a-option>
          <a-option value="{exit_code}">&#123;exit_code&#125; - 退出码</a-option>
          <a-option value="{status}">&#123;status&#125; - 执行状态</a-option>
          <a-option value="{test_result}">&#123;test_result&#125; - 测试结果</a-option>
        </a-select>
        <template #extra>使用 {变量名} 格式引用变量</template>
      </a-form-item>
      
      <a-form-item label="运算符">
        <a-select v-model="localData.exitOperator" placeholder="选择运算符">
          <a-option value="contains">包含 (contains)</a-option>
          <a-option value="not_contains">不包含 (not contains)</a-option>
          <a-option value="equals">等于 (equals)</a-option>
          <a-option value="not_equals">不等于 (not equals)</a-option>
          <a-option value="is_empty">为空 (is empty)</a-option>
          <a-option value="is_not_empty">不为空 (is not empty)</a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item v-if="showValueInput" label="比较值">
        <a-input v-model="localData.exitValue" placeholder="输入比较值" />
        <template #extra>支持 {变量名} 引用其他变量</template>
      </a-form-item>
      
      <a-alert type="info" style="margin-top: 12px">
        <template #icon><span>💡</span></template>
        满足退出条件时结束循环，否则等待间隔后重试
      </a-alert>
      
      <div class="output-info">
        <div class="output-title">输出分支</div>
        <div class="output-row">
          <span class="output-icon primary">→</span>
          <span class="output-label">循环体</span>
          <span class="output-desc">进入循环执行的子流程</span>
        </div>
        <div class="output-row">
          <span class="output-icon success">✓</span>
          <span class="output-label">完成</span>
          <span class="output-desc">满足条件或达到最大次数后继续</span>
        </div>
      </div>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'

const props = defineProps<{
  data: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:data', value: Record<string, any>): void
}>()

const localData = ref({ ...props.data })

// 初始化默认值
onMounted(() => {
  if (!localData.value.label) localData.value.label = '重试循环'
  if (localData.value.maxRetries === undefined) localData.value.maxRetries = 3
  if (localData.value.workflowId === undefined) localData.value.workflowId = ''
  if (localData.value.interval === undefined) localData.value.interval = 5
  if (!localData.value.exitVariable) localData.value.exitVariable = '{output}'
  if (!localData.value.exitOperator) localData.value.exitOperator = 'contains'
  if (localData.value.exitValue === undefined) localData.value.exitValue = 'PASS'
})

// 是否显示比较值输入框（为空/不为空运算符不需要）
const showValueInput = computed(() => {
  return localData.value.exitOperator !== 'is_empty' && localData.value.exitOperator !== 'is_not_empty'
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
.loop-config {
  padding: 12px 16px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
  margin-bottom: 12px;
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

/* 输出分支说明 */
.output-info {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--tf-border);
}

.output-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-secondary);
  margin-bottom: 8px;
}

.output-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}

.output-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.output-icon.primary {
  background: var(--tf-accent-subtle));
  color: var(--color-primary-light-4, #58a6ff);
}

.output-icon.success {
  background: var(--tf-success-bg);
  color: var(--color-success-light-4, #52c41a);
}

.output-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
  min-width: 50px;
}

.output-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}
</style>
