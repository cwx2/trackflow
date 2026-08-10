<template>
  <div class="issue-get-config">
    <a-form :model="local" layout="vertical" size="small">

      <a-form-item label="工单 ID / 编号">
        <div v-if="isReference" class="reference-value">
          <code>{{ refLabel }}</code>
          <a-button size="mini" type="text" @click="clearInput">改为固定值</a-button>
        </div>
        <a-input
          v-else
          :model-value="issueValue"
          placeholder="工单数字 ID，如 123；或编号，如 DE4-1"
          @input="setIssueLiteral"
        />
        <template #extra>
          支持工单 ID（纯数字）或工单编号（如 DE4-1）。通常从触发器或上游节点获取，也可直接填写固定值。
        </template>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>📋</span></template>
        <div class="alert-content">
          此节点读取完整工单信息（含评论、标签、自定义字段），
          输出 <code>issue</code> 对象供下游「准备工单上下文」或「变更需求状态」节点使用。
        </div>
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

function findInput() {
  return (local.value.inputs || []).find((item: any) => item.name === 'issue')
}

const isReference = computed(() => findInput()?.value?.type === 'ref')

const refLabel = computed(() => {
  const v = findInput()?.value
  return v?.type === 'ref' ? `${v.nodeId}.${v.outputName}` : ''
})

const issueValue = computed(() => {
  const item = findInput()
  return item?.value?.type === 'literal' ? String(item.value.value ?? '') : ''
})

function setIssueLiteral(value: string) {
  const item = findInput()
  if (item) {
    item.value = value ? { type: 'literal', value } : null
  }
  commit()
}

function clearInput() {
  const item = findInput()
  if (item) item.value = null
  commit()
}

function commit() {
  emit('update:data', JSON.parse(JSON.stringify(local.value)))
}

watch(() => props.data, (val) => {
  local.value = JSON.parse(JSON.stringify(val || {}))
}, { deep: true })
</script>

<style scoped>
.issue-get-config {
  padding: 12px 16px;
}

.reference-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.reference-value code {
  color: var(--color-primary-6);
  font-size: 12px;
}

.alert-content {
  font-size: 12px;
  line-height: 1.6;
}

.alert-content code {
  background: var(--tf-bg-elevated);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
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
</style>
