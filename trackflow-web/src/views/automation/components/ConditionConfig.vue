<template>
  <div class="condition-config">
    <a-form :model="localData" layout="vertical" size="small">
      <a-form-item label="节点标签">
        <a-input v-model="localData.label" placeholder="条件判断" />
      </a-form-item>
      
      <a-form-item label="条件变量">
        <a-select
          v-model="localData.variable"
          placeholder="选择或输入变量名"
          allow-create
          allow-clear
        >
          <a-option value="{output}">&#123;output&#125; - 上一节点输出</a-option>
          <a-option value="{exit_code}">&#123;exit_code&#125; - 退出码</a-option>
          <a-option value="{status}">&#123;status&#125; - 执行状态</a-option>
        </a-select>
        <div class="form-hint">使用 {变量名} 格式引用变量</div>
      </a-form-item>
      
      <a-form-item label="运算符">
        <a-select v-model="localData.operator" placeholder="选择运算符">
          <a-option value="contains">包含 (contains)</a-option>
          <a-option value="not_contains">不包含 (not contains)</a-option>
          <a-option value="equals">等于 (equals)</a-option>
          <a-option value="not_equals">不等于 (not equals)</a-option>
          <a-option value="is_empty">为空 (is empty)</a-option>
          <a-option value="is_not_empty">不为空 (is not empty)</a-option>
        </a-select>
      </a-form-item>
      
      <a-form-item v-if="showValueInput" label="比较值">
        <a-input v-model="localData.value" placeholder="输入比较值" />
        <div class="form-hint">支持 {变量名} 引用其他变量</div>
      </a-form-item>
      
      <div class="output-info">
        <div class="output-title">输出分支</div>
        <div class="output-row">
          <span class="output-icon success">✓</span>
          <span class="output-label">True</span>
          <span class="output-desc">条件满足时走此分支</span>
        </div>
        <div class="output-row">
          <span class="output-icon danger">✗</span>
          <span class="output-label">False</span>
          <span class="output-desc">条件不满足时走此分支</span>
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
  if (!localData.value.label) localData.value.label = '条件判断'
  if (!localData.value.variable) localData.value.variable = '{output}'
  if (!localData.value.operator) localData.value.operator = 'contains'
  if (localData.value.value === undefined) localData.value.value = ''
})

// 是否显示比较值输入框（为空/不为空运算符不需要）
const showValueInput = computed(() => {
  return localData.value.operator !== 'is_empty' && localData.value.operator !== 'is_not_empty'
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
.condition-config {
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

.output-icon.success {
  background: var(--tf-success-bg);
  color: var(--color-success-light-4, #52c41a);
}

.output-icon.danger {
  background: var(--color-danger-light-1, rgba(245, 34, 45, 0.1));
  color: var(--tf-danger);
}

.output-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--tf-text-primary);
  min-width: 40px;
}

.output-desc {
  font-size: 11px;
  color: var(--tf-text-tertiary);
}
</style>
