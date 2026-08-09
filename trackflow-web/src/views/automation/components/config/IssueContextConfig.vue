<template>
  <div class="issue-context-config">
    <a-form :model="local" layout="vertical">
      <a-form-item label="工单对象">
        <div v-if="isReference('issue')" class="reference-value">
          <code>{{ referenceLabel('issue') }}</code>
          <a-button size="mini" type="text" @click="clearInput('issue')">改为固定值</a-button>
        </div>
        <a-input
          v-else
          :model-value="String(getInputLiteral('issue') ?? '')"
          placeholder="通常来自上游「获取需求」节点的输出"
          disabled
        />
        <template #extra>必须通过连线从上游"获取工单"节点传入，不支持手动填写 ID</template>
      </a-form-item>

      <a-form-item label="包含评论">
        <a-switch
          :model-value="getInputLiteral('includeComments') !== false"
          @change="(val: any) => setInputLiteral('includeComments', val)"
        />
        <template #extra>开启后附加最近 10 条评论内容，增加上下文但也会消耗更多 token</template>
      </a-form-item>

      <a-form-item label="包含自定义字段">
        <a-switch
          :model-value="getInputLiteral('includeCustomFields') !== false"
          @change="(val: any) => setInputLiteral('includeCustomFields', val)"
        />
        <template #extra>开启后附加该工单配置的自定义字段键值，适用于需要完整字段信息的 AI 分析场景</template>
      </a-form-item>

      <a-form-item label="最大字符数">
        <a-input-number
          :model-value="getInputLiteral('maxLength') ?? 4000"
          :min="500"
          :max="32000"
          :step="500"
          style="width: 100%"
          placeholder="默认 4000"
          @change="(val: any) => setInputLiteral('maxLength', val)"
        />
        <template #extra>限制输出总字符数防止超出模型 token 上限；GPT-4 建议 ≤8000，Claude 可适当放宽</template>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>ℹ️</span></template>
        <div class="alert-content">
          本节点将工单信息聚合为结构化 Markdown 文本，可直接连接到 Agent 节点的 <code>context</code> 输入端口。
        </div>
      </a-alert>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:data', value: Record<string, any>): void }>()

const local = ref<Record<string, any>>(JSON.parse(JSON.stringify(props.data || {})))

function findInput(name: string) {
  return (local.value.inputs || []).find((item: any) => item.name === name)
}

function getInputLiteral(name: string): any {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? item.value.value : undefined
}

function isReference(name: string): boolean {
  const item = findInput(name)
  return item?.value?.type === 'ref'
}

function referenceLabel(name: string): string {
  const item = findInput(name)
  if (item?.value?.type === 'ref') {
    return `${item.value.nodeId}.${item.value.outputName}`
  }
  return ''
}

function setInputLiteral(name: string, value: unknown) {
  const item = findInput(name)
  if (item) {
    item.value = (value === undefined || value === null || value === '')
      ? null
      : { type: 'literal', value }
  }
  commit()
}

function clearInput(name: string) {
  const item = findInput(name)
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
.issue-context-config {
  padding: 12px 16px;
}

.form-hint {
  font-size: 11px;
  color: var(--tf-text-tertiary);
  margin-top: 4px;
}

.reference-value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.reference-value code {
  color: var(--color-primary-6);
}

.alert-content {
  font-size: 12px;
  line-height: 1.5;
}

.alert-content code {
  padding: 1px 4px;
  background: var(--tf-bg-surface);
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
