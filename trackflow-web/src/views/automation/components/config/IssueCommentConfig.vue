<template>
  <div class="issue-comment-config">
    <a-form :model="local" layout="vertical" size="small">

      <!-- 工单 ID -->
      <a-form-item label="工单 ID">
        <div v-if="isRef('issueId')" class="reference-value">
          <code>{{ refLabel('issueId') }}</code>
          <a-button size="mini" type="text" @click="clearInput('issueId')">改为固定值</a-button>
        </div>
        <a-input-number
          v-else
          :model-value="literalNum('issueId')"
          :min="1"
          placeholder="工单数字 ID，如 123"
          style="width: 100%"
          @change="(v: any) => setLiteral('issueId', v)"
        />
        <template #extra>必填。填写要写入评论的工单 ID（纯数字），通常通过连线从上游节点传入。</template>
      </a-form-item>

      <!-- 评论内容 -->
      <a-form-item label="评论内容">
        <div v-if="isRef('content')" class="reference-value">
          <code>{{ refLabel('content') }}</code>
          <a-button size="mini" type="text" @click="clearInput('content')">改为固定值</a-button>
        </div>
        <a-textarea
          v-else
          :model-value="literalStr('content')"
          placeholder="评论内容，支持 Markdown 格式。通常来自上游 Agent 的输出，也可填写固定模板。"
          :auto-size="{ minRows: 4, maxRows: 10 }"
          @input="(v: string) => setLiteral('content', v)"
        />
        <template #extra>必填。支持 Markdown 格式，可包含链接、代码块、加粗等。</template>
      </a-form-item>

      <a-alert type="info" style="margin-top: 8px">
        <template #icon><span>💬</span></template>
        <div class="alert-content">
          此节点以工作流执行身份向工单添加评论，记录 AI 分析结论、处理进度或测试结果。
          评论成功后输出 <code>comment</code> 对象（含 commentId）。
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

function isRef(name: string): boolean {
  return findInput(name)?.value?.type === 'ref'
}

function refLabel(name: string): string {
  const v = findInput(name)?.value
  return v?.type === 'ref' ? `${v.nodeId}.${v.outputName}` : ''
}

function literalStr(name: string): string {
  const item = findInput(name)
  return item?.value?.type === 'literal' ? String(item.value.value ?? '') : ''
}

function literalNum(name: string): number | undefined {
  const item = findInput(name)
  if (item?.value?.type === 'literal') {
    const val = Number(item.value.value)
    return isNaN(val) ? undefined : val
  }
  return undefined
}

function setLiteral(name: string, value: unknown) {
  const item = findInput(name)
  if (item) {
    item.value = (value === undefined || value === null || value === '') ? null : { type: 'literal', value }
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
.issue-comment-config {
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
